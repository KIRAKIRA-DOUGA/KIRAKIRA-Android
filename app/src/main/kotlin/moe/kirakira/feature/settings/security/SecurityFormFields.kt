package moe.kirakira.feature.settings.security

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import moe.kirakira.R
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.ui.components.form.AccountFlowSection
import moe.kirakira.ui.components.form.FilledTextField
import moe.kirakira.ui.components.form.FormButtonEdge
import moe.kirakira.ui.components.form.FormTextButton
import moe.kirakira.ui.components.form.OtpInputField
import moe.kirakira.ui.components.form.OtpInputType
import moe.kirakira.ui.components.form.PasswordTextField
import moe.kirakira.ui.components.form.rememberBoundTextField

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
    LaunchedEffect(isActive, state.working, state.completionPending) { visible = false }
    val password = keyboardType == KeyboardType.Password
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
    val editor = rememberBoundTextField(state.form[field], { onEdit(field, it) }, isActive && enabled)
    if (field == SecurityField.CODE || field == SecurityField.NEW_EMAIL_CODE) {
        OtpInputField(
            state = editor,
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
    val fieldModifier = modifier
        .fillMaxWidth()
        .focusRequester(focusRequester)
        .then(
            when {
                password -> Modifier.semantics {
                    contentType = if (field == SecurityField.PASSWORD) ContentType.Password else ContentType.NewPassword
                }
                field == SecurityField.EMAIL -> Modifier.semantics { contentType = ContentType.EmailAddress }
                else -> Modifier
            },
        )
    val options = KeyboardOptions(
        autoCorrectEnabled = false,
        keyboardType = keyboardType,
        imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
    )
    val actions = KeyboardActionHandler {
        if (isActive && enabled) {
            if (onDone != null) onDone() else if (onNext != null) onNext() else focus.moveFocus(FocusDirection.Next)
        }
    }
    if (password) {
        PasswordTextField(
            state = editor,
            passwordVisible = visible,
            onPasswordVisibilityChange = { visible = !visible },
            modifier = fieldModifier,
            enabled = isActive && enabled,
            errorMessage = error,
            keyboardOptions = options,
            onKeyboardAction = actions,
            label = { Text(stringResource(label)) },
        )
    } else {
        FilledTextField(
            state = editor,
            modifier = fieldModifier,
            enabled = isActive && enabled,
            errorMessage = error,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = options,
            onKeyboardAction = actions,
            label = { Text(stringResource(label)) },
        )
    }
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
                val recipient = if (newEmail) CodeRecipient.NEW_EMAIL else CodeRecipient.CURRENT_EMAIL
                FormTextButton(
                    onClick = { onSend(newEmail) },
                    loading = state.codeRecipient == recipient,
                    enabled = isActive && !state.working && !state.completionPending && cooldown == 0 &&
                        (!newEmail || state.form[SecurityField.EMAIL].isNotBlank()),
                    edge = FormButtonEdge.END,
                ) {
                    Text(
                        if (cooldown > 0) stringResource(R.string.auth_resend_countdown, cooldown)
                        else stringResource(R.string.auth_resend),
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
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
