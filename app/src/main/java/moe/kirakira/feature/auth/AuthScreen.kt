package moe.kirakira.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.AccountFlowActionBar
import moe.kirakira.ui.components.AccountFlowColumn
import moe.kirakira.ui.components.AccountFlowDefaults
import moe.kirakira.ui.components.AccountFlowError
import moe.kirakira.ui.components.AccountFlowHeader
import moe.kirakira.ui.components.AccountFlowProgress
import moe.kirakira.ui.components.AccountFlowScaffold
import moe.kirakira.ui.components.AccountFlowSubmitButton

@Composable
internal fun AuthScreen(
    state: AuthUiState,
    passwordVisible: Boolean,
    snackbarHostState: SnackbarHostState,
    emailFocusRequester: FocusRequester,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: () -> Unit,
    onSubmit: () -> Unit,
    onRegister: () -> Unit,
    // Retained for existing callers; the toolbar no longer offers a close action.
    onClose: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFieldChange: (AuthField, String) -> Unit = { _, _ -> },
    onForgotPassword: () -> Unit = {},
    onResend: () -> Unit = {},
    onRecoveryHelp: () -> Unit = {},
    focusRequesters: Map<AuthField, FocusRequester> = remember { AuthField.entries.associateWith { FocusRequester() } },
) {
    val buttonContentPadding = ButtonDefaults.TextButtonContentPadding
    val buttonStartPadding = buttonContentPadding.calculateStartPadding(LocalLayoutDirection.current)
    val buttonEndPadding = buttonContentPadding.calculateEndPadding(LocalLayoutDirection.current)
    val title = when (state.step) {
        AuthStep.REGISTER_PROFILE -> R.string.auth_profile_title
        AuthStep.REGISTER_CREDENTIALS -> R.string.auth_credentials_title
        AuthStep.REGISTER_INVITATION -> R.string.auth_invitation_title
        AuthStep.REGISTER_VERIFY -> R.string.auth_verify_email_title
        AuthStep.LOGIN_EMAIL -> R.string.auth_verify_email_title
        AuthStep.LOGIN_TOTP -> R.string.auth_totp_title
        AuthStep.FORGOT_EMAIL -> R.string.auth_forgot_password
        AuthStep.TOTP_HELP -> R.string.auth_recovery_title
        AuthStep.RESET_PASSWORD, AuthStep.SAVE_PASSWORD_RESET -> R.string.auth_reset_password
        AuthStep.SAVE_SESSION -> R.string.auth_save_session
        else -> R.string.auth_sign_in
    }
    val description = when (state.step) {
        AuthStep.LOGIN_TOTP -> R.string.auth_totp_description
        AuthStep.TOTP_HELP -> R.string.auth_totp_recovery_description
        AuthStep.SAVE_SESSION -> R.string.auth_save_session_description
        AuthStep.SAVE_PASSWORD_RESET -> R.string.auth_finish_reset_description
        AuthStep.LOGIN, AuthStep.LOGIN_EMAIL, AuthStep.REGISTER_PROFILE, AuthStep.REGISTER_CREDENTIALS,
        AuthStep.REGISTER_INVITATION, AuthStep.REGISTER_VERIFY, AuthStep.FORGOT_EMAIL, AuthStep.RESET_PASSWORD,
            -> null
    }
    val submitLabel = when (state.step) {
        AuthStep.LOGIN, AuthStep.LOGIN_EMAIL, AuthStep.LOGIN_TOTP -> R.string.auth_sign_in_button
        AuthStep.REGISTER_VERIFY -> R.string.auth_register
        AuthStep.RESET_PASSWORD -> R.string.auth_reset_password
        AuthStep.SAVE_SESSION, AuthStep.SAVE_PASSWORD_RESET -> R.string.auth_retry
        else -> R.string.auth_continue
    }
    val stepIcon = when (state.step) {
        AuthStep.LOGIN -> R.drawable.logo_kirakira
        AuthStep.LOGIN_EMAIL, AuthStep.REGISTER_VERIFY -> R.drawable.ic_symbol_mail
        AuthStep.LOGIN_TOTP -> R.drawable.ic_symbol_lock
        AuthStep.REGISTER_PROFILE -> R.drawable.ic_symbol_person
        AuthStep.REGISTER_CREDENTIALS -> R.drawable.ic_symbol_person_add
        AuthStep.REGISTER_INVITATION -> R.drawable.ic_symbol_confirmation_number
        AuthStep.FORGOT_EMAIL -> R.drawable.ic_symbol_manage_accounts
        AuthStep.RESET_PASSWORD -> R.drawable.ic_symbol_password
        AuthStep.TOTP_HELP -> R.drawable.ic_symbol_help
        AuthStep.SAVE_SESSION, AuthStep.SAVE_PASSWORD_RESET -> R.drawable.ic_symbol_save
    }
    val registrationStep = when (state.step) {
        AuthStep.REGISTER_PROFILE -> 1
        AuthStep.REGISTER_CREDENTIALS -> 2
        AuthStep.REGISTER_INVITATION -> 3
        AuthStep.REGISTER_VERIFY -> 4
        else -> 0
    }
    val heading = stringResource(title)
    AccountFlowScaffold(
        icon = stepIcon,
        iconSize = if (state.step == AuthStep.LOGIN) 40.dp else 32.dp,
        onBack = onBack.takeIf { state.step != AuthStep.LOGIN },
        backButtonModifier = Modifier.testTag("auth_back"),
        modifier = modifier.testTag("auth_${state.step.name}"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.step != AuthStep.TOTP_HELP) {
                AccountFlowActionBar {
                    AccountFlowSubmitButton(
                        label = stringResource(
                            if (state.submission == AuthSubmission.FAILED) R.string.auth_retry else submitLabel,
                        ),
                        onClick = onSubmit,
                        enabled = state.canSubmit,
                        busy = state.isSubmitting,
                        actionDescription = heading.takeIf { state.isSubmitting },
                        modifier = Modifier.testTag("auth_submit"),
                    )
                }
            }
        },
    ) { padding ->
        AccountFlowColumn(padding) {
            AccountFlowHeader(
                title = heading,
                description = description?.let { stringResource(it) },
                progressContent = if (registrationStep > 0) {
                    { AccountFlowProgress(registrationStep, 4) }
                } else {
                    null
                },
            )
            if (state.step != AuthStep.TOTP_HELP && state.step != AuthStep.SAVE_SESSION &&
                state.step != AuthStep.SAVE_PASSWORD_RESET
            ) {
                Spacer(Modifier.height(AccountFlowDefaults.HeaderSpacing))
                AuthForm(
                    state = state,
                    passwordVisible = passwordVisible,
                    emailFocusRequester = emailFocusRequester,
                    focusRequesters = focusRequesters,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onPasswordVisibilityChange = onPasswordVisibilityChange,
                    onSubmit = onSubmit,
                    onFieldChange = onFieldChange,
                    onResend = onResend,
                )
            }
            if (state.step == AuthStep.LOGIN) {
                Spacer(Modifier.height(AccountFlowDefaults.FeedbackSpacing))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = onForgotPassword,
                        enabled = state.canEdit,
                        modifier = Modifier.offset(x = -buttonStartPadding).defaultMinSize(minWidth = 48.dp),
                        contentPadding = buttonContentPadding,
                    ) {
                        Text(stringResource(R.string.auth_forgot_password))
                    }
                    Box(
                        modifier = Modifier.width(IntrinsicSize.Max).weight(1f),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        TextButton(
                            onClick = onRegister,
                            enabled = state.canEdit,
                            modifier = Modifier
                                .offset(x = buttonEndPadding)
                                .defaultMinSize(minWidth = 48.dp)
                                .testTag("auth_register"),
                            contentPadding = buttonContentPadding,
                        ) {
                            Text(stringResource(R.string.auth_register))
                        }
                    }
                }
            } else if (state.step == AuthStep.TOTP_HELP) {
                Spacer(Modifier.height(AccountFlowDefaults.FeedbackSpacing))
                TextButton(
                    onClick = onRecoveryHelp,
                    modifier = Modifier.offset(x = -buttonStartPadding).defaultMinSize(minWidth = 48.dp),
                    contentPadding = buttonContentPadding,
                ) {
                    Text(stringResource(R.string.auth_contact_support))
                }
            }
            val hasError = state.errorRes != null ||
                (state.submission == AuthSubmission.FAILED && state.fieldErrors.isEmpty())
            if (state.noticeRes != null || hasError) {
                Spacer(Modifier.height(AccountFlowDefaults.FeedbackSpacing))
                Column(verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FeedbackSpacing)) {
                    state.noticeRes?.let {
                        Text(
                            text = stringResource(it),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    if (hasError) {
                        AccountFlowError(stringResource(state.errorRes ?: R.string.auth_sign_in_failed))
                    }
                }
            }
        }
    }
}
