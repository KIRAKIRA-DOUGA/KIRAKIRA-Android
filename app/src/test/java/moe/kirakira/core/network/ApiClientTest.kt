package moe.kirakira.core.network

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import moe.kirakira.testing.ScriptedApi
import moe.kirakira.testing.assertApiFailure
import moe.kirakira.testing.bodyText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApiClientTest {
    @Test
    fun get_queryWithReservedCharacters_preservesOneEncodedValue() = runTest {
        val transport = ScriptedApi()
        val client = transport.client(StandardTestDispatcher(testScheduler))
        transport.respond("""{"success":true,"futureField":42}""")
        val email = "name+tag&admin=true@example.invalid"
        assertTrue(client.get<Result>("user/check", mapOf("email" to email)).success)
        val request = transport.requests.single()
        assertEquals(email, request.url.queryParameter("email"))
        assertEquals(setOf("email"), request.url.queryParameterNames)
        assertEquals("no-store", request.header("Cache-Control"))
        assertEquals("application/json", request.header("Accept"))
        assertEquals(null, request.header("Cookie"))
    }

    @Test
    fun post_bodyAndCookie_areBoundToExplicitRequest() = runTest {
        val transport = ScriptedApi()
        val client = transport.client(StandardTestDispatcher(testScheduler))
        transport.respond("""{"success":true}""")
        client.post<Result>("user/self", "{}", "uuid=fixture; token=local")
        transport.respond("""{"success":true}""")
        client.get<Result>("user/check")
        assertEquals("POST", transport.requests.first().method)
        assertEquals("{}", transport.requests.first().bodyText())
        assertEquals("uuid=fixture; token=local", transport.requests.first().header("Cookie"))
        assertEquals(null, transport.requests.last().header("Cookie"))
    }

    @Test
    fun request_httpFailure_isSanitizedAndNotReplayed() = runTest {
        val transport = ScriptedApi()
        val client = transport.client(StandardTestDispatcher(testScheduler))
        val failures = mapOf(
            401 to ApiFailure.SESSION_EXPIRED,
            429 to ApiFailure.RATE_LIMITED,
            503 to ApiFailure.SERVER,
            403 to ApiFailure.REJECTED,
            302 to ApiFailure.REJECTED,
        )
        failures.forEach { (status, failure) ->
            transport.respond("private backend details", status, mapOf("Location" to "https://other.example.invalid/"))
            assertApiFailure(failure) { client.post<Result>("user/login", "{}") }
        }
        assertEquals(failures.size, transport.requests.size)
        assertTrue(transport.requests.all { it.url.host == "auth.example.invalid" })
    }

    @Test
    fun request_transportFailure_distinguishesTimeoutWithoutLeakingMessage() = runTest {
        val transport = ScriptedApi()
        val client = transport.client(StandardTestDispatcher(testScheduler))
        transport.fail(IOException("private network details"))
        assertApiFailure(ApiFailure.NETWORK) { client.request("user/login", "{}") }
        transport.fail(InterruptedIOException("private timeout details"))
        assertApiFailure(ApiFailure.TIMEOUT) { client.request("user/login", "{}") }
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun decode_malformedOrMissingRequiredData_rejectsResponse() = runTest {
        val transport = ScriptedApi()
        val client = transport.client(StandardTestDispatcher(testScheduler))
        for (json in listOf("not json", "{}", """{"success":null}""")) {
            transport.respond(json)
            assertApiFailure(ApiFailure.INVALID_RESPONSE) { client.get<Result>("user/check") }
        }
    }

    @Test
    fun request_responseOverLimit_isRejected() = runTest {
        val transport = ScriptedApi()
        val client = transport.client(StandardTestDispatcher(testScheduler))
        transport.respond("x".repeat(1_048_577))
        assertApiFailure(ApiFailure.INVALID_RESPONSE) { client.request("user/check") }
    }

    @Test
    fun request_canceledCoroutine_cancelsUnderlyingCall() = runTest {
        val transport = ScriptedApi()
        val client = transport.client(StandardTestDispatcher(testScheduler))
        val pending = transport.hold()
        var completed = false
        val job = launch { client.request("user/check"); completed = true }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(pending.isCanceled)
        pending.respond("""{"success":true}""")
        runCurrent()
        assertEquals(false, completed)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun request_externalOrTraversingPath_isRejectedBeforeTransport() = runTest {
        val transport = ScriptedApi()
        val client = transport.client(StandardTestDispatcher(testScheduler))
        for (path in listOf("/user/login", "https://other.example.invalid/login", "../login")) {
            val error = runCatching { client.request(path) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        }
        assertTrue(transport.requests.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { ApiClient("http://auth.example.invalid/") }
    }

    @Serializable
    private class Result(val success: Boolean)
}
