package moe.kirakira.feature.auth

import android.util.Patterns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.Normalizer
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.core.credentials.PasswordDraft
import moe.kirakira.core.credentials.PasswordSaveResult
import moe.kirakira.core.credentials.PasswordSelection
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.AuthFlowOwner
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.data.auth.VerificationPurpose
import moe.kirakira.ui.components.messageRes

internal class AuthViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: AuthRepository,
    initialEmail: String,
    private val credentialsEnabled: Boolean = false,
    private val isValidEmail: (String) -> Boolean = { Patterns.EMAIL_ADDRESS.matcher(it).matches() },
) : ViewModel() {
    // Only email survives process death. Passwords, verification codes and steps remain entry-local.
    private val _uiState = MutableStateFlow(AuthUiState(email = savedStateHandle[EMAIL_KEY] ?: initialEmail))
    val uiState = _uiState.asStateFlow()
    // A recreated process must not restore a step whose credentials only existed in memory.
    val navigationStateKey: String = UUID.randomUUID().toString()
    private var requestJob: Job? = null
    private var requestGeneration = 0
    private val flowOwner = AuthFlowOwner()
    private val credentialEmailFilter = initialEmail.trim().takeIf { it.isNotEmpty() }
    private var automaticCredentialRequested = false
    private var credentialSequence = 0L
    private var credentialClaim: Any? = null
    private var pendingPassword: PasswordDraft? = null
    private var passwordNeedsSave = true
    private var closed = false
    private var sentCode: Pair<String, VerificationPurpose>? = null

    fun requestSavedPassword(automatic: Boolean = false) {
        val state = _uiState.value
        if (closed || state.step != AuthStep.LOGIN || !state.canEdit || state.credentialOperation != null) return
        if (automatic && automaticCredentialRequested) return
        automaticCredentialRequested = true
        if (!credentialsEnabled) return
        _uiState.update {
            it.copy(credentialOperation = AuthCredentialOperation.Get(++credentialSequence, credentialEmailFilter, automatic))
        }
    }

    fun claimCredentialOperation(id: Long, host: Any): Boolean {
        if (closed || _uiState.value.credentialOperation?.id != id || credentialClaim != null) return false
        credentialClaim = host
        return true
    }

    fun credentialSelected(id: Long, host: Any, result: PasswordSelection, language: String) {
        val operation = _uiState.value.credentialOperation as? AuthCredentialOperation.Get ?: return
        if (operation.id != id || credentialClaim !== host || closed) return
        credentialClaim = null
        _uiState.update { it.copy(credentialOperation = null) }
        when (result) {
            is PasswordSelection.Selected -> {
                if (operation.email != null && !operation.email.equals(result.draft.email, ignoreCase = true)) return
                savedStateHandle[EMAIL_KEY] = result.draft.email
                passwordNeedsSave = false
                _uiState.update {
                    it.copy(email = result.draft.email, password = result.draft.password, fieldErrors = emptyMap())
                }
                submit(language)
            }
            PasswordSelection.Failed -> _uiState.update { it.copy(noticeRes = R.string.auth_password_picker_failed) }
            PasswordSelection.Unavailable -> if (!operation.automatic) {
                _uiState.update { it.copy(noticeRes = R.string.auth_password_picker_unavailable) }
            }
            PasswordSelection.Cancelled -> Unit
        }
    }

    fun passwordSaved(id: Long, host: Any, result: PasswordSaveResult) {
        val operation = _uiState.value.credentialOperation as? AuthCredentialOperation.Save ?: return
        if (operation.id != id || credentialClaim !== host || closed) return
        credentialClaim = null
        finishSuccess(operation.passwordReset, result == PasswordSaveResult.FAILED)
    }

    fun abandonCredentialOperation(id: Long, host: Any) {
        if (_uiState.value.credentialOperation?.id != id || credentialClaim !== host) return
        // A destroyed host never replays a picker or a save; its eventual callback is obsolete.
        val operation = _uiState.value.credentialOperation
        credentialClaim = null
        if (operation is AuthCredentialOperation.Save) finishSuccess(operation.passwordReset)
        else _uiState.update { it.copy(credentialOperation = null) }
    }

    fun consumeFocus(id: Long) {
        if (_uiState.value.focusRequestId == id) _uiState.update { it.copy(focusField = null) }
    }

    private fun preferManualInput() {
        if (_uiState.value.step != AuthStep.LOGIN) return
        // Consume even a not-yet-launched automatic request. Focus alone never calls this.
        automaticCredentialRequested = true
        if (_uiState.value.credentialOperation is AuthCredentialOperation.Get) {
            // Invalidate synchronously before accepting input; the host effect then cancels its job.
            credentialClaim = null
            _uiState.update { it.copy(credentialOperation = null) }
        }
    }

    init {
        viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(resendSeconds = repository.resendSeconds(it.email.trim())) }
                delay(1_000)
            }
        }
    }

    fun updateEmail(value: String) {
        if (closed || !_uiState.value.canEdit) return
        savedStateHandle[EMAIL_KEY] = value
        if (value != _uiState.value.email) passwordNeedsSave = true
        edit { it.copy(email = value, emailInvalid = false, fieldErrors = it.fieldErrors - AuthField.EMAIL, code = "") }
    }

    fun updatePassword(value: String) {
        if (closed || !_uiState.value.canEdit) return
        if (value != _uiState.value.password) passwordNeedsSave = true
        edit {
            val errors = it.fieldErrors - AuthField.PASSWORD - AuthField.CONFIRM_PASSWORD
            it.copy(password = value, fieldErrors = if (it.confirmPassword.isNotEmpty() && value != it.confirmPassword) {
                errors + (AuthField.CONFIRM_PASSWORD to R.string.auth_password_mismatch)
            } else errors)
        }
    }

    fun updateField(field: AuthField, value: String) = edit {
        val updated = when (field) {
            AuthField.EMAIL -> it.copy(email = value)
            AuthField.PASSWORD -> it.copy(password = value)
            AuthField.USERNAME -> it.copy(username = value)
            AuthField.NICKNAME -> it.copy(nickname = value)
            AuthField.INVITATION -> it.copy(invitation = value)
            AuthField.PASSWORD_HINT -> it.copy(passwordHint = value)
            AuthField.CONFIRM_PASSWORD -> it.copy(confirmPassword = value)
            AuthField.CODE -> it.copy(code = value.trim())
        }
        updated.copy(fieldErrors = it.fieldErrors - field)
    }

    private fun edit(transform: (AuthUiState) -> AuthUiState) {
        if (closed || !_uiState.value.canEdit) return
        preferManualInput()
        _uiState.update { transform(it).copy(submission = AuthSubmission.IDLE, errorRes = null, noticeRes = null) }
    }

    fun requestRegistration() = changeFlow(AuthStep.REGISTER_PROFILE)
    fun requestPasswordReset() = changeFlow(AuthStep.FORGOT_EMAIL)

    private fun changeFlow(step: AuthStep) {
        if (closed || !_uiState.value.canEdit) return
        preferManualInput()
        releaseCredentials()
        repository.discardPendingAuthentication(flowOwner)
        _uiState.value = AuthUiState(step = step, email = _uiState.value.email)
        requestFirstField()
    }

    fun back(from: AuthStep) {
        val state = _uiState.value
        if (state.step != from || state.submission in setOf(AuthSubmission.FINISHING, AuthSubmission.SUCCEEDED)) return
        val previous = from.previousStep ?: return
        cancelRequest()
        pendingPassword = null
        credentialClaim = null
        repository.discardPendingAuthentication(flowOwner)
        _uiState.value = if (previous == AuthStep.LOGIN &&
            from != AuthStep.LOGIN_EMAIL && from != AuthStep.LOGIN_TOTP
        ) {
            AuthUiState(email = state.email)
        } else {
            state.copy(
                step = previous,
                code = "",
                fieldErrors = emptyMap(),
                focusField = null,
                credentialOperation = null,
                emailInvalid = false,
                submission = AuthSubmission.IDLE,
                errorRes = null,
                noticeRes = null,
            )
        }
    }

    fun submit(language: String): Boolean {
        val state = _uiState.value
        if (closed || !state.canSubmit) return false
        preferManualInput()
        val email = state.email.trim()
        if (state.step in setOf(AuthStep.LOGIN, AuthStep.REGISTER_CREDENTIALS, AuthStep.FORGOT_EMAIL) &&
            !isValidEmail(email)
        ) {
            fieldFail(AuthField.EMAIL, R.string.auth_email_invalid)
            return false
        }
        if (state.step in setOf(AuthStep.REGISTER_CREDENTIALS, AuthStep.RESET_PASSWORD) && !matchingPasswords()) return false
        savedStateHandle[EMAIL_KEY] = email
        _uiState.update { it.copy(email = email, emailInvalid = false) }
        perform {
            when (state.step) {
                AuthStep.LOGIN -> when (repository.secondFactor(email)) {
                    SecondFactor.NONE -> signIn(SecondFactor.NONE)
                    SecondFactor.EMAIL -> {
                        moveTo(AuthStep.LOGIN_EMAIL)
                        sendCode(VerificationPurpose.LOGIN, language)
                    }
                    SecondFactor.TOTP -> moveTo(AuthStep.LOGIN_TOTP)
                }
                AuthStep.LOGIN_EMAIL -> {
                    if (validEmailCode()) signIn(SecondFactor.EMAIL)
                }
                AuthStep.LOGIN_TOTP -> signIn(SecondFactor.TOTP)
                AuthStep.REGISTER_PROFILE -> {
                    val username = Normalizer.normalize(state.username, Normalizer.Form.NFC)
                    val nickname = Normalizer.normalize(state.nickname, Normalizer.Form.NFC)
                    if (!isValidAuthName(username)) {
                        fieldFail(AuthField.USERNAME, R.string.auth_name_invalid)
                    } else if (nickname.isNotEmpty() && !isValidAuthName(nickname)) {
                        fieldFail(AuthField.NICKNAME, R.string.auth_name_invalid)
                    } else if (!repository.checkUsername(username)) {
                        fieldFail(AuthField.USERNAME, R.string.auth_username_taken)
                    } else {
                        _uiState.update { it.copy(username = username, nickname = nickname) }
                        moveTo(AuthStep.REGISTER_CREDENTIALS)
                    }
                }
                AuthStep.REGISTER_CREDENTIALS -> when {
                    state.passwordHint.contains(state.password) -> fieldFail(AuthField.PASSWORD_HINT, R.string.auth_hint_invalid)
                    repository.emailExists(email) -> fieldFail(AuthField.EMAIL, R.string.auth_email_registered)
                    else -> moveTo(AuthStep.REGISTER_INVITATION)
                }
                AuthStep.REGISTER_INVITATION -> when {
                    !state.invitation.trim().matches(Regex("KIRA-[A-Z0-9]{4}-[A-Z0-9]{4}")) -> {
                        fieldFail(AuthField.INVITATION, R.string.auth_invitation_invalid)
                    }
                    !repository.checkInvitation(state.invitation.trim()) -> {
                        fieldFail(AuthField.INVITATION, R.string.auth_invitation_invalid)
                    }
                    else -> {
                        // An existing cooldown belongs to this email, including a return to this step.
                        sendCode(VerificationPurpose.REGISTRATION, language)
                        moveTo(AuthStep.REGISTER_VERIFY)
                    }
                }
                AuthStep.REGISTER_VERIFY -> {
                    if (validEmailCode()) {
                        rememberPassword()
                        repository.register(
                            email = email,
                            password = state.password,
                            code = state.code,
                            invitation = state.invitation.trim(),
                            username = state.username,
                            nickname = state.nickname,
                            passwordHint = state.passwordHint,
                            owner = flowOwner,
                        )
                        complete()
                    }
                }
                AuthStep.FORGOT_EMAIL -> {
                    if (repository.secondFactor(email) == SecondFactor.TOTP) {
                        moveTo(AuthStep.TOTP_HELP)
                    } else {
                        moveTo(AuthStep.RESET_PASSWORD)
                        sendCode(VerificationPurpose.PASSWORD_RESET, language)
                    }
                }
                AuthStep.RESET_PASSWORD -> {
                    if (validEmailCode() && matchingPasswords()) {
                        rememberPassword()
                        repository.resetPassword(email, state.password, state.code, flowOwner)
                        complete(passwordReset = true)
                    }
                }
                AuthStep.SAVE_SESSION -> {
                    repository.finishAuthentication(flowOwner)
                    complete()
                }
                AuthStep.SAVE_PASSWORD_RESET -> {
                    repository.finishPasswordReset(flowOwner)
                    complete(passwordReset = true)
                }
                AuthStep.TOTP_HELP -> Unit
            }
        }
        return true
    }

    fun resend(language: String) {
        val state = _uiState.value
        if (!state.canEdit || repository.resendSeconds(state.email.trim()) > 0) return
        val purpose = when (state.step) {
            AuthStep.LOGIN_EMAIL -> VerificationPurpose.LOGIN
            AuthStep.REGISTER_VERIFY -> VerificationPurpose.REGISTRATION
            AuthStep.RESET_PASSWORD -> VerificationPurpose.PASSWORD_RESET
            else -> return
        }
        perform { sendCode(purpose, language) }
    }

    private suspend fun sendCode(purpose: VerificationPurpose, language: String) {
        val email = _uiState.value.email.trim()
        val request = email.lowercase() to purpose
        // A previous step's code can be reused only for the same email and purpose.
        if (sentCode == request && repository.resendSeconds(email) > 0) return
        repository.sendCode(email, purpose, language)
        currentCoroutineContext().ensureActive()
        sentCode = request
        _uiState.update { it.copy(noticeRes = R.string.auth_code_sent, resendSeconds = repository.resendSeconds(it.email)) }
    }

    private suspend fun signIn(factor: SecondFactor) {
        val state = _uiState.value
        rememberPassword()
        repository.login(state.email.trim(), state.password, state.code, factor, flowOwner)
        complete()
    }

    private fun matchingPasswords(): Boolean {
        val state = _uiState.value
        if (state.password.isEmpty() || state.password != state.confirmPassword) {
            fieldFail(AuthField.CONFIRM_PASSWORD, R.string.auth_password_mismatch)
            return false
        }
        return true
    }

    private fun validEmailCode(): Boolean {
        if (!_uiState.value.code.matches(Regex("[0-9]{6}"))) {
            fieldFail(AuthField.CODE, R.string.auth_code_invalid)
            return false
        }
        return true
    }

    private fun moveTo(step: AuthStep) {
        _uiState.update { it.copy(step = step, code = "", fieldErrors = emptyMap(), errorRes = null, noticeRes = null) }
        requestFirstField()
    }

    private fun requestFirstField() {
        val state = _uiState.value
        val fields = when (state.step) {
            AuthStep.LOGIN, AuthStep.REGISTER_CREDENTIALS -> listOf(AuthField.EMAIL to state.email, AuthField.PASSWORD to state.password, AuthField.CONFIRM_PASSWORD to state.confirmPassword)
            AuthStep.REGISTER_PROFILE -> listOf(AuthField.USERNAME to state.username, AuthField.NICKNAME to state.nickname)
            AuthStep.REGISTER_INVITATION -> listOf(AuthField.INVITATION to state.invitation)
            AuthStep.FORGOT_EMAIL -> listOf(AuthField.EMAIL to state.email)
            AuthStep.RESET_PASSWORD -> listOf(AuthField.PASSWORD to state.password, AuthField.CONFIRM_PASSWORD to state.confirmPassword, AuthField.CODE to state.code)
            AuthStep.LOGIN_EMAIL, AuthStep.LOGIN_TOTP, AuthStep.REGISTER_VERIFY -> listOf(AuthField.CODE to state.code)
            else -> emptyList()
        }
        val field = fields.firstOrNull { it.second.isEmpty() }?.first ?: return
        _uiState.update { it.copy(focusField = field, focusRequestId = it.focusRequestId + 1) }
    }

    private fun fieldFail(field: AuthField, message: Int) {
        _uiState.update {
            it.copy(fieldErrors = it.fieldErrors + (field to message), emailInvalid = field == AuthField.EMAIL,
                focusField = field, focusRequestId = it.focusRequestId + 1,
                errorRes = null, submission = AuthSubmission.FAILED)
        }
    }

    private fun rememberPassword() {
        if (credentialsEnabled && passwordNeedsSave) {
            pendingPassword = PasswordDraft(_uiState.value.email.trim(), _uiState.value.password)
        }
    }

    private suspend fun complete(passwordReset: Boolean = false) {
        currentCoroutineContext().ensureActive()
        val draft = pendingPassword
        pendingPassword = null
        if (draft != null) {
            _uiState.update {
                it.copy(password = "", confirmPassword = "", code = "", fieldErrors = emptyMap(),
                    submission = AuthSubmission.FINISHING,
                    credentialOperation = AuthCredentialOperation.Save(++credentialSequence, draft, passwordReset))
            }
        } else finishSuccess(passwordReset)
    }

    private fun finishSuccess(passwordReset: Boolean, saveFailed: Boolean = false) {
        val email = _uiState.value.email
        releaseCredentials()
        _uiState.value = if (passwordReset) {
            AuthUiState(email = email, noticeRes = if (saveFailed) R.string.auth_password_update_failed else R.string.auth_password_reset_success)
        } else {
            AuthUiState(email = email, submission = AuthSubmission.SUCCEEDED,
                noticeRes = if (saveFailed) R.string.auth_password_save_failed else null)
        }
    }

    private fun releaseCredentials() {
        pendingPassword = null
        credentialClaim = null
        passwordNeedsSave = true
    }

    private fun fail(message: Int) {
        _uiState.update { it.copy(errorRes = message, submission = AuthSubmission.FAILED) }
    }

    private fun perform(block: suspend () -> Unit) {
        val generation = ++requestGeneration
        _uiState.update { it.copy(submission = AuthSubmission.SUBMITTING, errorRes = null, noticeRes = null) }
        requestJob = viewModelScope.launch {
            try {
                block()
            } catch (error: ApiException) {
                if (generation != requestGeneration) return@launch
                if (repository.hasPendingAuthentication(flowOwner)) {
                    _uiState.update {
                        it.copy(step = AuthStep.SAVE_SESSION, password = "", confirmPassword = "", code = "")
                    }
                } else if (repository.hasPendingPasswordReset(flowOwner)) {
                    _uiState.update {
                        it.copy(step = AuthStep.SAVE_PASSWORD_RESET, password = "", confirmPassword = "", code = "")
                    }
                }
                if (!repository.hasPendingAuthentication(flowOwner) && !repository.hasPendingPasswordReset(flowOwner)) {
                    pendingPassword = null
                }
                fail(
                    if (error.failure == ApiFailure.REJECTED) {
                        when (_uiState.value.step) {
                            AuthStep.LOGIN, AuthStep.LOGIN_EMAIL, AuthStep.LOGIN_TOTP -> R.string.auth_credentials_rejected
                            AuthStep.REGISTER_VERIFY -> R.string.auth_registration_failed
                            else -> error.failure.messageRes()
                        }
                    } else error.failure.messageRes(),
                )
            } finally {
                if (generation == requestGeneration) {
                    _uiState.update {
                        it.copy(
                            submission = if (it.isSubmitting) AuthSubmission.IDLE else it.submission,
                            resendSeconds = repository.resendSeconds(it.email.trim()),
                        )
                    }
                }
            }
        }
    }

    fun cancel() {
        closed = true
        releaseCredentials()
        cancelRequest()
        repository.discardPendingAuthentication(flowOwner)
        _uiState.value = AuthUiState(step = _uiState.value.step, email = _uiState.value.email)
    }

    private fun cancelRequest() {
        requestGeneration++
        requestJob?.cancel()
        requestJob = null
    }

    override fun onCleared() {
        cancel()
    }

    private companion object {
        const val EMAIL_KEY = "email"
    }
}
