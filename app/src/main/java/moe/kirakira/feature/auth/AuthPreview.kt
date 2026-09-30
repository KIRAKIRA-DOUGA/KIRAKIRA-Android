package moe.kirakira.feature.auth

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
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
    FILLED,
    EMAIL_ERROR,
    SUBMITTING,
    FAILED,
}

internal class AuthPreviewStateProvider : PreviewParameterProvider<AuthPreviewState> {
    override val values = AuthPreviewState.entries.asSequence()
}

@Preview(name = "Auth · Form states", showBackground = true)
@Composable
private fun AuthFormStatePreview(@PreviewParameter(AuthPreviewStateProvider::class) preview: AuthPreviewState) {
    AuthPreviewContent(
        AuthUiState(
            email = stringResource(
                if (preview == AuthPreviewState.EMAIL_ERROR) {
                    R.string.demo_auth_invalid_email
                } else {
                    R.string.demo_auth_email
                },
            ),
            password = stringResource(R.string.demo_auth_password),
            emailInvalid = preview == AuthPreviewState.EMAIL_ERROR,
            submission = when (preview) {
                AuthPreviewState.SUBMITTING -> AuthSubmission.SUBMITTING
                AuthPreviewState.FAILED -> AuthSubmission.FAILED
                else -> AuthSubmission.IDLE
            },
        ),
    )
}

@Preview(name = "Auth · Dynamic color", showBackground = true)
@Composable
private fun AuthDynamicColorPreview() {
    AuthPreviewContent(AuthUiState(), dynamicColor = true)
}

@Composable
private fun AuthPreviewContent(state: AuthUiState, dynamicColor: Boolean = false) {
    KIRAKIRATheme(dynamicColor = dynamicColor) {
        AuthScreen(
            state = state,
            passwordVisible = false,
            snackbarHostState = remember { SnackbarHostState() },
            emailFocusRequester = remember { FocusRequester() },
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordVisibilityChange = {},
            onSubmit = {},
            onRegister = {},
            onClose = {},
            onBack = {},
        )
    }
}

internal class AuthStepPreviewProvider : PreviewParameterProvider<AuthStep> {
    override val values = AuthStep.entries.asSequence()
}

@Preview(name = "Authentication steps", showBackground = true)
@Composable
private fun AuthStepPreview(@PreviewParameter(AuthStepPreviewProvider::class) step: AuthStep) {
    AuthPreviewContent(AuthUiState(step = step, email = stringResource(R.string.demo_auth_email)))
}
