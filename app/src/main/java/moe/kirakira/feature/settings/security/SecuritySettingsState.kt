package moe.kirakira.feature.settings.security

import kotlinx.serialization.Serializable
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.security.SecurityStatus
import moe.kirakira.data.security.TotpSecrets
import moe.kirakira.data.security.TotpSetup

internal enum class SecurityPage { OVERVIEW, EMAIL, PASSWORD, TWO_FACTOR }

/** Rendering identity only; each business flow owns its current phase and payload. */
@Serializable
internal enum class SecurityStep {
    OVERVIEW, EMAIL, VERIFY_EMAIL, PASSWORD, VERIFY_PASSWORD, TWO_FACTOR, ENABLE_EMAIL, DISABLE_EMAIL, TOTP_CONFIRM,
    DISABLE_TOTP, SECRETS, CHECK_FACTOR,
}

internal enum class SecurityField { EMAIL, PASSWORD, NEW_PASSWORD, CONFIRM_PASSWORD, CODE, NEW_EMAIL_CODE }

internal class SecurityForm(private val values: Map<SecurityField, String> = emptyMap()) {
    operator fun get(field: SecurityField): String = values[field].orEmpty()
    fun with(field: SecurityField, value: String) = SecurityForm(values + (field to value))
    fun only(vararg fields: SecurityField) = SecurityForm(values.filterKeys { it in fields })
    val hasValues: Boolean get() = values.values.any { it.isNotEmpty() }

    companion object {
        val Empty = SecurityForm()
    }
}

/** Drafts and verification credentials are retained only by this navigation entry's ViewModel. */
internal sealed interface CredentialFlow {
    data object Idle : CredentialFlow
    class EditEmail(val form: SecurityForm = SecurityForm.Empty, val confirmDiscard: Boolean = false) : CredentialFlow
    class VerifyEmail(val form: SecurityForm) : CredentialFlow
    class EditPassword(val form: SecurityForm = SecurityForm.Empty, val confirmDiscard: Boolean = false) : CredentialFlow
    class VerifyPassword(val form: SecurityForm) : CredentialFlow
    data object SyncEmail : CredentialFlow
    data object SyncPassword : CredentialFlow
}

internal val CredentialFlow.form: SecurityForm
    get() = when (this) {
        is CredentialFlow.EditEmail -> form
        is CredentialFlow.VerifyEmail -> form
        is CredentialFlow.EditPassword -> form
        is CredentialFlow.VerifyPassword -> form
        CredentialFlow.Idle, CredentialFlow.SyncEmail, CredentialFlow.SyncPassword -> SecurityForm.Empty
    }

internal val CredentialFlow.needsDiscardConfirmation: Boolean
    get() = when (this) {
        is CredentialFlow.EditEmail -> form.hasValues
        is CredentialFlow.EditPassword -> form.hasValues
        CredentialFlow.Idle, is CredentialFlow.VerifyEmail, is CredentialFlow.VerifyPassword,
        CredentialFlow.SyncEmail, CredentialFlow.SyncPassword -> false
    }

internal fun CredentialFlow.forSync(): CredentialFlow = when (this) {
    is CredentialFlow.EditEmail, is CredentialFlow.VerifyEmail, CredentialFlow.SyncEmail -> CredentialFlow.SyncEmail
    is CredentialFlow.EditPassword, is CredentialFlow.VerifyPassword, CredentialFlow.SyncPassword -> CredentialFlow.SyncPassword
    CredentialFlow.Idle -> CredentialFlow.Idle
}

internal sealed interface TotpDraft {
    class Available(val setup: TotpSetup) : TotpDraft
    data object Unavailable : TotpDraft
}

/** Plain payload classes prevent generated toString() methods from exposing credentials or setup materials. */
internal sealed interface TwoFactorFlow {
    class Manage(val draft: TotpDraft? = null, val notice: Int? = null) : TwoFactorFlow
    class EnableEmail(val draft: TotpDraft? = null) : TwoFactorFlow
    class DisableEmail(val form: SecurityForm = SecurityForm.Empty) : TwoFactorFlow
    class DisableTotp(val form: SecurityForm = SecurityForm.Empty) : TwoFactorFlow
    class ConfirmTotp(val setup: TotpSetup, val form: SecurityForm = SecurityForm.Empty) : TwoFactorFlow
    class SaveCodes(val secrets: TotpSecrets, val confirmDiscard: Boolean = false) : TwoFactorFlow
    class Checking(val mutation: TwoFactorMutation) : TwoFactorFlow
}

internal sealed interface TwoFactorMutation {
    class EnableEmail(val draft: TotpDraft?) : TwoFactorMutation
    data object DisableEmail : TwoFactorMutation
    data object DisableTotp : TwoFactorMutation
    data object BeginTotp : TwoFactorMutation
    class ConfirmTotp(val draft: TotpDraft) : TwoFactorMutation
}

internal data class SecuritySettingsState(
    val revision: Long = 0,
    val status: SecurityStatus? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val page: SecurityPage = SecurityPage.OVERVIEW,
    val credentials: CredentialFlow = CredentialFlow.Idle,
    val twoFactor: TwoFactorFlow = TwoFactorFlow.Manage(),
    val error: ApiFailure? = null,
    val validation: Int? = null,
    val fieldErrors: Map<SecurityField, Int> = emptyMap(),
    val validationAttempt: Long = 0,
    val message: Int? = null,
    val currentCooldown: Int = 0,
    val newCooldown: Int = 0,
    val loginEmail: String? = null,
) {
    val working: Boolean get() = loading || busy
    val completionPending: Boolean get() = credentials == CredentialFlow.SyncEmail || credentials == CredentialFlow.SyncPassword
    val canLeaveStep: Boolean get() = !working && !completionPending &&
        !credentials.needsDiscardConfirmation &&
        !(page == SecurityPage.TWO_FACTOR && twoFactor is TwoFactorFlow.SaveCodes)
    val canSubmit: Boolean get() = !working && !confirmDiscardCredentials && when (val flow = credentials) {
        is CredentialFlow.EditEmail -> flow.form[SecurityField.EMAIL].isNotBlank()
        is CredentialFlow.EditPassword -> flow.form[SecurityField.NEW_PASSWORD].isNotEmpty() &&
            flow.form[SecurityField.CONFIRM_PASSWORD].isNotEmpty()
        is CredentialFlow.VerifyEmail -> flow.form[SecurityField.PASSWORD].isNotEmpty() &&
            flow.form[SecurityField.CODE].isNotBlank() && flow.form[SecurityField.NEW_EMAIL_CODE].isNotBlank()
        is CredentialFlow.VerifyPassword -> flow.form[SecurityField.PASSWORD].isNotEmpty() &&
            flow.form[SecurityField.CODE].isNotBlank()
        CredentialFlow.SyncEmail, CredentialFlow.SyncPassword -> true
        CredentialFlow.Idle -> page == SecurityPage.TWO_FACTOR
    }
    val confirmDiscardCredentials: Boolean get() = when (val flow = credentials) {
        is CredentialFlow.EditEmail -> flow.confirmDiscard
        is CredentialFlow.EditPassword -> flow.confirmDiscard
        CredentialFlow.Idle, is CredentialFlow.VerifyEmail, is CredentialFlow.VerifyPassword,
        CredentialFlow.SyncEmail, CredentialFlow.SyncPassword -> false
    }
    val statusPending: Boolean get() = twoFactor is TwoFactorFlow.Checking
    val confirmDiscardCodes: Boolean get() = (twoFactor as? TwoFactorFlow.SaveCodes)?.confirmDiscard == true
    val canRefresh: Boolean get() = page == SecurityPage.OVERVIEW ||
        (page == SecurityPage.TWO_FACTOR && twoFactor is TwoFactorFlow.Manage)

    val form: SecurityForm
        get() = when (page) {
            SecurityPage.EMAIL, SecurityPage.PASSWORD -> credentials.form
            SecurityPage.OVERVIEW -> SecurityForm.Empty
            SecurityPage.TWO_FACTOR -> when (val flow = twoFactor) {
                is TwoFactorFlow.DisableEmail -> flow.form
                is TwoFactorFlow.DisableTotp -> flow.form
                is TwoFactorFlow.ConfirmTotp -> flow.form
                is TwoFactorFlow.Manage, is TwoFactorFlow.EnableEmail,
                is TwoFactorFlow.SaveCodes, is TwoFactorFlow.Checking -> SecurityForm.Empty
            }
        }

    val step: SecurityStep
        get() = when (page) {
            SecurityPage.OVERVIEW -> SecurityStep.OVERVIEW
            SecurityPage.EMAIL -> when (credentials) {
                is CredentialFlow.VerifyEmail, CredentialFlow.SyncEmail -> SecurityStep.VERIFY_EMAIL
                else -> SecurityStep.EMAIL
            }
            SecurityPage.PASSWORD -> when (credentials) {
                is CredentialFlow.VerifyPassword, CredentialFlow.SyncPassword -> SecurityStep.VERIFY_PASSWORD
                else -> SecurityStep.PASSWORD
            }
            SecurityPage.TWO_FACTOR -> when (twoFactor) {
                is TwoFactorFlow.Manage -> SecurityStep.TWO_FACTOR
                is TwoFactorFlow.EnableEmail -> SecurityStep.ENABLE_EMAIL
                is TwoFactorFlow.DisableEmail -> SecurityStep.DISABLE_EMAIL
                is TwoFactorFlow.DisableTotp -> SecurityStep.DISABLE_TOTP
                is TwoFactorFlow.ConfirmTotp -> SecurityStep.TOTP_CONFIRM
                is TwoFactorFlow.SaveCodes -> SecurityStep.SECRETS
                is TwoFactorFlow.Checking -> SecurityStep.CHECK_FACTOR
            }
        }
}

internal fun TwoFactorMutation.withoutSecrets(): TwoFactorMutation = when (this) {
    is TwoFactorMutation.EnableEmail -> TwoFactorMutation.EnableEmail(draft?.let { TotpDraft.Unavailable })
    is TwoFactorMutation.ConfirmTotp -> TwoFactorMutation.ConfirmTotp(TotpDraft.Unavailable)
    TwoFactorMutation.DisableEmail, TwoFactorMutation.DisableTotp, TwoFactorMutation.BeginTotp -> this
}
