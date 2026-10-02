package moe.kirakira.feature.settings.security

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.data.security.SecurityEmailPurpose
import moe.kirakira.data.security.SecurityRepository
import moe.kirakira.data.security.SecurityStatus
import moe.kirakira.data.security.TotpSecrets
import moe.kirakira.data.security.TotpSetup

internal enum class SecurityStep { OVERVIEW, EMAIL, PASSWORD, ENABLE_EMAIL, DISABLE_EMAIL, TOTP_SETUP, DISABLE_TOTP, SECRETS }
internal enum class SecurityField { EMAIL, PASSWORD, NEW_PASSWORD, CONFIRM_PASSWORD, CODE, NEW_EMAIL_CODE }

internal class SecurityForm(val values: Map<SecurityField, String> = emptyMap()) {
    operator fun get(field: SecurityField): String = values[field].orEmpty()
    fun with(field: SecurityField, value: String) = SecurityForm(values + (field to value))
}

internal data class SecuritySettingsState(
    val revision: Long = 0,
    val status: SecurityStatus? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val step: SecurityStep = SecurityStep.OVERVIEW,
    val form: SecurityForm = SecurityForm(),
    val setup: TotpSetup? = null,
    val secrets: TotpSecrets? = null,
    val error: ApiFailure? = null,
    val validation: Int? = null,
    val message: Int? = null,
    val completionPending: Boolean = false,
    val currentCooldown: Int = 0,
    val newCooldown: Int = 0,
    val confirmDiscardCodes: Boolean = false,
    val loginEmail: String? = null,
) {
    val working: Boolean get() = loading || busy
}

internal class SecuritySettingsViewModel(private val repository: SecurityRepository) : ViewModel() {
    val session = repository.session
    private val _state = MutableStateFlow(SecuritySettingsState(revision = session.value.revision))
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var revision = session.value.revision

    init {
        viewModelScope.launch {
            session.map { Triple(it.revision, it.isLoading, it.isBusy) }.distinctUntilChanged().collect { value ->
                if (value.first == repository.publishedRevision) {
                    revision = value.first
                    _state.update { it.copy(revision = revision) }
                } else {
                    request?.cancel()
                    repository.discard()
                    revision = value.first
                    _state.value = SecuritySettingsState(revision = revision)
                    if (ready) refresh()
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                _state.update { it.copy(
                    currentCooldown = repository.resendSeconds(session.value.activeProfile?.email.orEmpty()),
                    newCooldown = repository.resendSeconds(it.form[SecurityField.EMAIL].trim()),
                ) }
                delay(1_000)
            }
        }
    }

    private val ready: Boolean get() = session.value.revision == revision && session.value.activeProfile != null &&
        !session.value.isLoading && !session.value.isBusy

    fun refresh() {
        if (!ready || _state.value.working || _state.value.step != SecurityStep.OVERVIEW) return
        runOperation(loading = true) {
            val status = repository.status(revision)
            _state.update { it.copy(status = status) }
        }
    }

    fun edit(field: SecurityField, value: String) {
        if (!ready || _state.value.working || _state.value.completionPending) return
        _state.update {
            val form = it.form.with(field, value)
            it.copy(form = if (field == SecurityField.EMAIL) form.with(SecurityField.NEW_EMAIL_CODE, "") else form,
                validation = null, error = null)
        }
    }

    fun open(step: SecurityStep) {
        val status = _state.value.status ?: return
        if (!ready || _state.value.working || _state.value.completionPending) return
        if (step == SecurityStep.ENABLE_EMAIL || step == SecurityStep.TOTP_SETUP) {
            if (status.factor != SecondFactor.NONE) return
        }
        if (step == SecurityStep.DISABLE_EMAIL && status.factor != SecondFactor.EMAIL) return
        if (step == SecurityStep.DISABLE_TOTP && status.factor != SecondFactor.TOTP) return
        _state.update { it.copy(step = step, form = SecurityForm(), setup = null, secrets = null,
            validation = null, error = null, message = null) }
    }

    val canNavigateBack: Boolean get() = !_state.value.working && _state.value.step == SecurityStep.OVERVIEW

    fun requestBack(): Boolean {
        val current = _state.value
        if (current.working || current.completionPending) return false
        if (current.step == SecurityStep.SECRETS) {
            _state.update { it.copy(confirmDiscardCodes = true) }
            return false
        }
        if (current.step != SecurityStep.OVERVIEW) { closeFlow(); return false }
        return true
    }

    fun cancelDiscardCodes() { _state.update { it.copy(confirmDiscardCodes = false) } }
    fun finishCodes() { if (!_state.value.working) closeFlow() }
    private fun closeFlow() {
        _state.update { it.copy(step = SecurityStep.OVERVIEW, form = SecurityForm(), secrets = null,
            setup = null, validation = null, error = null, confirmDiscardCodes = false) }
        refresh()
    }
    fun dismissMessage() { _state.update { it.copy(message = null) } }
    fun consumeLogin() { _state.update { it.copy(loginEmail = null) } }
    fun deactivate() {
        request?.cancel()
        _state.update { it.copy(
            form = SecurityForm(), setup = null, secrets = null, loading = false, busy = false,
            step = if (repository.needsCompletion) it.step else SecurityStep.OVERVIEW,
            completionPending = repository.needsCompletion, confirmDiscardCodes = false,
        ) }
    }
    fun copied(success: Boolean) {
        _state.update { it.copy(message = if (success) R.string.security_copied else R.string.security_copy_failed) }
    }

    fun sendCode(language: String, newEmail: Boolean = false) {
        val current = _state.value
        if (!ready || current.working || current.completionPending) return
        val email = current.form[SecurityField.EMAIL].trim().takeIf { newEmail }
        if (newEmail && !validEmail(email.orEmpty())) return
        if (!newEmail && current.status?.factor == SecondFactor.TOTP) return
        val purpose = when (current.step) {
            SecurityStep.EMAIL -> SecurityEmailPurpose.EMAIL
            SecurityStep.PASSWORD -> SecurityEmailPurpose.PASSWORD
            SecurityStep.DISABLE_EMAIL -> SecurityEmailPurpose.DISABLE_EMAIL
            else -> return
        }
        runOperation {
            repository.sendCode(revision, purpose, language, email, current.status?.factor ?: return@runOperation)
            _state.update { it.copy(message = R.string.auth_code_sent) }
        }
    }

    fun submit() {
        val current = _state.value
        if (!ready || current.working) return
        if (current.completionPending) { finishCompletion(); return }
        val form = current.form
        val password = form[SecurityField.PASSWORD]
        val code = form[SecurityField.CODE].trim().uppercase()
        val newPassword = form[SecurityField.NEW_PASSWORD]
        val step = current.step
        when (step) {
            SecurityStep.EMAIL -> {
                if (!validEmail(form[SecurityField.EMAIL].trim())) return
                if (password.isEmpty() || !sixDigits(code) || !sixDigits(form[SecurityField.NEW_EMAIL_CODE].trim())) {
                    validation(R.string.security_fields_invalid); return
                }
            }
            SecurityStep.PASSWORD -> {
                if (password.isEmpty() || newPassword.isEmpty() || newPassword != form[SecurityField.CONFIRM_PASSWORD]) {
                    validation(R.string.auth_password_mismatch); return
                }
                if (password == newPassword) { validation(R.string.security_same_password); return }
                if (!sixDigits(code)) { validation(R.string.security_code_invalid); return }
            }
            SecurityStep.DISABLE_EMAIL, SecurityStep.DISABLE_TOTP -> {
                if (password.isEmpty() || (step == SecurityStep.DISABLE_EMAIL && !sixDigits(code)) ||
                    (step == SecurityStep.DISABLE_TOTP && !code.matches(Regex("[A-Z0-9]{6}")))) {
                    validation(R.string.security_fields_invalid); return
                }
            }
            SecurityStep.TOTP_SETUP -> {
                if (current.setup == null) { beginTotp(); return }
                if (!sixDigits(code)) { validation(R.string.security_code_invalid); return }
            }
            SecurityStep.ENABLE_EMAIL -> Unit
            else -> return
        }
        val email = session.value.activeProfile?.email.orEmpty()
        runOperation {
            when (step) {
                SecurityStep.EMAIL -> {
                    repository.changeEmail(revision, form[SecurityField.EMAIL].trim(), password, code,
                        form[SecurityField.NEW_EMAIL_CODE].trim())
                    completeCredentials(R.string.security_email_changed)
                }
                SecurityStep.PASSWORD -> {
                    repository.changePassword(revision, password, newPassword, code)
                    completeCredentials(R.string.security_password_changed, email)
                }
                SecurityStep.ENABLE_EMAIL -> {
                    repository.enableEmail(revision)
                    completeFactor(SecurityStatus(SecondFactor.EMAIL), R.string.security_email_2fa_enabled)
                }
                SecurityStep.DISABLE_EMAIL, SecurityStep.DISABLE_TOTP -> {
                    repository.disable(revision, current.status?.factor ?: return@runOperation, password, code)
                    completeFactor(SecurityStatus(SecondFactor.NONE), R.string.security_2fa_disabled)
                }
                SecurityStep.TOTP_SETUP -> {
                    val secrets = repository.confirmTotp(revision, current.setup ?: return@runOperation, code)
                    _state.update { it.copy(step = SecurityStep.SECRETS, form = SecurityForm(), setup = null,
                        secrets = secrets, status = SecurityStatus(SecondFactor.TOTP)) }
                }
            }
        }
    }

    private fun beginTotp() = runOperation {
        val setup = repository.beginTotp(revision)
        _state.update { it.copy(setup = setup) }
    }

    private fun finishCompletion() {
        val step = _state.value.step
        val email = session.value.activeProfile?.email.orEmpty()
        runOperation {
            repository.finishCompletion()
            completeCredentials(if (step == SecurityStep.PASSWORD) R.string.security_password_changed
                else R.string.security_email_changed, email.takeIf { step == SecurityStep.PASSWORD })
        }
    }

    private fun completeCredentials(message: Int, loginEmail: String? = null) {
        revision = session.value.revision
        _state.update { it.copy(revision = revision, step = SecurityStep.OVERVIEW, form = SecurityForm(),
            completionPending = false, message = message, loginEmail = loginEmail) }
    }

    private fun completeFactor(status: SecurityStatus, message: Int) {
        _state.update { it.copy(step = SecurityStep.OVERVIEW, status = status, form = SecurityForm(), message = message) }
    }

    private fun validEmail(email: String): Boolean {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { validation(R.string.auth_email_invalid); return false }
        if (email.equals(session.value.activeProfile?.email, ignoreCase = true)) {
            validation(R.string.security_same_email); return false
        }
        return true
    }
    private fun validation(message: Int) { _state.update { it.copy(validation = message) } }

    private fun runOperation(loading: Boolean = false, block: suspend () -> Unit) {
        if (!ready || _state.value.working) return
        _state.update { it.copy(loading = loading, busy = !loading, error = null, validation = null) }
        request = viewModelScope.launch {
            try {
                block()
            } catch (error: ApiException) {
                currentCoroutineContext().ensureActive()
                _state.update {
                    val confirmedWithoutCodes = repository.totpConfirmed && it.step == SecurityStep.TOTP_SETUP
                    it.copy(error = error.failure, completionPending = repository.needsCompletion,
                        form = if (repository.needsCompletion || confirmedWithoutCodes) SecurityForm() else it.form,
                        step = if (confirmedWithoutCodes) SecurityStep.OVERVIEW else it.step,
                        setup = if (confirmedWithoutCodes) null else it.setup,
                        status = if (confirmedWithoutCodes) SecurityStatus(SecondFactor.TOTP) else it.status,
                        message = if (confirmedWithoutCodes) R.string.security_codes_unavailable else it.message)
                }
            } finally {
                if (currentCoroutineContext().isActive) _state.update { it.copy(loading = false, busy = false) }
            }
        }
    }

    override fun onCleared() { repository.discard(); _state.value = SecuritySettingsState() }
}

private fun sixDigits(value: String): Boolean = value.matches(Regex("[0-9]{6}"))
