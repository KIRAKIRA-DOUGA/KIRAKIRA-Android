package moe.kirakira.data.auth

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.testing.AuthFixture
import moe.kirakira.testing.ScriptedApi
import moe.kirakira.testing.assertApiFailure
import moe.kirakira.testing.bodyText
import moe.kirakira.testing.loginResponse
import moe.kirakira.testing.profileResponse
import moe.kirakira.testing.savedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private val owner = AuthFlowOwner()

    @Test
    fun login_success_hashesPasswordAndPublishesOnlyAfterSaving() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.repository.initialize()
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        fixture.store.beforeWrite = { assertNull(fixture.repository.session.value.activeUuid) }
        fixture.repository.login("user1@example.invalid", "password", "", SecondFactor.NONE, owner)
        val request = Json.parseToJsonElement(fixture.transport.requests.first().bodyText()).jsonObject
        assertEquals(
            "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
            request["passwordHash"]?.jsonPrimitive?.content,
        )
        assertEquals("fixture-1", fixture.store.saved.activeUuid)
        assertEquals("Local 1", fixture.repository.session.value.activeProfile?.displayName)
        assertFalse(fixture.repository.hasPendingAuthentication(owner))
    }

    @Test
    fun login_existingUuid_replacesSessionWithoutDuplicatingAccount() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.seedAccounts(active = "fixture-2")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        fixture.repository.login("user1@example.invalid", "password", "", SecondFactor.NONE, owner)
        assertEquals(setOf("fixture-1", "fixture-2"), fixture.store.saved.accounts.map { it.profile.uuid }.toSet())
        assertEquals(2, fixture.store.saved.accounts.size)
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
    }

    @Test
    fun login_storageFailure_retrySavesWithoutRepeatingAuthenticationOrProfileRequest() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.seedAccounts(active = "fixture-2")
        fixture.store.writeFailure = ApiFailure.STORAGE
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        assertApiFailure(ApiFailure.STORAGE) {
            fixture.repository.login("user1@example.invalid", "password", "123456", SecondFactor.EMAIL, owner)
        }
        assertEquals("fixture-2", fixture.repository.session.value.activeUuid)
        assertTrue(fixture.repository.hasPendingAuthentication(owner))
        fixture.store.writeFailure = null
        fixture.repository.finishAuthentication(owner)
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
        assertEquals(2, fixture.transport.requests.size)
        assertFalse(fixture.repository.hasPendingAuthentication(owner))
    }

    @Test
    fun login_profileUnavailable_usesAuthenticatedBasicIdentity() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.repository.initialize()
        fixture.transport.respond(loginResponse())
        fixture.transport.fail(IOException("offline"))
        fixture.repository.login("user1@example.invalid", "password", "", SecondFactor.NONE, owner)
        assertEquals("user1@example.invalid", fixture.repository.session.value.activeProfile?.displayName)
        assertEquals("fixture-1", fixture.store.saved.activeUuid)
    }

    @Test
    fun login_profileIdentityMismatch_doesNotSaveOrOfferToSaveUnverifiedIdentity() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.repository.initialize()
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse(2))
        assertApiFailure(ApiFailure.INVALID_RESPONSE) {
            fixture.repository.login("user1@example.invalid", "password", "", SecondFactor.NONE, owner)
        }
        assertEquals(0, fixture.store.writes)
        assertNull(fixture.repository.session.value.activeUuid)
        assertFalse(fixture.repository.hasPendingAuthentication(owner))
    }

    @Test
    fun select_networkFailure_keepsBothSessionsAndCurrentAccount() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.seedAccounts(active = "fixture-1")
        fixture.transport.fail(IOException("offline"))
        assertApiFailure(ApiFailure.NETWORK) { fixture.repository.select("fixture-2") }
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
        assertTrue(fixture.store.saved.accounts.all { it.token != null })
        assertTrue(fixture.repository.session.value.accounts.none { it.needsLogin })
        assertFalse(fixture.repository.session.value.isBusy)
    }

    @Test
    fun select_serverRejection_preservesTokenAndOtherActiveAccount() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.seedAccounts(active = "fixture-1")
        fixture.transport.respond("""{"success":false}""")
        assertApiFailure(ApiFailure.REJECTED) { fixture.repository.select("fixture-2") }
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
        assertNotNull(fixture.store.saved.accounts.last().token)
        assertTrue(fixture.repository.session.value.accounts.last().needsLogin)
    }

    @Test
    fun select_expiredCurrentToken_clearsOnlyThatTokenAndFallsBackToGuest() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.seedAccounts(active = "fixture-2")
        fixture.transport.respond("{}", status = 401)
        assertApiFailure(ApiFailure.SESSION_EXPIRED) { fixture.repository.select("fixture-2") }
        assertNull(fixture.repository.session.value.activeUuid)
        assertNotNull(fixture.store.saved.accounts.first().token)
        assertNull(fixture.store.saved.accounts.last().token)
        assertEquals(2, fixture.repository.session.value.accounts.size)
    }

    @Test
    fun select_storageFailure_doesNotPublishNewSelection() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.seedAccounts(active = "fixture-1")
        fixture.transport.respond(profileResponse(2))
        fixture.store.writeFailure = ApiFailure.STORAGE
        assertApiFailure(ApiFailure.STORAGE) { fixture.repository.select("fixture-2") }
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
        assertEquals("fixture-1", fixture.store.saved.activeUuid)
    }

    @Test
    fun select_guestAndRemove_areOfflineAndPreserveUnrelatedAccounts() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.seedAccounts(active = "fixture-1")
        fixture.repository.select(null)
        assertNull(fixture.repository.session.value.activeUuid)
        assertEquals(2, fixture.store.saved.accounts.size)
        fixture.repository.remove("fixture-1")
        assertEquals(listOf("fixture-2"), fixture.store.saved.accounts.map { it.profile.uuid })
        assertTrue(fixture.transport.requests.isEmpty())
    }

    @Test
    fun initialize_unreadableVault_refusesLoginUntilExplicitLocalReset() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.store.readFailure = ApiFailure.STORAGE
        fixture.repository.initialize()
        assertEquals(ApiFailure.STORAGE, fixture.repository.session.value.error)
        assertApiFailure(ApiFailure.STORAGE) {
            fixture.repository.login("user1@example.invalid", "password", "", SecondFactor.NONE, owner)
        }
        assertTrue(fixture.transport.requests.isEmpty())
        assertEquals(0, fixture.store.writes)
        assertEquals(0, fixture.store.resets)
        fixture.repository.resetLocalAccounts()
        assertEquals(1, fixture.store.resets)
        assertNull(fixture.repository.session.value.error)
    }

    @Test
    fun sendCode_cooldown_isSharedAcrossPurposesAndEmailCase() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.transport.respond("""{"success":true}""")
        fixture.repository.sendCode("User1@example.invalid", VerificationPurpose.LOGIN, "en-US")
        assertEquals(60, fixture.repository.resendSeconds("user1@example.invalid"))
        assertApiFailure(ApiFailure.RATE_LIMITED) {
            fixture.repository.sendCode("user1@example.invalid", VerificationPurpose.PASSWORD_RESET, "en-US")
        }
        fixture.timeMillis = 59_001
        assertEquals(1, fixture.repository.resendSeconds("user1@example.invalid"))
        fixture.timeMillis = 60_000
        assertEquals(0, fixture.repository.resendSeconds("user1@example.invalid"))
        fixture.transport.respond("""{"success":true}""")
        fixture.repository.sendCode("user1@example.invalid", VerificationPurpose.PASSWORD_RESET, "en-US")
        assertEquals(2, fixture.transport.requests.size)
    }

    @Test
    fun sendCode_serverRateLimit_startsCooldownWithoutAutomaticRetry() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.transport.respond("{}", status = 429)
        assertApiFailure(ApiFailure.RATE_LIMITED) {
            fixture.repository.sendCode("user1@example.invalid", VerificationPurpose.LOGIN, "en-US")
        }
        assertEquals(60, fixture.repository.resendSeconds("user1@example.invalid"))
        assertEquals(1, fixture.transport.requests.size)
    }

    @Test
    fun sendCode_concurrentRequests_sendOnlyOnce() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        val pending = fixture.transport.hold()
        val first = async {
            fixture.repository.sendCode("user1@example.invalid", VerificationPurpose.LOGIN, "en-US")
        }
        runCurrent()
        val second = async {
            assertApiFailure(ApiFailure.RATE_LIMITED) {
                fixture.repository.sendCode("user1@example.invalid", VerificationPurpose.REGISTRATION, "en-US")
            }
        }
        runCurrent()
        pending.respond("""{"success":true}""")
        first.await()
        second.await()
        assertEquals(1, fixture.transport.requests.size)
    }

    @Test
    fun resetPassword_localFailure_retryOnlyClearsAffectedLocalSession() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.seedAccounts(active = "fixture-2")
        fixture.transport.respond("""{"success":true}""")
        fixture.store.writeFailure = ApiFailure.STORAGE
        assertApiFailure(ApiFailure.STORAGE) {
            fixture.repository.resetPassword("USER1@example.invalid", "new-password", "123456", owner)
        }
        assertTrue(fixture.repository.hasPendingPasswordReset(owner))
        fixture.store.writeFailure = null
        fixture.repository.finishPasswordReset(owner)
        assertNull(fixture.store.saved.accounts.first().token)
        assertNotNull(fixture.store.saved.accounts.last().token)
        assertEquals("fixture-2", fixture.repository.session.value.activeUuid)
        assertEquals(1, fixture.transport.requests.size)
        assertFalse(fixture.repository.hasPendingPasswordReset(owner))
    }

    @Test
    fun login_canceledOlderCommit_doesNotDiscardNewerPendingSession() = runTest {
        val fixture = AuthFixture(StandardTestDispatcher(testScheduler))
        fixture.repository.initialize()
        val finishFirstWrite = CompletableDeferred<Unit>()
        fixture.store.beforeWrite = {
            if (fixture.store.writes == 1) finishFirstWrite.await() else throw ApiException(ApiFailure.STORAGE)
        }
        fixture.transport.respond(loginResponse(1))
        fixture.transport.respond(profileResponse(1))
        val first = launch {
            fixture.repository.login("user1@example.invalid", "password", "", SecondFactor.NONE, owner)
        }
        runCurrent()
        assertEquals(1, fixture.store.writes)
        first.cancel()
        fixture.repository.discardPendingAuthentication(owner)
        fixture.transport.respond(loginResponse(2))
        fixture.transport.respond(profileResponse(2))
        val second = async {
            assertApiFailure(ApiFailure.STORAGE) {
                fixture.repository.login("user2@example.invalid", "password", "", SecondFactor.NONE, owner)
            }
        }
        runCurrent()
        finishFirstWrite.complete(Unit)
        first.join()
        second.await()
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
        assertTrue(fixture.repository.hasPendingAuthentication(owner))
    }

    private suspend fun AuthFixture.seedAccounts(active: String) {
        store.saved = StoredSessions(
            baseUrl = ScriptedApi.BASE_URL,
            accounts = listOf(savedAccount(1), savedAccount(2)),
            activeUuid = active,
        )
        repository.initialize()
    }
}
