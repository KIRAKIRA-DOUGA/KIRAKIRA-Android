@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.security

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import moe.kirakira.R
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.ui.components.AccountFlowFieldErrorIcon
import moe.kirakira.ui.components.AccountFlowSection
import moe.kirakira.ui.components.AnimatedSlashIcon
import moe.kirakira.ui.components.OtpInputField
import moe.kirakira.ui.components.OtpInputType
import moe.kirakira.ui.components.SlashIconType
import moe.kirakira.ui.components.accountFlowSupportingError

@Composable
internal fun SecurityCurrentEmail(email: String, modifier: Modifier = Modifier) {
    Text(email, modifier = modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun SecurityTextField(
    state: SecuritySettingsState,
    field: SecurityField,
    label: Int,
    onEdit: (SecurityField, String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isActive: Boolean = true,
    onDone: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onNext: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) { visible = false }
    val password = keyboardType == KeyboardType.Password
    val showPassword = visible && isActive
    val enabled = !state.working && !state.completionPending
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val error = state.fieldErrors[field]?.let { stringResource(it) }
    LaunchedEffect(state.validationAttempt, isActive, lifecycle) {
        if (isActive && state.fieldErrors.keys.firstOrNull() == field) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }
    if (field == SecurityField.CODE || field == SecurityField.NEW_EMAIL_CODE) {
        OtpInputField(
            value = state.form[field],
            onValueChange = { onEdit(field, it) },
            label = stringResource(label),
            enabled = isActive && enabled,
            modifier = modifier.focusRequester(focusRequester),
            error = error,
            inputType = if (keyboardType == KeyboardType.Ascii) OtpInputType.ALPHANUMERIC else OtpInputType.NUMERIC,
            onNext = onNext,
            onDone = onDone,
        )
        return
    }
    OutlinedTextField(
        value = state.form[field],
        onValueChange = { onEdit(field, it) },
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
            .focusRequester(focusRequester)
            .then(
                when {
                    password -> Modifier.semantics {
                        contentType = if (field == SecurityField.PASSWORD) ContentType.Password else ContentType.NewPassword
                    }
                    field == SecurityField.EMAIL -> Modifier.semantics { contentType = ContentType.EmailAddress }
                    else -> Modifier
                },
            ),
        label = { Text(stringResource(label)) },
        enabled = enabled,
        isError = error != null,
        supportingText = accountFlowSupportingError(error),
        shape = MaterialTheme.shapes.large,
        singleLine = true,
        visualTransformation = if (password && !showPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                if (isActive && enabled) {
                    if (onNext != null) onNext() else focus.moveFocus(FocusDirection.Next)
                }
            },
            onDone = { if (isActive && enabled) onDone?.invoke() },
        ),
        trailingIcon = if (password) {
            {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (error != null) {
                        AccountFlowFieldErrorIcon()
                    }
                    IconButton(onClick = { visible = !visible }, enabled = isActive && enabled) {
                        AnimatedSlashIcon(
                            type = SlashIconType.VISIBILITY,
                            slashed = showPassword,
                            description = stringResource(
                                if (showPassword) R.string.auth_hide_password else R.string.auth_show_password,
                            ),
                        )
                    }
                }
            }
        } else if (error != null) {
            { AccountFlowFieldErrorIcon() }
        } else {
            null
        },
    )
}

@Composable
internal fun SecurityCodeField(
    state: SecuritySettingsState,
    onEdit: (SecurityField, String) -> Unit,
    onSend: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    newEmail: Boolean = false,
    isActive: Boolean = true,
    recipient: String? = null,
    onDone: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onNext: (() -> Unit)? = null,
) {
    val buttonContentPadding = ButtonDefaults.TextButtonContentPadding
    val buttonEndPadding = buttonContentPadding.calculateEndPadding(LocalLayoutDirection.current)
    val totp = !newEmail && state.status?.factor == SecondFactor.TOTP
    val backupAllowed = state.step == SecurityStep.DISABLE_TOTP
    val label = when {
        newEmail -> R.string.security_new_email_code
        totp && backupAllowed -> R.string.security_totp_or_backup
        totp -> R.string.security_totp_code
        state.step == SecurityStep.VERIFY_EMAIL -> R.string.security_current_code
        else -> R.string.auth_verification_code
    }
    AccountFlowSection(
        title = when {
            newEmail -> stringResource(R.string.security_new_email)
            totp -> stringResource(R.string.security_totp_authenticator)
            else -> null
        },
        modifier = modifier,
        supportingContent = recipient?.takeIf { it.isNotBlank() }?.let { email ->
            {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actionContent = if (!totp) {
            {
                val cooldown = if (newEmail) state.newCooldown else state.currentCooldown
                TextButton(
                    onClick = { onSend(newEmail) },
                    enabled = isActive && !state.working && !state.completionPending && cooldown == 0 &&
                        (!newEmail || state.form[SecurityField.EMAIL].isNotBlank()),
                    modifier = Modifier.offset(x = buttonEndPadding).defaultMinSize(minWidth = 48.dp),
                    contentPadding = buttonContentPadding,
                ) {
                    Text(
                        if (cooldown > 0) stringResource(R.string.auth_resend_countdown, cooldown)
                        else stringResource(R.string.auth_resend),
                    )
                }
            }
        } else {
            null
        },
    ) {
        SecurityTextField(
            state, if (newEmail) SecurityField.NEW_EMAIL_CODE else SecurityField.CODE, label, onEdit,
            keyboardType = if (backupAllowed) KeyboardType.Ascii else KeyboardType.Number,
            isActive = isActive,
            onDone = onDone,
            focusRequester = focusRequester,
            onNext = onNext,
        )
    }
}
