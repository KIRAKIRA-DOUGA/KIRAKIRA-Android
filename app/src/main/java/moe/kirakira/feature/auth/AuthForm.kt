package moe.kirakira.feature.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import moe.kirakira.R

@Composable
internal fun AuthForm(
    state: AuthUiState,
    passwordVisible: Boolean,
    emailFocusRequester: FocusRequester,
    focusRequesters: Map<AuthField, FocusRequester>,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: () -> Unit,
    onFieldChange: (AuthField, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state.step) {
            AuthStep.LOGIN, AuthStep.REGISTER_CREDENTIALS -> {
                LoginForm(
                    state = state,
                    passwordVisible = passwordVisible,
                    emailFocusRequester = emailFocusRequester,
                    passwordFocusRequester = focusRequesters.getValue(AuthField.PASSWORD),
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onPasswordVisibilityChange = onPasswordVisibilityChange,
                    onSubmit = onSubmit,
                )
                if (state.step == AuthStep.REGISTER_CREDENTIALS) {
                    AuthTextField(
                        value = state.confirmPassword,
                        onValueChange = { onFieldChange(AuthField.CONFIRM_PASSWORD, it) },
                        label = R.string.auth_confirm_password,
                        enabled = state.canEdit,
                        modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.CONFIRM_PASSWORD)),
                        errorRes = state.fieldErrors[AuthField.CONFIRM_PASSWORD],
                        keyboardType = KeyboardType.Password,
                        secret = true,
                    )
                    AuthTextField(
                        value = state.passwordHint,
                        onValueChange = { onFieldChange(AuthField.PASSWORD_HINT, it) },
                        label = R.string.auth_password_hint,
                        enabled = state.canEdit,
                        modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.PASSWORD_HINT)),
                        errorRes = state.fieldErrors[AuthField.PASSWORD_HINT],
                        onSubmit = onSubmit,
                    )
                }
            }
            AuthStep.REGISTER_INVITATION -> {
                AuthTextField(
                    value = state.invitation,
                    onValueChange = { onFieldChange(AuthField.INVITATION, it) },
                    label = R.string.auth_invitation,
                    enabled = state.canEdit,
                    modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.INVITATION)),
                    errorRes = state.fieldErrors[AuthField.INVITATION],
                    onSubmit = onSubmit,
                )
            }
            AuthStep.REGISTER_PROFILE -> {
                AuthTextField(
                    value = state.username,
                    onValueChange = { onFieldChange(AuthField.USERNAME, it) },
                    label = R.string.auth_username,
                    enabled = state.canEdit,
                    modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.USERNAME))
                        .semantics { contentType = ContentType.NewUsername },
                    errorRes = state.fieldErrors[AuthField.USERNAME],
                )
                AuthTextField(
                    value = state.nickname,
                    onValueChange = { onFieldChange(AuthField.NICKNAME, it) },
                    label = R.string.auth_nickname,
                    enabled = state.canEdit,
                    modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.NICKNAME)),
                    errorRes = state.fieldErrors[AuthField.NICKNAME],
                    onSubmit = onSubmit,
                )
            }
            AuthStep.FORGOT_EMAIL -> {
                AuthTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = R.string.auth_email,
                    enabled = state.canEdit,
                    modifier = Modifier.focusRequester(emailFocusRequester).semantics {
                        contentType = ContentType.EmailAddress
                    },
                    keyboardType = KeyboardType.Email,
                    errorRes = state.fieldErrors[AuthField.EMAIL],
                    onSubmit = onSubmit,
                )
            }
            AuthStep.LOGIN_EMAIL, AuthStep.LOGIN_TOTP, AuthStep.REGISTER_VERIFY, AuthStep.RESET_PASSWORD -> {
                Text(state.email, style = MaterialTheme.typography.bodyLarge)
                if (state.step == AuthStep.RESET_PASSWORD) {
                    AuthTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = R.string.auth_new_password,
                        enabled = state.canEdit,
                        modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.PASSWORD)),
                        keyboardType = KeyboardType.Password,
                        secret = true,
                    )
                }
                if (state.step == AuthStep.RESET_PASSWORD) {
                    AuthTextField(
                        value = state.confirmPassword,
                        onValueChange = { onFieldChange(AuthField.CONFIRM_PASSWORD, it) },
                        label = R.string.auth_confirm_password,
                        enabled = state.canEdit,
                        modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.CONFIRM_PASSWORD)),
                        errorRes = state.fieldErrors[AuthField.CONFIRM_PASSWORD],
                        keyboardType = KeyboardType.Password,
                        secret = true,
                    )
                }
                AuthTextField(
                    value = state.code,
                    modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.CODE)),
                    errorRes = state.fieldErrors[AuthField.CODE],
                    onValueChange = { onFieldChange(AuthField.CODE, it) },
                    label = if (state.step == AuthStep.LOGIN_TOTP) {
                        R.string.auth_totp_code
                    } else {
                        R.string.auth_verification_code
                    },
                    enabled = state.canEdit,
                    keyboardType = if (state.step == AuthStep.LOGIN_TOTP) KeyboardType.Ascii else KeyboardType.Number,
                    onSubmit = onSubmit,
                )
            }
            AuthStep.TOTP_HELP, AuthStep.SAVE_SESSION, AuthStep.SAVE_PASSWORD_RESET -> Unit
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    @StringRes errorRes: Int? = null,
    onSubmit: (() -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().testTag("auth_field_$label").then(
            if (secret) Modifier.semantics { contentType = ContentType.NewPassword } else Modifier,
        ),
        label = { Text(stringResource(label)) },
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        singleLine = true,
        isError = errorRes != null,
        supportingText = errorRes?.let { { Text(stringResource(it)) } },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(FocusDirection.Next) },
            onDone = { onSubmit?.invoke() },
        ),
    )
}
