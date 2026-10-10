package moe.kirakira.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import moe.kirakira.R
import moe.kirakira.ui.components.AccountFlowDefaults
import moe.kirakira.ui.components.AnimatedSlashIcon
import moe.kirakira.ui.components.SlashIconType

@Composable
internal fun LoginForm(
    state: AuthUiState,
    passwordVisible: Boolean,
    emailFocusRequester: FocusRequester,
    passwordFocusRequester: FocusRequester,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocusRequester)
                .testTag("auth_email")
                .semantics { contentType = ContentType.EmailAddress + ContentType.Username },
            enabled = state.canEdit,
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            isError = state.fieldErrors[AuthField.EMAIL] != null || state.emailInvalid,
            supportingText = (state.fieldErrors[AuthField.EMAIL]
                ?: R.string.auth_email_invalid.takeIf { state.emailInvalid })?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .testTag("auth_password")
                .semantics {
                    contentType = if (state.step == AuthStep.REGISTER_CREDENTIALS) {
                        ContentType.NewPassword
                    } else {
                        ContentType.Password
                    }
                },
            enabled = state.canEdit,
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onPasswordVisibilityChange, enabled = state.canEdit) {
                    AnimatedSlashIcon(
                        type = SlashIconType.VISIBILITY,
                        slashed = passwordVisible,
                        description = stringResource(
                            if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                        ),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = if (state.step == AuthStep.REGISTER_CREDENTIALS) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                onDone = { if (state.canSubmit) onSubmit() },
            ),
        )
    }
}
