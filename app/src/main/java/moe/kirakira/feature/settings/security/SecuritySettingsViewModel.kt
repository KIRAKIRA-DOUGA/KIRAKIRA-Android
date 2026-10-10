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
import moe.kirakira.data.security.TotpConfirmation

internal class SecuritySettingsViewModel(private val repository: SecurityRepository) : ViewModel() {
    val session = repository.session
    private val _state = MutableStateFlow(SecuritySettingsState(revision = session.value.revision))
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var factorMutation: TwoFactorMutation? = null
    private var revision = session.value.revision

    init {
        viewModelScope.launch {
            session.map { Triple(it.revision, it.isLoading, it.isBusy) }.distinctUntilChanged().collect { value ->
                if (value.first == repository.publishedRevision) {
                    revision = value.first
                    _state.update { it.copy(revision = revision) }
                } else {
                    request?.cancel()
                    factorMutation = null
                    repository.discard()
                    revision = value.first
                    _state.value = SecuritySettingsState(revision = revision)
                    if (ready) refresh()
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                val currentCooldown = repository.resendSeconds(session.value.activeProfile?.email.orEmpty())
                val newCooldown = repository.resendSeconds(_state.value.form[SecurityField.EMAIL].trim())
                _state.update { it.copy(currentCooldown = currentCooldown, newCooldown = newCooldown) }
                delay(1_000)
            }
        }
    }

    private val ready: Boolean get() = session.value.revision == revision && session.value.activeProfile != null &&
        !session.value.isLoading && !session.value.isBusy

    fun refresh() {
        val current = _state.value
        if (!ready || current.working) return
        val checking = current.twoFactor as? TwoFactorFlow.Checking
        if (checking != null) {
            checkFactorStatus(checking.mutation)
            return
        }
        if (!current.canRefresh) return
        runOperation(loading = true) {
            val status = repository.status(revision)
            _state.update { previous ->
                val manage = previous.twoFactor as? TwoFactorFlow.Manage
                previous.copy(
                    status = status,
                    twoFactor = TwoFactorFlow.Manage(
                        draft = manage?.draft.takeIf { status.factor == SecondFactor.NONE },
                        notice = manage?.notice.takeIf { status.factor == previous.status?.factor },
                    ),
                )
            }
        }
    }

    fun edit(field: SecurityField, value: String) {
        val current = _state.value
        if (!ready || current.working || current.completionPending || current.statusPending || current.confirmDiscardCredentials) return
        val numericCode = current.page in setOf(SecurityPage.EMAIL, SecurityPage.PASSWORD) &&
            field in setOf(SecurityField.CODE, SecurityField.NEW_EMAIL_CODE)
        val form = current.form.with(field, if (numericCode) value.filter { it in '0'..'9' }.take(6) else value)
        val next = when (current.page) {
            SecurityPage.EMAIL, SecurityPage.PASSWORD -> {
                val flow = when (current.credentials) {
                    is CredentialFlow.EditEmail -> {
                        if (field != SecurityField.EMAIL) return
                        CredentialFlow.EditEmail(form)
                    }
                    is CredentialFlow.EditPassword -> {
                        if (field != SecurityField.NEW_PASSWORD && field != SecurityField.CONFIRM_PASSWORD) return
                        CredentialFlow.EditPassword(form)
                    }
                    is CredentialFlow.VerifyEmail -> {
                        if (field !in setOf(SecurityField.PASSWORD, SecurityField.CODE, SecurityField.NEW_EMAIL_CODE)) return
                        CredentialFlow.VerifyEmail(form)
                    }
                    is CredentialFlow.VerifyPassword -> {
                        if (field != SecurityField.PASSWORD && field != SecurityField.CODE) return
                        CredentialFlow.VerifyPassword(form)
                    }
                    CredentialFlow.Idle, CredentialFlow.SyncEmail, CredentialFlow.SyncPassword -> return
                }
                current.copy(credentials = flow)
            }
            SecurityPage.TWO_FACTOR -> when (val flow = current.twoFactor) {
                is TwoFactorFlow.DisableEmail -> current.copy(twoFactor = TwoFactorFlow.DisableEmail(form))
                is TwoFactorFlow.DisableTotp -> current.copy(twoFactor = TwoFactorFlow.DisableTotp(form))
                is TwoFactorFlow.ConfirmTotp -> current.copy(twoFactor = TwoFactorFlow.ConfirmTotp(flow.setup, form))
                is TwoFactorFlow.Manage, is TwoFactorFlow.EnableEmail,
                is TwoFactorFlow.SaveCodes, is TwoFactorFlow.Checking -> return
            }
            SecurityPage.OVERVIEW -> return
        }
        val clearedErrors = if (field == SecurityField.NEW_PASSWORD) {
            setOf(field, SecurityField.CONFIRM_PASSWORD)
        } else setOf(field)
        _state.value = next.copy(
            validation = null,
            error = null,
            fieldErrors = next.fieldErrors - clearedErrors,
            newCooldown = if (field == SecurityField.EMAIL) repository.resendSeconds(value.trim()) else next.newCooldown,
        )
    }

    fun open(step: SecurityStep) {
        val current = _state.value
        if (!ready || current.working || current.completionPending) return
        if (step == SecurityStep.TWO_FACTOR && current.page == SecurityPage.OVERVIEW) {
            _state.update { it.copy(page = SecurityPage.TWO_FACTOR, validation = null, error = null) }
            return
        }
        val status = current.status ?: return
        val manage = current.twoFactor as? TwoFactorFlow.Manage
        val next = when (step) {
            SecurityStep.EMAIL, SecurityStep.PASSWORD -> {
                if (current.page != SecurityPage.OVERVIEW || current.statusPending) return
                current.copy(
                    page = if (step == SecurityStep.EMAIL) SecurityPage.EMAIL else SecurityPage.PASSWORD,
                    credentials = if (step == SecurityStep.EMAIL) CredentialFlow.EditEmail() else CredentialFlow.EditPassword(),
                )
            }
            SecurityStep.ENABLE_EMAIL -> {
                if (current.page != SecurityPage.TWO_FACTOR || manage == null || status.factor != SecondFactor.NONE) return
                current.copy(twoFactor = TwoFactorFlow.EnableEmail(manage.draft))
            }
            SecurityStep.TOTP_CONFIRM -> {
                if (current.page != SecurityPage.TWO_FACTOR || manage == null || status.factor != SecondFactor.NONE) return
                val flow = when (val draft = manage.draft) {
                    null -> {
                        _state.update { it.copy(message = null) }
                        beginTotp()
                        return
                    }
                    is TotpDraft.Available -> TwoFactorFlow.ConfirmTotp(draft.setup)
                    TotpDraft.Unavailable -> return
                }
                current.copy(twoFactor = flow)
            }
            SecurityStep.DISABLE_EMAIL -> {
                if (current.page != SecurityPage.TWO_FACTOR || manage == null || status.factor != SecondFactor.EMAIL) return
                current.copy(twoFactor = TwoFactorFlow.DisableEmail())
            }
            SecurityStep.DISABLE_TOTP -> {
                if (current.page != SecurityPage.TWO_FACTOR || manage == null || status.factor != SecondFactor.TOTP) return
                current.copy(twoFactor = TwoFactorFlow.DisableTotp())
            }
            SecurityStep.OVERVIEW, SecurityStep.TWO_FACTOR,
            SecurityStep.VERIFY_EMAIL, SecurityStep.VERIFY_PASSWORD,
            SecurityStep.SECRETS, SecurityStep.CHECK_FACTOR -> return
        }
        _state.value = next.copy(validation = null, fieldErrors = emptyMap(), error = null, message = null)
    }

    val canNavigateBack: Boolean get() = _state.value.canLeaveStep &&
        _state.value.page == SecurityPage.OVERVIEW

    fun requestBack(): Boolean {
        val current = _state.value
        if (current.working || current.completionPending) return false
        when (current.page) {
            SecurityPage.OVERVIEW -> return true
            SecurityPage.EMAIL, SecurityPage.PASSWORD -> backCredentials(current.credentials)
            SecurityPage.TWO_FACTOR -> when (val flow = current.twoFactor) {
                is TwoFactorFlow.Manage, is TwoFactorFlow.Checking -> {
                    _state.update { it.copy(page = SecurityPage.OVERVIEW, validation = null, error = null) }
                }
                is TwoFactorFlow.EnableEmail -> manageFactor(flow.draft)
                is TwoFactorFlow.ConfirmTotp -> manageFactor(TotpDraft.Available(flow.setup))
                is TwoFactorFlow.SaveCodes -> {
                    _state.update { it.copy(twoFactor = TwoFactorFlow.SaveCodes(flow.secrets, confirmDiscard = true)) }
                }
                is TwoFactorFlow.DisableEmail, is TwoFactorFlow.DisableTotp -> manageFactor()
            }
        }
        return false
    }

    private fun backCredentials(flow: CredentialFlow) {
        val draft = when (flow) {
            is CredentialFlow.VerifyEmail -> CredentialFlow.EditEmail(flow.form.only(SecurityField.EMAIL))
            is CredentialFlow.VerifyPassword -> CredentialFlow.EditPassword(
                flow.form.only(SecurityField.NEW_PASSWORD, SecurityField.CONFIRM_PASSWORD),
            )
            is CredentialFlow.EditEmail -> {
                if (!flow.needsDiscardConfirmation) { leaveCredentials(); return }
                CredentialFlow.EditEmail(flow.form, confirmDiscard = true)
            }
            is CredentialFlow.EditPassword -> {
                if (!flow.needsDiscardConfirmation) { leaveCredentials(); return }
                CredentialFlow.EditPassword(flow.form, confirmDiscard = true)
            }
            CredentialFlow.Idle -> { leaveCredentials(); return }
            CredentialFlow.SyncEmail, CredentialFlow.SyncPassword -> return
        }
        _state.update { it.copy(credentials = draft, fieldErrors = emptyMap(), validation = null, error = null, message = null) }
    }

    fun cancelDiscardCredentials() {
        val current = _state.value
        val flow = when (val previous = current.credentials) {
            is CredentialFlow.EditEmail -> CredentialFlow.EditEmail(previous.form)
            is CredentialFlow.EditPassword -> CredentialFlow.EditPassword(previous.form)
            CredentialFlow.Idle, is CredentialFlow.VerifyEmail, is CredentialFlow.VerifyPassword,
            CredentialFlow.SyncEmail, CredentialFlow.SyncPassword -> return
        }
        _state.value = current.copy(credentials = flow)
    }

    fun discardCredentials() {
        if (!ready || _state.value.working || !_state.value.confirmDiscardCredentials) return
        leaveCredentials()
    }

    private fun leaveCredentials() {
        _state.update { it.copy(page = SecurityPage.OVERVIEW, credentials = CredentialFlow.Idle,
            fieldErrors = emptyMap(), validation = null, error = null, message = null) }
        refresh()
    }

    fun cancelDiscardCodes() {
        val flow = _state.value.twoFactor as? TwoFactorFlow.SaveCodes ?: return
        _state.update { it.copy(twoFactor = TwoFactorFlow.SaveCodes(flow.secrets)) }
    }

    fun finishCodes() {
        if (!ready || _state.value.working || _state.value.twoFactor !is TwoFactorFlow.SaveCodes) return
        manageFactor()
        refresh()
    }

    private fun manageFactor(draft: TotpDraft? = null, notice: Int? = null) {
        _state.update { it.copy(page = SecurityPage.TWO_FACTOR, credentials = CredentialFlow.Idle,
            twoFactor = TwoFactorFlow.Manage(draft, notice), validation = null, error = null) }
    }

    fun dismissMessage() { _state.update { it.copy(message = null) } }
    fun consumeLogin() { _state.update { it.copy(loginEmail = null) } }

    fun deactivate() {
        val current = _state.value
        val pendingMutation = (current.twoFactor as? TwoFactorFlow.Checking)?.mutation ?: factorMutation
        request?.cancel()
        factorMutation = null
        val flow = if (pendingMutation != null) TwoFactorFlow.Checking(pendingMutation.withoutSecrets()) else {
            val draft = when (val previous = current.twoFactor) {
                is TwoFactorFlow.Manage -> previous.draft
                is TwoFactorFlow.EnableEmail -> previous.draft
                is TwoFactorFlow.ConfirmTotp -> TotpDraft.Unavailable
                is TwoFactorFlow.DisableEmail, is TwoFactorFlow.DisableTotp,
                is TwoFactorFlow.SaveCodes, is TwoFactorFlow.Checking -> null
            }
            TwoFactorFlow.Manage(
                draft = draft?.let { TotpDraft.Unavailable },
                notice = when {
                    current.twoFactor is TwoFactorFlow.SaveCodes -> R.string.security_codes_unavailable
                    draft != null -> R.string.security_setup_unavailable
                    else -> null
                },
            )
        }
        _state.update { it.copy(
            credentials = if (repository.needsCompletion) current.credentials.forSync() else CredentialFlow.Idle,
            twoFactor = flow, loading = false, busy = false,
            status = it.status.takeIf { pendingMutation == null },
            page = if (repository.needsCompletion) it.page else SecurityPage.OVERVIEW,
            fieldErrors = emptyMap(), validation = null, error = null, message = null,
        ) }
    }

    fun copied(success: Boolean) {
        _state.update { it.copy(message = if (success) R.string.security_copied else R.string.security_copy_failed) }
    }

    fun sendCode(language: String, newEmail: Boolean = false) {
        val current = _state.value
        if (!ready || current.working || current.completionPending || current.statusPending) return
        val email = current.form[SecurityField.EMAIL].trim().takeIf { newEmail }
        if (newEmail && (current.credentials !is CredentialFlow.VerifyEmail || emailError(email.orEmpty()) != null)) return
        if (!newEmail && current.status?.factor == SecondFactor.TOTP) return
        val purpose = when (current.step) {
            SecurityStep.VERIFY_EMAIL -> SecurityEmailPurpose.EMAIL
            SecurityStep.VERIFY_PASSWORD -> SecurityEmailPurpose.PASSWORD
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
        if (!ready || current.working || current.confirmDiscardCredentials) return
        if (current.completionPending) { finishCompletion(); return }
        if (current.statusPending) {
            val checking = current.twoFactor as? TwoFactorFlow.Checking ?: return
            checkFactorStatus(checking.mutation)
            return
        }
        if (current.status == null) return
        if (current.page == SecurityPage.TWO_FACTOR) { submitFactor(current.twoFactor); return }
        submitCredentials(current.credentials)
    }

    private fun submitCredentials(flow: CredentialFlow) {
        val form = flow.form
        when (flow) {
            is CredentialFlow.EditEmail -> {
                val email = form[SecurityField.EMAIL].trim()
                val error = emailError(email)
                if (error != null) { fieldErrors(mapOf(SecurityField.EMAIL to error)); return }
                _state.update { it.copy(credentials = CredentialFlow.VerifyEmail(form.with(SecurityField.EMAIL, email)),
                    fieldErrors = emptyMap(), error = null, message = null) }
            }
            is CredentialFlow.EditPassword -> {
                if (!validateNewPassword(form)) return
                _state.update { it.copy(credentials = CredentialFlow.VerifyPassword(form), fieldErrors = emptyMap(),
                    error = null, message = null) }
            }
            is CredentialFlow.VerifyEmail -> {
                if (!validateIdentity(form, newEmail = true)) return
                runOperation {
                    repository.changeEmail(revision, form[SecurityField.EMAIL], form[SecurityField.PASSWORD],
                        form[SecurityField.CODE], form[SecurityField.NEW_EMAIL_CODE])
                    completeCredentials(R.string.security_email_changed)
                }
            }
            is CredentialFlow.VerifyPassword -> {
                if (!validateIdentity(form)) return
                if (form[SecurityField.PASSWORD] == form[SecurityField.NEW_PASSWORD]) {
                    // Keep the new-password error on its editable step and discard identity proofs.
                    _state.update { it.copy(credentials = CredentialFlow.EditPassword(
                        form.only(SecurityField.NEW_PASSWORD, SecurityField.CONFIRM_PASSWORD)),
                        fieldErrors = mapOf(SecurityField.NEW_PASSWORD to R.string.security_same_password),
                        validationAttempt = it.validationAttempt + 1, error = null, message = null) }
                    return
                }
                val email = session.value.activeProfile?.email.orEmpty()
                runOperation {
                    repository.changePassword(revision, form[SecurityField.PASSWORD], form[SecurityField.NEW_PASSWORD],
                        form[SecurityField.CODE])
                    completeCredentials(R.string.security_password_changed, email)
                }
            }
            CredentialFlow.Idle, CredentialFlow.SyncEmail, CredentialFlow.SyncPassword -> Unit
        }
    }

    private fun validateNewPassword(form: SecurityForm): Boolean {
        val errors = buildMap {
            if (form[SecurityField.NEW_PASSWORD].isEmpty()) put(SecurityField.NEW_PASSWORD, R.string.security_password_required)
            if (form[SecurityField.CONFIRM_PASSWORD].isEmpty() ||
                form[SecurityField.CONFIRM_PASSWORD] != form[SecurityField.NEW_PASSWORD]) {
                put(SecurityField.CONFIRM_PASSWORD, R.string.auth_password_mismatch)
            }
        }
        fieldErrors(errors)
        return errors.isEmpty()
    }

    private fun validateIdentity(form: SecurityForm, newEmail: Boolean = false): Boolean {
        val errors = buildMap {
            if (form[SecurityField.PASSWORD].isEmpty()) put(SecurityField.PASSWORD, R.string.security_password_required)
            if (!sixDigits(form[SecurityField.CODE])) put(SecurityField.CODE,
                if (_state.value.status?.factor == SecondFactor.TOTP) R.string.security_totp_code_invalid else R.string.auth_code_invalid)
            if (newEmail && !sixDigits(form[SecurityField.NEW_EMAIL_CODE])) {
                put(SecurityField.NEW_EMAIL_CODE, R.string.auth_code_invalid)
            }
        }
        fieldErrors(errors)
        return errors.isEmpty()
    }

    private fun fieldErrors(errors: Map<SecurityField, Int>) {
        _state.update { it.copy(fieldErrors = errors, validation = null, error = null,
            validationAttempt = if (errors.isNotEmpty()) it.validationAttempt + 1 else it.validationAttempt) }
    }

    private fun submitFactor(flow: TwoFactorFlow) {
        when (flow) {
            is TwoFactorFlow.EnableEmail -> runOperation(mutation = TwoFactorMutation.EnableEmail(flow.draft)) {
                repository.enableEmail(revision)
                completeFactor(SecurityStatus(SecondFactor.EMAIL), R.string.security_email_2fa_enabled)
            }
            is TwoFactorFlow.DisableEmail -> disableFactor(SecondFactor.EMAIL, flow.form, TwoFactorMutation.DisableEmail)
            is TwoFactorFlow.DisableTotp -> disableFactor(SecondFactor.TOTP, flow.form, TwoFactorMutation.DisableTotp)
            is TwoFactorFlow.ConfirmTotp -> {
                val code = flow.form[SecurityField.CODE].trim()
                if (!sixDigits(code)) { validation(R.string.security_code_invalid); return }
                runOperation(mutation = TwoFactorMutation.ConfirmTotp(TotpDraft.Available(flow.setup))) {
                    when (val result = repository.confirmTotp(revision, flow.setup, code)) {
                        is TotpConfirmation.CodesAvailable -> _state.update {
                            it.copy(twoFactor = TwoFactorFlow.SaveCodes(result.secrets), status = SecurityStatus(SecondFactor.TOTP))
                        }
                        TotpConfirmation.CodesUnavailable -> completeFactor(
                            SecurityStatus(SecondFactor.TOTP), notice = R.string.security_codes_unavailable,
                        )
                    }
                }
            }
            is TwoFactorFlow.Manage, is TwoFactorFlow.SaveCodes, is TwoFactorFlow.Checking -> Unit
        }
    }

    private fun beginTotp() = runOperation(mutation = TwoFactorMutation.BeginTotp) {
        val setup = repository.beginTotp(revision)
        _state.update { it.copy(twoFactor = TwoFactorFlow.ConfirmTotp(setup)) }
    }

    private fun disableFactor(factor: SecondFactor, form: SecurityForm, mutation: TwoFactorMutation) {
        val password = form[SecurityField.PASSWORD]
        val code = form[SecurityField.CODE].trim().uppercase()
        val validCode = if (factor == SecondFactor.EMAIL) sixDigits(code) else code.matches(Regex("[A-Z0-9]{6}"))
        if (password.isEmpty() || !validCode) { validation(R.string.security_fields_invalid); return }
        runOperation(mutation = mutation) {
            repository.disable(revision, factor, password, code)
            completeFactor(SecurityStatus(SecondFactor.NONE), R.string.security_2fa_disabled)
        }
    }

    private fun checkFactorStatus(mutation: TwoFactorMutation) = runOperation {
        val status = repository.status(revision)
        when (mutation) {
            is TwoFactorMutation.EnableEmail -> completeFactor(
                status, R.string.security_email_2fa_enabled.takeIf { status.factor == SecondFactor.EMAIL },
                notice = R.string.security_setup_unavailable.takeIf {
                    status.factor == SecondFactor.NONE && mutation.draft == TotpDraft.Unavailable
                },
                draft = mutation.draft.takeIf { status.factor == SecondFactor.NONE },
            )
            TwoFactorMutation.DisableEmail, TwoFactorMutation.DisableTotp -> completeFactor(
                status, R.string.security_2fa_disabled.takeIf { status.factor == SecondFactor.NONE },
            )
            TwoFactorMutation.BeginTotp -> completeFactor(
                status,
                notice = R.string.security_setup_unavailable.takeIf { status.factor == SecondFactor.NONE },
                draft = TotpDraft.Unavailable.takeIf { status.factor == SecondFactor.NONE },
            )
            is TwoFactorMutation.ConfirmTotp -> {
                val draft = mutation.draft
                if (status.factor == SecondFactor.NONE && draft is TotpDraft.Available) {
                    _state.update { it.copy(status = status, page = SecurityPage.TWO_FACTOR,
                        twoFactor = TwoFactorFlow.ConfirmTotp(draft.setup)) }
                } else {
                    completeFactor(
                        status,
                        notice = if (status.factor == SecondFactor.TOTP) R.string.security_codes_unavailable
                            else R.string.security_setup_unavailable.takeIf { status.factor == SecondFactor.NONE },
                        draft = TotpDraft.Unavailable.takeIf { status.factor == SecondFactor.NONE },
                    )
                }
            }
        }
    }

    private fun finishCompletion() {
        val page = _state.value.page
        val email = session.value.activeProfile?.email.orEmpty()
        runOperation {
            repository.finishCompletion()
            completeCredentials(if (page == SecurityPage.PASSWORD) R.string.security_password_changed
                else R.string.security_email_changed, email.takeIf { page == SecurityPage.PASSWORD })
        }
    }

    private fun completeCredentials(message: Int, loginEmail: String? = null) {
        revision = session.value.revision
        _state.update { it.copy(revision = revision, page = SecurityPage.OVERVIEW, credentials = CredentialFlow.Idle,
            fieldErrors = emptyMap(), validation = null, message = message, loginEmail = loginEmail) }
    }

    private fun completeFactor(status: SecurityStatus, message: Int? = null, notice: Int? = null, draft: TotpDraft? = null) {
        _state.update { it.copy(page = SecurityPage.TWO_FACTOR, status = status,
            twoFactor = TwoFactorFlow.Manage(draft, notice), credentials = CredentialFlow.Idle,
            fieldErrors = emptyMap(), message = message) }
    }

    private fun emailError(email: String): Int? = when {
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> R.string.auth_email_invalid
        email.equals(session.value.activeProfile?.email, ignoreCase = true) -> R.string.security_same_email
        else -> null
    }

    private fun validation(message: Int) { _state.update { it.copy(validation = message) } }

    private fun runOperation(
        loading: Boolean = false,
        mutation: TwoFactorMutation? = null,
        block: suspend () -> Unit,
    ) {
        if (!ready || _state.value.working) return
        factorMutation = mutation
        _state.update { it.copy(loading = loading, busy = !loading, error = null, validation = null, fieldErrors = emptyMap()) }
        request = viewModelScope.launch {
            try {
                block()
            } catch (error: ApiException) {
                currentCoroutineContext().ensureActive()
                _state.update {
                    if (mutation != null && error.failure.isUncertainMutation()) {
                        it.copy(status = null, twoFactor = TwoFactorFlow.Checking(mutation), error = error.failure)
                    } else {
                        it.copy(error = error.failure,
                            credentials = if (repository.needsCompletion) it.credentials.forSync() else it.credentials)
                    }
                }
            } finally {
                if (currentCoroutineContext().isActive) {
                    factorMutation = null
                    _state.update { it.copy(loading = false, busy = false) }
                }
            }
        }
    }

    override fun onCleared() {
        factorMutation = null
        repository.discard()
        _state.value = SecuritySettingsState()
    }
}

private fun ApiFailure.isUncertainMutation(): Boolean = when (this) {
    ApiFailure.NETWORK, ApiFailure.TIMEOUT, ApiFailure.SERVER, ApiFailure.INVALID_RESPONSE -> true
    ApiFailure.REJECTED, ApiFailure.SESSION_EXPIRED, ApiFailure.RATE_LIMITED, ApiFailure.DAILY_LIMIT, ApiFailure.STORAGE -> false
}

private fun sixDigits(value: String): Boolean = value.matches(Regex("[0-9]{6}"))
