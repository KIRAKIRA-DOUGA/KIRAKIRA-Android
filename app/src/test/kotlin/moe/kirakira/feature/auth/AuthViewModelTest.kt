package moe.kirakira.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.testing.AuthFixture
import moe.kirakira.testing.MainDispatcherRule
import moe.kirakira.testing.loginResponse
import moe.kirakira.testing.profileResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class AuthViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()
    private lateinit var fixture: AuthFixture
    private val models = ViewModelStore()
    private var modelIndex = 0

    @Before
    fun setUp() {
        fixture = AuthFixture(main.dispatcher)
    }

    @After
    fun tearDown() {
        models.clear()
        fixture.transport.cancelAll()
    }

    @Test
    fun submit_invalidEmail_staysOnLoginWithoutRequest() = runAuthTest {
        val model = newModel()
        model.updateEmail("not-an-email")
        model.updatePassword("password")
        assertFalse(model.submit("en-US"))
        assertEquals(AuthStep.LOGIN, model.uiState.value.step)
        assertTrue(model.uiState.value.emailInvalid)
        assertTrue(fixture.transport.requests.isEmpty())
    }

    @Test
    fun submit_noSecondFactor_completesAndClearsSecrets() = runAuthTest {
        fixture.repository.initialize()
        val model = newModel().fillLogin()
        fixture.transport.respond("""{"success":true,"have2FA":false}""")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        assertTrue(model.submit("en-US"))
        assertFalse(model.submit("en-US"))
        runCurrent()
        assertEquals(AuthSubmission.SUCCEEDED, model.uiState.value.submission)
        assertEquals("", model.uiState.value.password)
        assertEquals("", model.uiState.value.code)
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
        assertEquals(3, fixture.transport.requests.size)
    }

    @Test
    fun submit_emailFactor_entersVerificationAndResendHonorsCooldown() = runAuthTest {
        fixture.repository.initialize()
        val model = newModel().fillLogin()
        fixture.transport.respond("""{"success":true,"have2FA":true,"type":"email"}""")
        fixture.transport.respond("""{"success":true}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.LOGIN_EMAIL, model.uiState.value.step)
        assertEquals(60, model.uiState.value.resendSeconds)
        model.resend("en-US")
        assertEquals(2, fixture.transport.requests.size)
        model.updateField(AuthField.CODE, "123456")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthSubmission.SUCCEEDED, model.uiState.value.submission)
    }

    @Test
    fun submit_totpFactor_acceptsRecoveryCodeWithoutSendingEmail() = runAuthTest {
        fixture.repository.initialize()
        val model = newModel().fillLogin()
        fixture.transport.respond("""{"success":true,"have2FA":true,"type":"totp"}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.LOGIN_TOTP, model.uiState.value.step)
        assertEquals(1, fixture.transport.requests.size)
        model.updateField(AuthField.CODE, "local-recovery-code")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthSubmission.SUCCEEDED, model.uiState.value.submission)
    }

    @Test
    fun register_fourSteps_validateBeforeCreatingAccount() = runAuthTest {
        fixture.repository.initialize()
        val model = newModel()
        model.requestRegistration()
        model.updateField(AuthField.USERNAME, "Test User")
        fixture.transport.respond("""{"success":true,"isAvailableUsername":true}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.REGISTER_CREDENTIALS, model.uiState.value.step)
        model.fillLogin()
        model.updateField(AuthField.CONFIRM_PASSWORD, "wrong-password")
        assertFalse(model.submit("en-US"))
        assertEquals(R.string.auth_password_mismatch, model.uiState.value.fieldErrors[AuthField.CONFIRM_PASSWORD])
        model.updateField(AuthField.CONFIRM_PASSWORD, "password")
        fixture.transport.respond("""{"success":true,"exists":false}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.REGISTER_INVITATION, model.uiState.value.step)
        model.updateField(AuthField.INVITATION, "KIRA-AAAA-BBBB")
        fixture.transport.respond("""{"success":true,"isAvailableInvitationCode":true}""")
        fixture.transport.respond("""{"success":true}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.REGISTER_VERIFY, model.uiState.value.step)
        model.updateField(AuthField.CODE, "123456")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthSubmission.SUCCEEDED, model.uiState.value.submission)
        assertEquals("/user/registering", fixture.transport.requests[4].url.encodedPath)
    }

    @Test
    fun forgotPassword_totpHelp_returnsToEmailStep() = runAuthTest {
        val model = newModel().fillLogin()
        model.requestPasswordReset()
        fixture.transport.respond("""{"success":true,"have2FA":true,"type":"totp"}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.TOTP_HELP, model.uiState.value.step)
        assertFalse(model.uiState.value.canSubmit)
        model.back(AuthStep.TOTP_HELP)
        assertEquals(AuthStep.FORGOT_EMAIL, model.uiState.value.step)
        assertEquals("user1@example.invalid", model.uiState.value.email)
        assertEquals(1, fixture.transport.requests.size)
    }

    @Test
    fun resetPassword_success_returnsToLoginWithEmptyPassword() = runAuthTest {
        fixture.repository.initialize()
        val model = newModel().fillLogin()
        model.requestPasswordReset()
        fixture.transport.respond("""{"success":true,"have2FA":false}""")
        fixture.transport.respond("""{"success":true}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.RESET_PASSWORD, model.uiState.value.step)
        model.updatePassword("new-password")
        model.updateField(AuthField.CONFIRM_PASSWORD, "new-password")
        model.updateField(AuthField.CODE, "123456")
        fixture.transport.respond("""{"success":true}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.LOGIN, model.uiState.value.step)
        assertEquals(R.string.auth_password_reset_success, model.uiState.value.noticeRes)
        assertEquals("", model.uiState.value.password)
        assertEquals("", model.uiState.value.code)
        assertEquals("user1@example.invalid", model.uiState.value.email)
    }

    @Test
    fun back_inFlightRequest_cancelsItWithoutOverwritingNextRequest() = runAuthTest {
        val model = newModel().fillLogin()
        model.requestPasswordReset()
        val first = fixture.transport.hold()
        model.submit("en-US")
        runCurrent()
        model.back(AuthStep.FORGOT_EMAIL)
        assertTrue(first.isCanceled)
        assertEquals(AuthStep.LOGIN, model.uiState.value.step)
        model.requestRegistration()
        model.updateField(AuthField.USERNAME, "Test User")
        val second = fixture.transport.hold()
        model.submit("en-US")
        runCurrent()
        assertTrue(model.uiState.value.isSubmitting)
        first.respond("""{"success":true,"have2FA":false}""")
        runCurrent()
        assertEquals(AuthStep.REGISTER_PROFILE, model.uiState.value.step)
        assertTrue(model.uiState.value.isSubmitting)
        second.respond("""{"success":true,"isAvailableUsername":true}""")
        runCurrent()
        assertEquals(AuthStep.REGISTER_CREDENTIALS, model.uiState.value.step)
        assertFalse(model.uiState.value.isSubmitting)
    }

    @Test
    fun back_verification_preservesLoginDraftAndIgnoresStaleBackEvent() = runAuthTest {
        val model = newModel().fillLogin()
        fixture.transport.respond("""{"success":true,"have2FA":true,"type":"totp"}""")
        model.submit("en-US")
        runCurrent()
        model.updateField(AuthField.CODE, "local-backup-code")
        model.back(AuthStep.LOGIN_TOTP)
        assertEquals(AuthStep.LOGIN, model.uiState.value.step)
        assertEquals("password", model.uiState.value.password)
        assertEquals("", model.uiState.value.code)
        model.requestPasswordReset()
        model.back(AuthStep.LOGIN_TOTP)
        assertEquals(AuthStep.FORGOT_EMAIL, model.uiState.value.step)
    }

    @Test
    fun recreate_savedState_restoresOnlyEmailAndNewNavigationScope() = runAuthTest {
        val saved = SavedStateHandle()
        val first = newModel(saved).fillLogin()
        fixture.transport.respond("""{"success":true,"have2FA":true,"type":"totp"}""")
        first.submit("en-US")
        runCurrent()
        first.updateField(AuthField.CODE, "local-recovery-code")
        assertEquals(setOf("email"), saved.keys())
        val restoredState = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val restored = newModel(restoredState)
        assertEquals(AuthStep.LOGIN, restored.uiState.value.step)
        assertEquals("user1@example.invalid", restored.uiState.value.email)
        assertEquals("", restored.uiState.value.password)
        assertEquals("", restored.uiState.value.code)
        assertNotEquals(first.navigationStateKey, restored.navigationStateKey)
    }

    @Test
    fun submit_serverRejection_showsFailureWithoutCreatingSessionOrRetrying() = runAuthTest {
        fixture.repository.initialize()
        val model = newModel().fillLogin()
        fixture.transport.respond("""{"success":true,"have2FA":false}""")
        fixture.transport.respond("""{"success":false,"message":"private details"}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthSubmission.FAILED, model.uiState.value.submission)
        assertEquals(R.string.auth_credentials_rejected, model.uiState.value.errorRes)
        assertEquals(null, fixture.repository.session.value.activeUuid)
        assertEquals(2, fixture.transport.requests.size)
    }

    @Test
    fun submit_storageFailure_entersSaveRetryWithoutCredentialsOrRepeatedLogin() = runAuthTest {
        fixture.repository.initialize()
        fixture.store.writeFailure = ApiFailure.STORAGE
        val model = newModel().fillLogin()
        fixture.transport.respond("""{"success":true,"have2FA":false}""")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.SAVE_SESSION, model.uiState.value.step)
        assertEquals("", model.uiState.value.password)
        assertEquals("", model.uiState.value.code)
        fixture.store.writeFailure = null
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthSubmission.SUCCEEDED, model.uiState.value.submission)
        assertEquals(3, fixture.transport.requests.size)
    }

    @Test
    fun submit_success_isTerminalUntilPageCloses() = runAuthTest {
        fixture.repository.initialize()
        val model = newModel().fillLogin()
        fixture.transport.respond("""{"success":true,"have2FA":false}""")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        model.submit("en-US")
        runCurrent()
        model.updateEmail("user2@example.invalid")
        model.updatePassword("another-password")
        model.requestRegistration()
        model.requestPasswordReset()
        assertEquals(AuthSubmission.SUCCEEDED, model.uiState.value.submission)
        assertEquals("user1@example.invalid", model.uiState.value.email)
        assertEquals("", model.uiState.value.password)
        assertFalse(model.submit("en-US"))
        assertEquals(3, fixture.transport.requests.size)
    }

    @Test
    fun clear_oldEntry_doesNotDiscardNewEntrysPendingSession() = runAuthTest {
        fixture.repository.initialize()
        val old = newModel()
        old.cancel()
        val current = newModel().fillLogin()
        fixture.store.writeFailure = ApiFailure.STORAGE
        fixture.transport.respond("""{"success":true,"have2FA":false}""")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        current.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.SAVE_SESSION, current.uiState.value.step)
        // Navigation may release the old entry only after its exit animation finishes.
        models.put("auth-0", object : ViewModel() {})
        fixture.store.writeFailure = null
        current.submit("en-US")
        runCurrent()
        assertEquals(AuthSubmission.SUCCEEDED, current.uiState.value.submission)
        assertEquals(3, fixture.transport.requests.size)
    }

    @Test
    fun registration_back_preservesDraft_revalidatesPassword_andDoesNotResendDuringCooldown() = runAuthTest {
        fixture.repository.initialize()
        val model = newModel()
        model.requestRegistration()
        model.updateField(AuthField.USERNAME, "Fixture User")
        model.updateField(AuthField.NICKNAME, "Nickname")
        fixture.transport.respond("""{"success":true,"isAvailableUsername":true}""")
        model.submit("en-US")
        runCurrent()
        model.fillLogin()
        model.updateField(AuthField.CONFIRM_PASSWORD, "password")
        model.updateField(AuthField.PASSWORD_HINT, "optional clue")
        fixture.transport.respond("""{"success":true,"exists":false}""")
        model.submit("en-US")
        runCurrent()
        model.updateField(AuthField.INVITATION, "KIRA-AAAA-BBBB")
        fixture.transport.respond("""{"success":true,"isAvailableInvitationCode":true}""")
        fixture.transport.respond("""{"success":true}""")
        model.submit("en-US")
        runCurrent()
        model.updateField(AuthField.CODE, "123456")
        model.back(AuthStep.REGISTER_VERIFY)
        assertEquals(AuthStep.REGISTER_INVITATION, model.uiState.value.step)
        assertEquals("", model.uiState.value.code)
        assertEquals("KIRA-AAAA-BBBB", model.uiState.value.invitation)
        model.back(AuthStep.REGISTER_INVITATION)
        assertEquals("password", model.uiState.value.confirmPassword)
        assertEquals("optional clue", model.uiState.value.passwordHint)
        assertEquals("Nickname", model.uiState.value.nickname)
        model.updatePassword("changed")
        assertEquals(R.string.auth_password_mismatch, model.uiState.value.fieldErrors[AuthField.CONFIRM_PASSWORD])
        assertFalse(model.submit("en-US"))
        assertEquals(AuthField.CONFIRM_PASSWORD, model.uiState.value.focusField)
        model.updateField(AuthField.CONFIRM_PASSWORD, "changed")
        fixture.transport.respond("""{"success":true,"exists":false}""")
        model.submit("en-US")
        runCurrent()
        fixture.transport.respond("""{"success":true,"isAvailableInvitationCode":true}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.REGISTER_VERIFY, model.uiState.value.step)
        assertEquals(1, fixture.transport.requests.count { it.url.encodedPath.endsWith("sendGeneralEmailVerificationCode") })
        assertEquals(60, model.uiState.value.resendSeconds)
    }

    @Test
    fun registration_fieldErrors_areLocal_andFocusTheRelevantField() = runAuthTest {
        val model = newModel()
        model.requestRegistration()
        model.updateField(AuthField.USERNAME, " bad ")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthField.USERNAME, model.uiState.value.focusField)
        assertEquals(R.string.auth_name_invalid, model.uiState.value.fieldErrors[AuthField.USERNAME])
        assertEquals(null, model.uiState.value.errorRes)
        model.updateField(AuthField.USERNAME, "Fixture User")
        fixture.transport.respond("""{"success":true,"isAvailableUsername":false}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(R.string.auth_username_taken, model.uiState.value.fieldErrors[AuthField.USERNAME])
        fixture.transport.respond("""{"success":true,"isAvailableUsername":true}""")
        model.submit("en-US")
        runCurrent()
        model.fillLogin()
        model.updateField(AuthField.CONFIRM_PASSWORD, "password")
        fixture.transport.respond("""{"success":true,"exists":true}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthField.EMAIL, model.uiState.value.focusField)
        assertEquals(R.string.auth_email_registered, model.uiState.value.fieldErrors[AuthField.EMAIL])
        fixture.transport.respond("""{"success":true,"exists":false}""")
        model.submit("en-US")
        runCurrent()
        model.updateField(AuthField.INVITATION, "invalid")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthField.INVITATION, model.uiState.value.focusField)
        assertEquals(R.string.auth_invitation_invalid, model.uiState.value.fieldErrors[AuthField.INVITATION])
        model.updateField(AuthField.INVITATION, "KIRA-AAAA-BBBB")
        fixture.transport.respond("""{"success":true,"isAvailableInvitationCode":true}""")
        fixture.transport.respond("""{"success":true}""")
        model.submit("en-US")
        runCurrent()
        model.updateField(AuthField.CODE, "123")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthField.CODE, model.uiState.value.focusField)
        assertEquals(R.string.auth_code_invalid, model.uiState.value.fieldErrors[AuthField.CODE])
        assertEquals(null, model.uiState.value.errorRes)
    }

    @Test
    fun registration_cooldownFromAnotherPurpose_doesNotPretendRegistrationCodeWasSent() = runAuthTest {
        val model = newModel().fillLogin()
        fixture.transport.respond("""{"success":true,"have2FA":true,"type":"email"}""")
        fixture.transport.respond("""{"success":true}""")
        model.submit("en-US")
        runCurrent()
        model.back(AuthStep.LOGIN_EMAIL)
        model.requestRegistration()
        model.updateField(AuthField.USERNAME, "Fixture User")
        fixture.transport.respond("""{"success":true,"isAvailableUsername":true}""")
        model.submit("en-US")
        runCurrent()
        model.fillLogin()
        model.updateField(AuthField.CONFIRM_PASSWORD, "password")
        fixture.transport.respond("""{"success":true,"exists":false}""")
        model.submit("en-US")
        runCurrent()
        model.updateField(AuthField.INVITATION, "KIRA-AAAA-BBBB")
        fixture.transport.respond("""{"success":true,"isAvailableInvitationCode":true}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.REGISTER_INVITATION, model.uiState.value.step)
        assertEquals(AuthSubmission.FAILED, model.uiState.value.submission)
        assertEquals(1, fixture.transport.requests.count { it.url.encodedPath.endsWith("sendGeneralEmailVerificationCode") })
    }

    private fun runAuthTest(block: suspend TestScope.() -> Unit) = runTest(main.dispatcher) {
        try {
            block()
        } finally {
            // Dispose the entry before runTest drains its scheduler, including the cooldown ticker.
            models.clear()
            fixture.transport.cancelAll()
        }
    }

    private fun newModel(savedState: SavedStateHandle = SavedStateHandle()): AuthViewModel = AuthViewModel(
        savedState,
        fixture.repository,
        initialEmail = "",
        // Android's Patterns is a platform dependency; fixtures use reserved, deterministic addresses.
        isValidEmail = { it.endsWith("@example.invalid") },
    ).also { models.put("auth-${modelIndex++}", it) }

    private fun AuthViewModel.fillLogin(): AuthViewModel = apply {
        updateEmail("user1@example.invalid")
        updatePassword("password")
    }
}
