package moe.kirakira.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import moe.kirakira.R
import moe.kirakira.ui.components.form.AccountFlowDefaults
import moe.kirakira.ui.components.form.FilledTextField
import moe.kirakira.ui.components.form.PasswordTextField
import moe.kirakira.ui.components.form.rememberBoundTextField

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
    isActive: Boolean = true,
) {
    val focusManager = LocalFocusManager.current
    val emailError = state.fieldErrors[AuthField.EMAIL]
        ?: R.string.auth_email_invalid.takeIf { state.emailInvalid }
    val passwordError = state.fieldErrors[AuthField.PASSWORD]
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
        FilledTextField(
            state = rememberBoundTextField(state.email, onEmailChange, state.canEdit && isActive),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocusRequester)
                .testTag("auth_email")
                .semantics { contentType = ContentType.EmailAddress + ContentType.Username },
            enabled = state.canEdit && isActive,
            label = { Text(stringResource(R.string.auth_email)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            errorMessage = emailError?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            onKeyboardAction = { passwordFocusRequester.requestFocus() },
        )
        PasswordTextField(
            state = rememberBoundTextField(state.password, onPasswordChange, state.canEdit && isActive),
            passwordVisible = passwordVisible,
            onPasswordVisibilityChange = onPasswordVisibilityChange,
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
            enabled = state.canEdit && isActive,
            label = { Text(stringResource(R.string.auth_password)) },
            errorMessage = passwordError?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = if (state.step == AuthStep.REGISTER_CREDENTIALS) ImeAction.Next else ImeAction.Done,
            ),
            onKeyboardAction = {
                if (state.step == AuthStep.REGISTER_CREDENTIALS) focusManager.moveFocus(FocusDirection.Next)
                else onSubmit()
            },
        )
    }
}
