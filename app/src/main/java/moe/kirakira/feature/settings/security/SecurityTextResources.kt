package moe.kirakira.feature.settings.security

import androidx.annotation.StringRes
import moe.kirakira.R
import moe.kirakira.data.auth.SecondFactor

internal fun SecurityStep.iconRes(): Int = when (this) {
    SecurityStep.EMAIL, SecurityStep.VERIFY_EMAIL, SecurityStep.ENABLE_EMAIL, SecurityStep.DISABLE_EMAIL ->
        R.drawable.ic_symbol_mail
    SecurityStep.PASSWORD, SecurityStep.VERIFY_PASSWORD -> R.drawable.ic_symbol_password
    SecurityStep.SECRETS -> R.drawable.ic_symbol_check
    SecurityStep.OVERVIEW, SecurityStep.TWO_FACTOR, SecurityStep.TOTP_CONFIRM,
    SecurityStep.DISABLE_TOTP, SecurityStep.CHECK_FACTOR -> R.drawable.ic_symbol_lock
}

internal fun SecondFactor.label(): Int = when (this) {
    SecondFactor.NONE -> R.string.security_not_enabled
    SecondFactor.EMAIL -> R.string.security_email_authenticator
    SecondFactor.TOTP -> R.string.security_totp_authenticator
}

internal fun SecurityStep.title(): Int = when (this) {
    SecurityStep.OVERVIEW -> R.string.settings_security
    SecurityStep.EMAIL -> R.string.security_change_email
    SecurityStep.VERIFY_EMAIL -> R.string.security_verify_email_title
    SecurityStep.PASSWORD -> R.string.security_change_password
    SecurityStep.VERIFY_PASSWORD -> R.string.security_verify_identity
    SecurityStep.TWO_FACTOR -> R.string.security_two_factor_section
    SecurityStep.ENABLE_EMAIL -> R.string.security_enable_email_title
    SecurityStep.DISABLE_EMAIL -> R.string.security_disable_email_title
    SecurityStep.TOTP_CONFIRM -> R.string.security_totp_setup_title
    SecurityStep.DISABLE_TOTP -> R.string.security_disable_totp_title
    SecurityStep.SECRETS -> R.string.security_recovery_title
    SecurityStep.CHECK_FACTOR -> R.string.security_two_factor_section
}

@StringRes
internal fun SecuritySettingsState.descriptionRes(): Int? {
    if (completionPending) return null
    return when (step) {
        SecurityStep.EMAIL -> R.string.security_change_email_description
        SecurityStep.VERIFY_EMAIL -> if (status?.factor == SecondFactor.TOTP) {
            R.string.security_verify_email_totp_description
        } else {
            R.string.security_verify_email_description
        }
        SecurityStep.PASSWORD -> R.string.security_change_password_description
        SecurityStep.VERIFY_PASSWORD -> if (status?.factor == SecondFactor.TOTP) {
            R.string.security_verify_password_totp_description
        } else {
            R.string.security_verify_password_description
        }
        SecurityStep.TWO_FACTOR -> when (status?.factor) {
            SecondFactor.NONE -> R.string.security_select_factor_description
            SecondFactor.EMAIL, SecondFactor.TOTP -> R.string.security_manage_factor_description
            null -> null
        }
        SecurityStep.ENABLE_EMAIL -> R.string.security_enable_email_description
        SecurityStep.DISABLE_EMAIL -> R.string.security_disable_email_description
        SecurityStep.TOTP_CONFIRM -> R.string.security_totp_setup_description
        SecurityStep.DISABLE_TOTP -> R.string.security_disable_totp_description
        SecurityStep.SECRETS -> R.string.security_save_codes_description
        SecurityStep.OVERVIEW, SecurityStep.CHECK_FACTOR -> null
    }
}
