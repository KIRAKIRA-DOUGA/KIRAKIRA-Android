package moe.kirakira.ui.components.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.icon.AnimatedSlashIcon
import moe.kirakira.ui.components.icon.ErrorIcon
import moe.kirakira.ui.components.icon.SlashIconType

/** Shared password presentation; the caller owns visibility and resets it with the form lifecycle. */
@Composable
internal fun PasswordTextField(
    state: TextFieldState,
    passwordVisible: Boolean,
    onPasswordVisibilityChange: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
    onKeyboardAction: KeyboardActionHandler? = null,
    colors: TextFieldColors = FilledTextFieldDefaults.colors(),
    label: @Composable (() -> Unit)? = null,
) {
    val visible = passwordVisible && enabled
    FilledTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        errorMessage = errorMessage,
        passwordObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        colors = colors,
        label = label,
        trailingIcon = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (errorMessage != null) ErrorIcon()
                IconButton(onClick = onPasswordVisibilityChange, enabled = enabled) {
                    AnimatedSlashIcon(
                        type = SlashIconType.VISIBILITY,
                        slashed = visible,
                        description = stringResource(
                            if (visible) R.string.auth_hide_password else R.string.auth_show_password,
                        ),
                    )
                }
            }
        },
    )
}
