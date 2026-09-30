package moe.kirakira.data.auth

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.testing.ScriptedApi
import moe.kirakira.testing.assertApiFailure
import moe.kirakira.testing.bodyText
import moe.kirakira.testing.loginResponse
import moe.kirakira.testing.profileResponse
import moe.kirakira.testing.savedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthApiTest {
    @Test
    fun login_eachFactor_sendsOnlyItsVerificationField() = runTest {
        val transport = ScriptedApi()
        val api = AuthApi(transport.client(StandardTestDispatcher(testScheduler)))
        SecondFactor.entries.forEach { factor ->
            transport.respond(loginResponse())
            api.login("user1@example.invalid", "local-password-hash", "123456", factor)
            val body = Json.parseToJsonElement(transport.requests.last().bodyText()).jsonObject
            assertEquals("local-password-hash", body["passwordHash"]?.jsonPrimitive?.content)
            assertFalse(body.containsKey("password"))
            assertEquals(
                "123456".takeIf { factor == SecondFactor.EMAIL },
                body["verificationCode"]?.jsonPrimitive?.contentOrNull,
            )
            assertEquals(
                "123456".takeIf { factor == SecondFactor.TOTP },
                body["clientOtp"]?.jsonPrimitive?.contentOrNull,
            )
            assertEquals(null, transport.requests.last().header("Cookie"))
        }
    }

    @Test
    fun login_missingOrUnsafeIdentity_neverCreatesAccount() = runTest {
        val transport = ScriptedApi()
        val api = AuthApi(transport.client(StandardTestDispatcher(testScheduler)))
        val responses = listOf(
            """{"success":true}""",
            """{"success":true,"UUID":"fixture-1","uid":1}""",
            """{"success":true,"UUID":"fixture;other=1","uid":1,"token":"local"}""",
            """{"success":true,"UUID":"fixture-1","uid":-1,"token":"local"}""",
            """{"success":true,"UUID":"fixture-1","uid":1,"token":"local;other=1"}""",
        )
        responses.forEach {
            transport.respond(it)
            assertApiFailure(ApiFailure.INVALID_RESPONSE) {
                api.login("user1@example.invalid", "hash", "", SecondFactor.NONE)
            }
        }
    }

    @Test
    fun secondFactor_knownAndUnknownTypes_areHandledConservatively() = runTest {
        val transport = ScriptedApi()
        val api = AuthApi(transport.client(StandardTestDispatcher(testScheduler)))
        for ((json, factor) in listOf(
            """{"success":true,"have2FA":false}""" to SecondFactor.NONE,
            """{"success":true,"have2FA":true,"type":"email"}""" to SecondFactor.EMAIL,
            """{"success":true,"have2FA":true,"type":"totp"}""" to SecondFactor.TOTP,
        )) {
            transport.respond(json)
            assertEquals(factor, api.secondFactor("user1@example.invalid"))
        }
        transport.respond("""{"success":true,"have2FA":true,"type":"future"}""")
        assertApiFailure(ApiFailure.INVALID_RESPONSE) { api.secondFactor("user1@example.invalid") }
        transport.respond("""{"success":false,"have2FA":false,"message":"private details"}""")
        assertApiFailure(ApiFailure.REJECTED) { api.secondFactor("user1@example.invalid") }
    }

    @Test
    fun sendCode_purpose_selectsBackendTemplateAndBusinessName() = runTest {
        val transport = ScriptedApi()
        val api = AuthApi(transport.client(StandardTestDispatcher(testScheduler)))
        val expected = listOf(
            VerificationPurpose.LOGIN to ("login" to "SendLoginVerificationCode"),
            VerificationPurpose.REGISTRATION to ("registration" to "SendRegistrationVerificationCode"),
            VerificationPurpose.PASSWORD_RESET to ("forgot-password" to "SendResetPasswordVerificationCode"),
        )
        for ((purpose, fields) in expected) {
            transport.respond("""{"success":true}""")
            api.sendCode("user1@example.invalid", purpose, "zh-Hans-CN")
            val request = transport.requests.last()
            val body = Json.parseToJsonElement(request.bodyText()).jsonObject
            assertEquals("/user/sendGeneralEmailVerificationCode", request.url.encodedPath)
            assertEquals(fields.first, body["exclusiveBusinessName"]?.jsonPrimitive?.content)
            assertEquals(fields.second, body["mailTemplate"]?.jsonPrimitive?.content)
            assertEquals("zh-Hans-CN", body["clientLanguage"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun sendCode_businessRateLimits_takePrecedenceOverGenericRejection() = runTest {
        val transport = ScriptedApi()
        val api = AuthApi(transport.client(StandardTestDispatcher(testScheduler)))
        for ((flag, failure) in mapOf(
            "isCoolingDown" to ApiFailure.RATE_LIMITED,
            "isMaxDailyCreateAttempts" to ApiFailure.DAILY_LIMIT,
            "isMaxDailyVerifierAttempts" to ApiFailure.DAILY_LIMIT,
        )) {
            transport.respond("""{"success":false,"$flag":true}""")
            assertApiFailure(failure) { api.sendCode("user1@example.invalid", VerificationPurpose.LOGIN, "en-US") }
        }
    }

    @Test
    fun profile_identityMismatch_isRejectedAndCookieUsesRequestedAccount() = runTest {
        val transport = ScriptedApi()
        val api = AuthApi(transport.client(StandardTestDispatcher(testScheduler)))
        transport.respond(profileResponse(2))
        assertApiFailure(ApiFailure.INVALID_RESPONSE) { api.profile(savedAccount(1)) }
        assertEquals("uuid=fixture-1; uid=1; token=fixture-token-1", transport.requests.single().header("Cookie"))
    }

    @Test
    fun profile_businessFailure_doesNotClaimTokenExpiry() = runTest {
        val transport = ScriptedApi()
        val api = AuthApi(transport.client(StandardTestDispatcher(testScheduler)))
        transport.respond("""{"success":false,"message":"database error"}""")
        assertApiFailure(ApiFailure.REJECTED) { api.profile(savedAccount(1)) }
    }
}
