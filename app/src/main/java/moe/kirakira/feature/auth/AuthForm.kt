package moe.kirakira.feature.auth

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.AccountFlowDefaults
import moe.kirakira.ui.components.AccountFlowFieldErrorIcon
import moe.kirakira.ui.components.AccountFlowSection
import moe.kirakira.ui.components.OtpInputField
import moe.kirakira.ui.components.OtpInputType
import moe.kirakira.ui.components.accountFlowCodeTextStyle
import moe.kirakira.ui.components.accountFlowSupportingError

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
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonContentPadding = ButtonDefaults.TextButtonContentPadding
    val buttonEndPadding = buttonContentPadding.calculateEndPadding(LocalLayoutDirection.current)
    val buttonStartPadding = buttonContentPadding.calculateStartPadding(LocalLayoutDirection.current)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
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
                var recoveryInput by remember { mutableStateOf(false) }
                val useRecoveryInput = state.step == AuthStep.LOGIN_TOTP && (recoveryInput || state.code.length > 6)
                val focusManager = LocalFocusManager.current
                Column(verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.SectionSpacing)) {
                    if (state.step == AuthStep.RESET_PASSWORD) {
                        Column(verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
                            AuthTextField(
                                value = state.password,
                                onValueChange = onPasswordChange,
                                label = R.string.auth_new_password,
                                enabled = state.canEdit,
                                modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.PASSWORD)),
                                errorRes = state.fieldErrors[AuthField.PASSWORD],
                                keyboardType = KeyboardType.Password,
                                secret = true,
                            )
                            AuthTextField(
                                value = state.confirmPassword,
                                onValueChange = { onFieldChange(AuthField.CONFIRM_PASSWORD, it) },
                                label = R.string.auth_confirm_password,
                                enabled = state.canEdit,
                                modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.CONFIRM_PASSWORD)),
                                errorRes = state.fieldErrors[AuthField.CONFIRM_PASSWORD],
                                keyboardType = KeyboardType.Password,
                                secret = true,
                                onNext = { focusRequesters.getValue(AuthField.CODE).requestFocus() },
                            )
                        }
                    }
                    AccountFlowSection(
                        title = null,
                        supportingContent = state.email.takeIf { it.isNotBlank() }?.let { email ->
                            {
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actionContent = if (state.step != AuthStep.LOGIN_TOTP) {
                            {
                                TextButton(
                                    onClick = onResend,
                                    enabled = state.canEdit && state.resendSeconds == 0,
                                    modifier = Modifier
                                        .offset(x = buttonEndPadding)
                                        .defaultMinSize(minWidth = 48.dp),
                                    contentPadding = buttonContentPadding,
                                ) {
                                    Text(
                                        if (state.resendSeconds > 0) {
                                            stringResource(R.string.auth_resend_countdown, state.resendSeconds)
                                        } else {
                                            stringResource(R.string.auth_resend)
                                        },
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    ) {
                        if (useRecoveryInput) {
                            AuthTextField(
                                value = state.code,
                                onValueChange = { onFieldChange(AuthField.CODE, it) },
                                label = R.string.security_recovery_code,
                                enabled = state.canEdit,
                                modifier = Modifier.focusRequester(focusRequesters.getValue(AuthField.CODE)),
                                textStyle = accountFlowCodeTextStyle(),
                                keyboardType = KeyboardType.Ascii,
                                errorRes = state.fieldErrors[AuthField.CODE],
                                onSubmit = onSubmit,
                            )
                        } else {
                            val codeLabel = if (state.step == AuthStep.LOGIN_TOTP) {
                                R.string.auth_totp_code
                            } else {
                                R.string.auth_verification_code
                            }
                            OtpInputField(
                                value = state.code,
                                onValueChange = { onFieldChange(AuthField.CODE, it) },
                                label = stringResource(codeLabel),
                                enabled = state.canEdit,
                                modifier = Modifier
                                    .focusRequester(focusRequesters.getValue(AuthField.CODE))
                                    .testTag("auth_field_$codeLabel"),
                                error = state.fieldErrors[AuthField.CODE]?.let { stringResource(it) },
                                inputType = if (state.step == AuthStep.LOGIN_TOTP) {
                                    OtpInputType.ALPHANUMERIC
                                } else {
                                    OtpInputType.NUMERIC
                                },
                                onRecoveryCodePaste = if (state.step == AuthStep.LOGIN_TOTP) {
                                    { code ->
                                        recoveryInput = true
                                        onFieldChange(AuthField.CODE, code)
                                    }
                                } else {
                                    null
                                },
                                onDone = onSubmit,
                            )
                        }
                        if (state.step == AuthStep.LOGIN_TOTP) {
                            TextButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    recoveryInput = !useRecoveryInput
                                    onFieldChange(AuthField.CODE, "")
                                },
                                enabled = state.canEdit,
                                modifier = Modifier.offset(x = -buttonStartPadding).defaultMinSize(minWidth = 48.dp),
                                contentPadding = buttonContentPadding,
                            ) {
                                Text(
                                    stringResource(
                                        if (useRecoveryInput) R.string.auth_use_verification_code
                                        else R.string.auth_use_recovery_code,
                                    ),
                                )
                            }
                        }
                    }
                }
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
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    @StringRes errorRes: Int? = null,
    onSubmit: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
            .testTag("auth_field_$label")
            .then(if (secret) Modifier.semantics { contentType = ContentType.NewPassword } else Modifier),
        label = { Text(stringResource(label)) },
        textStyle = textStyle,
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        singleLine = true,
        isError = errorRes != null,
        supportingText = accountFlowSupportingError(errorRes?.let { stringResource(it) }),
        trailingIcon = if (errorRes != null) { { AccountFlowFieldErrorIcon() } } else null,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { if (onNext != null) onNext() else focus.moveFocus(FocusDirection.Next) },
            onDone = { onSubmit?.invoke() },
        ),
    )
}
