package moe.kirakira.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moe.kirakira.R
import moe.kirakira.core.credentials.PasswordDraft
import moe.kirakira.core.credentials.PasswordSaveResult
import moe.kirakira.core.credentials.PasswordSelection
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.StoredSessions
import moe.kirakira.testing.AuthFixture
import moe.kirakira.testing.MainDispatcherRule
import moe.kirakira.testing.ScriptedApi
import moe.kirakira.testing.loginResponse
import moe.kirakira.testing.profileResponse
import moe.kirakira.testing.savedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class AuthCredentialOperationTest {
    @get:Rule val main = MainDispatcherRule()
    private val models = ViewModelStore()
    private lateinit var fixture: AuthFixture
    private val host = Any()

    @Test
    fun autoRequest_cancelled_doesNotRepeatOnResumeReturnOrRecomposition() = scenario {
        val model = model()
        model.requestSavedPassword(automatic = true)
        val get = model.get()
        model.requestSavedPassword(automatic = true)
        assertEquals(get.id, model.uiState.value.credentialOperation?.id)
        model.credentialSelected(get.id, host, PasswordSelection.Cancelled, "en-US")
        model.requestSavedPassword(automatic = true)
        assertNull(model.uiState.value.credentialOperation)
        model.requestRegistration()
        model.back(AuthStep.REGISTER_PROFILE)
        model.requestSavedPassword(automatic = true)
        assertNull(model.uiState.value.credentialOperation)
        model.requestSavedPassword()
        assertNotNull(model.uiState.value.credentialOperation)
        assertTrue(fixture.transport.requests.isEmpty())
    }

    @Test
    fun noProvider_manualInputRemainsAvailable_andEmailFilterIsBoundToRoute() = scenario {
        val model = model("user2@example.invalid")
        model.requestSavedPassword()
        val get = model.get()
        assertEquals("user2@example.invalid", get.email)
        model.credentialSelected(get.id, host, PasswordSelection.Unavailable, "en-US")
        assertTrue(model.uiState.value.canEdit)
        assertEquals(R.string.auth_password_picker_unavailable, model.uiState.value.noticeRes)
        model.updatePassword("manual")
        assertTrue(model.uiState.value.canSubmit)
    }

    @Test
    fun filteredResult_wrongEmail_isIgnoredWithoutAuthentication() = scenario {
        val model = model("user2@example.invalid")
        model.requestSavedPassword()
        val get = model.get()
        model.credentialSelected(get.id, host,
            PasswordSelection.Selected(PasswordDraft("user1@example.invalid", "fixture")), "en-US")
        assertEquals("user2@example.invalid", model.uiState.value.email)
        assertTrue(fixture.transport.requests.isEmpty())
    }

    @Test
    fun systemPassword_emailFactor_keepsVerificationAndDoesNotSaveAgain() = scenario {
        val model = model()
        model.requestSavedPassword()
        val get = model.get()
        fixture.transport.respond("""{"success":true,"have2FA":true,"type":"email"}""")
        fixture.transport.respond("""{"success":true}""")
        model.credentialSelected(get.id, host,
            PasswordSelection.Selected(PasswordDraft("user1@example.invalid", "fixture")), "en-US")
        runCurrent()
        assertEquals(AuthStep.LOGIN_EMAIL, model.uiState.value.step)
        assertNull(model.uiState.value.credentialOperation)
        model.updateField(AuthField.CODE, "123456")
        successfulLogin()
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthSubmission.SUCCEEDED, model.uiState.value.submission)
        assertNull(model.uiState.value.credentialOperation)
    }

    @Test
    fun manualPassword_totpFactor_savesOnlyAfterVerificationAndDiskCommit() = scenario {
        val model = model().manual()
        fixture.transport.respond("""{"success":true,"have2FA":true,"type":"totp"}""")
        model.submit("en-US")
        runCurrent()
        assertNull(model.uiState.value.credentialOperation)
        model.updateField(AuthField.CODE, "fixture-recovery-code")
        successfulLogin()
        fixture.store.beforeWrite = { assertNull(model.uiState.value.credentialOperation) }
        model.submit("en-US")
        runCurrent()
        val save = model.save()
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
        assertEquals("password", save.draft.password)
        model.passwordSaved(save.id, host, PasswordSaveResult.CANCELLED)
        assertEquals(AuthSubmission.SUCCEEDED, model.uiState.value.submission)
        assertNull(model.uiState.value.credentialOperation)
        assertEquals("", model.uiState.value.password)
    }

    @Test
    fun localSaveFailure_retryRetainsPendingPassword_withoutReplayingLogin() = scenario {
        fixture.store.writeFailure = ApiFailure.STORAGE
        val model = model().manual()
        noFactorLogin()
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.SAVE_SESSION, model.uiState.value.step)
        assertEquals("", model.uiState.value.password)
        assertNull(model.uiState.value.credentialOperation)
        fixture.store.writeFailure = null
        model.submit("en-US")
        runCurrent()
        val save = model.save()
        assertEquals("password", save.draft.password)
        model.passwordSaved(save.id, host, PasswordSaveResult.FAILED)
        assertEquals(AuthSubmission.SUCCEEDED, model.uiState.value.submission)
        assertEquals(R.string.auth_password_save_failed, model.uiState.value.noticeRes)
        assertEquals(3, fixture.transport.requests.size)
    }

    @Test
    fun rotation_abandonsClaim_discardsOldResult_withoutAutoReopening() = scenario {
        val model = model()
        model.requestSavedPassword(automatic = true)
        val get = model.get()
        model.abandonCredentialOperation(get.id, host)
        model.requestSavedPassword(automatic = true)
        model.credentialSelected(get.id, host,
            PasswordSelection.Selected(PasswordDraft("old@example.invalid", "obsolete")), "en-US")
        assertNull(model.uiState.value.credentialOperation)
        assertEquals("", model.uiState.value.password)
        assertTrue(fixture.transport.requests.isEmpty())
    }

    @Test
    fun exit_discardsOldGetAndPendingSave_andDoesNotRestart() = scenario {
        val model = model()
        model.requestSavedPassword()
        val get = model.get()
        model.cancel()
        model.credentialSelected(get.id, host,
            PasswordSelection.Selected(PasswordDraft("old@example.invalid", "obsolete")), "en-US")
        model.requestSavedPassword()
        assertNull(model.uiState.value.credentialOperation)
        assertEquals("", model.uiState.value.password)
        val another = model().manual()
        noFactorLogin()
        another.submit("en-US")
        runCurrent()
        val save = another.save()
        another.cancel()
        another.passwordSaved(save.id, host, PasswordSaveResult.SAVED)
        assertNull(another.uiState.value.credentialOperation)
        assertFalse(another.uiState.value.submission == AuthSubmission.SUCCEEDED)
    }

    @Test
    fun changedSystemPassword_promptsSaveAfterFullSuccess() = scenario {
        val model = model()
        model.requestSavedPassword()
        val get = model.get()
        fixture.transport.respond("""{"success":true,"have2FA":true,"type":"totp"}""")
        model.credentialSelected(get.id, host,
            PasswordSelection.Selected(PasswordDraft("user1@example.invalid", "fixture")), "en-US")
        runCurrent()
        model.back(AuthStep.LOGIN_TOTP)
        model.updatePassword("edited")
        noFactorLogin()
        model.submit("en-US")
        runCurrent()
        assertEquals("edited", model.save().draft.password)
    }

    @Test
    fun resetPassword_localCleanupRetry_precedesProviderUpdate() = scenario {
        fixture.store.saved = StoredSessions(baseUrl = ScriptedApi.BASE_URL, accounts = listOf(savedAccount(1)), activeUuid = "fixture-1")
        // Use a fresh repository, since the fixture above was already initialized.
        val resetFixture = AuthFixture(main.dispatcher)
        resetFixture.store.saved = fixture.store.saved
        fixture = resetFixture
        fixture.repository.initialize()
        val model = model().manual()
        model.requestPasswordReset()
        fixture.transport.respond("""{"success":true,"have2FA":false}""")
        fixture.transport.respond("""{"success":true}""")
        model.submit("en-US")
        runCurrent()
        model.updatePassword("new-password")
        model.updateField(AuthField.CONFIRM_PASSWORD, "new-password")
        model.updateField(AuthField.CODE, "123456")
        fixture.store.writeFailure = ApiFailure.STORAGE
        fixture.transport.respond("""{"success":true}""")
        model.submit("en-US")
        runCurrent()
        assertEquals(AuthStep.SAVE_PASSWORD_RESET, model.uiState.value.step)
        assertNull(model.uiState.value.credentialOperation)
        fixture.store.writeFailure = null
        model.submit("en-US")
        runCurrent()
        val save = model.save()
        assertTrue(save.passwordReset)
        assertNull(fixture.repository.session.value.activeUuid)
        assertTrue(fixture.repository.session.value.accounts.single().needsLogin)
        model.passwordSaved(save.id, host, PasswordSaveResult.CANCELLED)
        assertEquals(AuthStep.LOGIN, model.uiState.value.step)
        assertEquals("", model.uiState.value.password)
        assertEquals(3, fixture.transport.requests.size)
    }

    private fun model(email: String = "") = AuthViewModel(
        SavedStateHandle(), fixture.repository, email, credentialsEnabled = true,
        isValidEmail = { it.endsWith("@example.invalid") },
    ).also { models.put("auth-${models.keys().size}", it) }

    private fun AuthViewModel.manual() = apply {
        updateEmail("user1@example.invalid")
        updatePassword("password")
    }

    private fun AuthViewModel.get() = (uiState.value.credentialOperation as AuthCredentialOperation.Get).also {
        assertTrue(claimCredentialOperation(it.id, host))
    }
    private fun AuthViewModel.save() = (uiState.value.credentialOperation as AuthCredentialOperation.Save).also {
        assertTrue(claimCredentialOperation(it.id, host))
    }
    private fun successfulLogin() {
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
    }
    private fun noFactorLogin() {
        fixture.transport.respond("""{"success":true,"have2FA":false}""")
        successfulLogin()
    }
    private fun scenario(block: suspend TestScope.() -> Unit) = runTest(main.dispatcher) {
        fixture = AuthFixture(main.dispatcher)
        fixture.repository.initialize()
        try { block() } finally {
            models.clear()
            fixture.transport.cancelAll()
        }
    }
}
