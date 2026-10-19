package moe.kirakira.feature.auth

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

@Preview(name = "Auth · English", locale = "en", showBackground = true)
@Preview(name = "认证 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Auth · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Auth · Fixed AppBar", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "Auth · Narrow, large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "Auth · Wide", widthDp = 840, heightDp = 600)
@Preview(name = "Auth · Short landscape", widthDp = 740, heightDp = 360)
@Composable
private fun AuthPreview() {
    AuthPreviewContent(AuthUiState())
}

internal enum class AuthPreviewState {
    EMAIL_ERROR,
    PASSWORD_ERROR,
    PASSWORD_VISIBLE_ERROR,
    MULTIPLE_ERRORS,
    SUBMITTING,
    FAILED,
}

internal class AuthPreviewStateProvider : PreviewParameterProvider<AuthPreviewState> {
    override val values = AuthPreviewState.entries.asSequence()
}

@Preview(name = "Auth · Form states", locale = "en", showBackground = true)
@Preview(name = "认证 · 表单状态、大字体", locale = "zh", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "Auth · Form states, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AuthFormStatePreview(@PreviewParameter(AuthPreviewStateProvider::class) preview: AuthPreviewState) {
    AuthPreviewContent(
        AuthUiState(
            step = if (preview == AuthPreviewState.MULTIPLE_ERRORS) AuthStep.REGISTER_CREDENTIALS else AuthStep.LOGIN,
            emailInvalid = preview == AuthPreviewState.EMAIL_ERROR,
            fieldErrors = when (preview) {
                AuthPreviewState.PASSWORD_ERROR, AuthPreviewState.PASSWORD_VISIBLE_ERROR -> {
                    mapOf(AuthField.PASSWORD to R.string.security_password_required)
                }
                AuthPreviewState.MULTIPLE_ERRORS -> mapOf(
                    AuthField.EMAIL to R.string.auth_email_invalid,
                    AuthField.PASSWORD to R.string.security_password_required,
                    AuthField.CONFIRM_PASSWORD to R.string.auth_password_mismatch,
                )
                else -> emptyMap()
            },
            submission = when (preview) {
                AuthPreviewState.SUBMITTING -> AuthSubmission.SUBMITTING
                AuthPreviewState.FAILED -> AuthSubmission.FAILED
                else -> AuthSubmission.IDLE
            },
        ),
        passwordVisible = preview == AuthPreviewState.PASSWORD_VISIBLE_ERROR,
    )
}

@Preview(name = "Auth · Wallpaper accent", showBackground = true)
@Composable
private fun AuthWallpaperAccentPreview() {
    AuthPreviewContent(AuthUiState(), dynamicColor = true)
}

@Composable
private fun AuthPreviewContent(
    state: AuthUiState,
    dynamicColor: Boolean = false,
    passwordVisible: Boolean = false,
) {
    KIRAKIRATheme(dynamicColor = dynamicColor) {
        AuthScreen(
            state = state,
            passwordVisible = passwordVisible,
            snackbarHostState = remember { SnackbarHostState() },
            emailFocusRequester = remember { FocusRequester() },
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordVisibilityChange = {},
            onSubmit = {},
            onRegister = {},
            onBack = {},
        )
    }
}

internal class AuthStepPreviewProvider : PreviewParameterProvider<AuthStep> {
    override val values = AuthStep.entries.asSequence()
}

@Preview(name = "Authentication steps", showBackground = true)
@Preview(name = "认证步骤 · 中文", locale = "zh", widthDp = 411, heightDp = 891)
@Preview(name = "Auth steps · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Auth steps · Narrow, large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun AuthStepPreview(@PreviewParameter(AuthStepPreviewProvider::class) step: AuthStep) {
    AuthPreviewContent(AuthUiState(step = step))
}

@Preview(name = "Auth · Verification, long email", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "认证 · 验证码错误与倒计时", locale = "zh", widthDp = 411, heightDp = 891)
@Composable
private fun AuthVerificationPreview() {
    AuthPreviewContent(
        AuthUiState(
            step = AuthStep.RESET_PASSWORD,
            email = "preview.with.a.long.email.address@example.com",
            resendSeconds = 59,
            fieldErrors = mapOf(
                AuthField.PASSWORD to R.string.security_password_required,
                AuthField.CONFIRM_PASSWORD to R.string.auth_password_mismatch,
                AuthField.CODE to R.string.auth_code_invalid,
            ),
        ),
    )
}

internal class AuthFilledCodePreviewProvider : PreviewParameterProvider<AuthUiState> {
    override val values = sequenceOf(
        AuthUiState(step = AuthStep.LOGIN_EMAIL, email = "preview@example.com", code = "123456"),
        AuthUiState(step = AuthStep.LOGIN_TOTP, email = "preview@example.com", code = "A1B2C3"),
        AuthUiState(step = AuthStep.LOGIN_TOTP, email = "preview@example.com", code = "ABCD1234EFGH5678JKLM9012"),
    )

    override fun getDisplayName(index: Int): String = listOf("Email code", "Backup code", "Recovery code")[index]
}

@Preview(name = "Auth · Filled verification code", locale = "en", widthDp = 411, heightDp = 891)
@Preview(name = "认证 · 验证码已填写", locale = "zh", widthDp = 411, heightDp = 891)
@Preview(name = "Auth · Filled code, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 411, heightDp = 891)
@Preview(name = "认证 · 验证码已填写、大字体", locale = "zh", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun AuthFilledCodePreview(@PreviewParameter(AuthFilledCodePreviewProvider::class) state: AuthUiState) {
    AuthPreviewContent(state)
}
