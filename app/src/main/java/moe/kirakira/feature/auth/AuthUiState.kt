package moe.kirakira.feature.auth

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable

@Serializable
internal enum class AuthStep {
    LOGIN, LOGIN_EMAIL, LOGIN_TOTP, REGISTER_PROFILE, REGISTER_CREDENTIALS, REGISTER_INVITATION, REGISTER_VERIFY,
    FORGOT_EMAIL, RESET_PASSWORD, TOTP_HELP, SAVE_SESSION, SAVE_PASSWORD_RESET,
}

internal enum class AuthField { EMAIL, PASSWORD, USERNAME, NICKNAME, INVITATION, PASSWORD_HINT, CONFIRM_PASSWORD, CODE }
internal enum class AuthSubmission { IDLE, SUBMITTING, FAILED, FINISHING, SUCCEEDED }

internal data class AuthUiState(
    val step: AuthStep = AuthStep.LOGIN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val username: String = "",
    val nickname: String = "",
    val invitation: String = "",
    val passwordHint: String = "",
    val code: String = "",
    val resendSeconds: Int = 0,
    val emailInvalid: Boolean = false,
    val fieldErrors: Map<AuthField, Int> = emptyMap(),
    val focusField: AuthField? = null,
    val focusRequestId: Long = 0,
    val credentialOperation: AuthCredentialOperation? = null,
    val submission: AuthSubmission = AuthSubmission.IDLE,
    @param:StringRes val errorRes: Int? = null,
    @param:StringRes val noticeRes: Int? = null,
) {
    override fun toString(): String = "AuthUiState(step=$step, submission=$submission)"

    val isSubmitting: Boolean get() = submission == AuthSubmission.SUBMITTING
    val canEdit: Boolean get() = !isSubmitting && submission != AuthSubmission.SUCCEEDED &&
        submission != AuthSubmission.FINISHING && credentialOperation !is AuthCredentialOperation.Save
    val canSubmit: Boolean get() = canEdit && when (step) {
        AuthStep.LOGIN -> email.isNotBlank() && password.isNotEmpty()
        AuthStep.REGISTER_CREDENTIALS -> email.isNotBlank() && password.isNotEmpty() && confirmPassword.isNotEmpty()
        AuthStep.LOGIN_EMAIL, AuthStep.LOGIN_TOTP, AuthStep.REGISTER_VERIFY -> code.isNotBlank()
        AuthStep.REGISTER_PROFILE -> username.isNotBlank() && nickname.isNotBlank()
        AuthStep.REGISTER_INVITATION -> invitation.isNotBlank()
        AuthStep.RESET_PASSWORD -> code.isNotBlank() && password.isNotEmpty() && confirmPassword.isNotEmpty()
        AuthStep.FORGOT_EMAIL -> email.isNotBlank()
        AuthStep.SAVE_SESSION, AuthStep.SAVE_PASSWORD_RESET -> true
        AuthStep.TOTP_HELP -> false
    }
}
