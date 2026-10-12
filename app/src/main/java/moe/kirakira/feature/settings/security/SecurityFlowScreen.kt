@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import moe.kirakira.R
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.feature.settings.settingsDestructiveButtonColors
import moe.kirakira.ui.components.AccountFlowActionBar
import moe.kirakira.ui.components.AccountFlowColumn
import moe.kirakira.ui.components.AccountFlowDefaults
import moe.kirakira.ui.components.AccountFlowError
import moe.kirakira.ui.components.AccountFlowFeedback
import moe.kirakira.ui.components.AccountFlowHeader
import moe.kirakira.ui.components.AccountFlowProgress
import moe.kirakira.ui.components.AccountFlowScaffold
import moe.kirakira.ui.components.AccountFlowSection
import moe.kirakira.ui.components.AccountFlowSubmitButton
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.messageRes

@Composable
internal fun SecurityFlowScreen(
    state: SecuritySettingsState,
    step: SecurityStep,
    email: String?,
    sessionBusy: Boolean,
    isActive: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFactor: (SecondFactor) -> Unit,
    onEdit: (SecurityField, String) -> Unit,
    onSendCode: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onFinishCodes: () -> Unit,
    onCopied: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AccountFlowScaffold(
        icon = step.iconRes(),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!sessionBusy && !(state.loading && state.status == null && !state.statusPending)) {
                AccountFlowActionBar {
                    when {
                        email == null -> AccountFlowSubmitButton(
                            label = stringResource(R.string.management_login),
                            onClick = onLogin,
                            enabled = isActive,
                            busy = false,
                        )
                        state.status == null && !state.statusPending -> AccountFlowSubmitButton(
                            label = stringResource(R.string.content_unavailable_retry),
                            onClick = onRefresh,
                            enabled = isActive,
                            busy = false,
                        )
                        else -> SecuritySubmitButton(state, step, onSubmit, onFinishCodes)
                    }
                }
            }
        },
    ) { padding ->
        when {
            sessionBusy || (state.loading && state.status == null && !state.statusPending) -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentAlignment = Alignment.Center,
            ) {
                IndeterminateCircularProgressIndicator()
            }
            email == null -> ContentUnavailableView(
                state = ContentUnavailableState.EMPTY,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                title = stringResource(R.string.security_sign_in),
                description = null,
                iconRes = R.drawable.ic_symbol_lock,
                presentation = ContentUnavailablePresentation.PAGE,
            )
            state.status == null && !state.statusPending -> ContentUnavailableView(
                state = ContentUnavailableState.ERROR,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                description = state.error?.let { stringResource(it.messageRes()) },
                presentation = ContentUnavailablePresentation.PAGE,
            )
            else -> {
                val content: @Composable () -> Unit = {
                    AccountFlowColumn(padding) {
                        SecurityFlow(
                            state = state,
                            step = step,
                            email = email,
                            isActive = isActive,
                            onSelectFactor = onSelectFactor,
                            onEdit = onEdit,
                            onSend = onSendCode,
                            onSubmit = onSubmit,
                            onCopied = onCopied,
                        )
                    }
                }
                if (step == SecurityStep.TWO_FACTOR) {
                    ContentPullToRefresh(
                        isRefreshing = state.loading && state.status != null,
                        onRefresh = onRefresh,
                        enabled = isActive && !state.busy && state.canRefresh,
                        modifier = Modifier.fillMaxSize(),
                        indicatorTopPadding = padding.calculateTopPadding(),
                    ) {
                        content()
                    }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SecurityFlow(
    state: SecuritySettingsState,
    step: SecurityStep,
    email: String,
    isActive: Boolean,
    onSelectFactor: (SecondFactor) -> Unit,
    onEdit: (SecurityField, String) -> Unit,
    onSend: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCopied: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val codeFocusRequester = remember { FocusRequester() }
    val newEmailCodeFocusRequester = remember { FocusRequester() }
    val progressStep = when (step) {
        SecurityStep.EMAIL, SecurityStep.PASSWORD, SecurityStep.TOTP_CONFIRM -> 1
        SecurityStep.VERIFY_EMAIL, SecurityStep.VERIFY_PASSWORD, SecurityStep.SECRETS -> 2
        SecurityStep.OVERVIEW, SecurityStep.TWO_FACTOR,
        SecurityStep.ENABLE_EMAIL, SecurityStep.DISABLE_EMAIL, SecurityStep.DISABLE_TOTP, SecurityStep.CHECK_FACTOR -> null
    }
    Column(modifier.fillMaxWidth()) {
        AccountFlowHeader(
            title = stringResource(step.title()),
            description = state.descriptionRes()?.let { stringResource(it) },
            progressContent = progressStep?.let { currentStep -> { AccountFlowProgress(currentStep, 2) } },
        )
        Spacer(Modifier.height(AccountFlowDefaults.HeaderSpacing))
        Column(verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
            if (state.completionPending) {
                Text(stringResource(R.string.security_completion_pending), style = MaterialTheme.typography.bodyLarge)
            } else when (step) {
                SecurityStep.TWO_FACTOR -> TwoFactorManagement(state, email, isActive, onSelectFactor)
                SecurityStep.EMAIL -> SecurityTextField(
                    state, SecurityField.EMAIL, R.string.security_new_email, onEdit,
                    keyboardType = KeyboardType.Email, isActive = isActive, onDone = onSubmit,
                )
                SecurityStep.VERIFY_EMAIL -> {
                    Column(verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.SectionSpacing)) {
                        AccountFlowSection(title = stringResource(R.string.security_verify_identity)) {
                            SecurityTextField(
                                state, SecurityField.PASSWORD, R.string.security_old_password, onEdit,
                                keyboardType = KeyboardType.Password, isActive = isActive,
                                onNext = { codeFocusRequester.requestFocus() },
                            )
                            SecurityCodeField(
                                state, onEdit, onSend, isActive = isActive,
                                recipient = email.takeUnless { state.status?.factor == SecondFactor.TOTP },
                                focusRequester = codeFocusRequester,
                                onNext = { newEmailCodeFocusRequester.requestFocus() },
                            )
                        }
                        SecurityCodeField(
                            state, onEdit, onSend, newEmail = true, isActive = isActive,
                            recipient = state.form[SecurityField.EMAIL], onDone = onSubmit,
                            focusRequester = newEmailCodeFocusRequester,
                        )
                    }
                }
                SecurityStep.PASSWORD -> {
                    SecurityTextField(
                        state, SecurityField.NEW_PASSWORD, R.string.auth_new_password, onEdit,
                        keyboardType = KeyboardType.Password, isActive = isActive,
                    )
                    SecurityTextField(
                        state, SecurityField.CONFIRM_PASSWORD, R.string.auth_confirm_password, onEdit,
                        keyboardType = KeyboardType.Password, isActive = isActive, onDone = onSubmit,
                    )
                }
                SecurityStep.VERIFY_PASSWORD -> {
                    SecurityTextField(
                        state, SecurityField.PASSWORD, R.string.security_old_password, onEdit,
                        keyboardType = KeyboardType.Password, isActive = isActive,
                        onNext = { codeFocusRequester.requestFocus() },
                    )
                    SecurityCodeField(
                        state, onEdit, onSend, isActive = isActive,
                        recipient = email.takeUnless { state.status?.factor == SecondFactor.TOTP }, onDone = onSubmit,
                        focusRequester = codeFocusRequester,
                    )
                }
                SecurityStep.ENABLE_EMAIL -> SecurityCurrentEmail(email)
                SecurityStep.DISABLE_EMAIL, SecurityStep.DISABLE_TOTP -> {
                    AccountFlowError(stringResource(R.string.security_disable_warning))
                    SecurityTextField(
                        state, SecurityField.PASSWORD, R.string.security_password, onEdit,
                        keyboardType = KeyboardType.Password, isActive = isActive,
                        onNext = { codeFocusRequester.requestFocus() },
                    )
                    SecurityCodeField(
                        state, onEdit, onSend, isActive = isActive, onDone = onSubmit,
                        recipient = email.takeUnless { state.status?.factor == SecondFactor.TOTP },
                        focusRequester = codeFocusRequester,
                    )
                }
                SecurityStep.TOTP_CONFIRM -> {
                    val flow = state.twoFactor as? TwoFactorFlow.ConfirmTotp
                    if (flow != null) {
                        TotpSetupContent(state, flow.setup, isActive, onEdit, onCopied, onSubmit)
                    }
                }
                SecurityStep.SECRETS -> {
                    val flow = state.twoFactor as? TwoFactorFlow.SaveCodes
                    if (flow != null) RecoveryCodes(flow.secrets, isActive, onCopied)
                }
                SecurityStep.CHECK_FACTOR -> Text(
                    text = stringResource(R.string.security_status_pending),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecurityStep.OVERVIEW -> Unit
            }
        }
        AccountFlowFeedback(state.validation?.let { stringResource(it) }) { message ->
            Spacer(Modifier.height(AccountFlowDefaults.FeedbackSpacing))
            AccountFlowError(message)
        }
        AccountFlowFeedback(state.error?.let { stringResource(it.messageRes()) }) { message ->
            Spacer(Modifier.height(AccountFlowDefaults.FeedbackSpacing))
            AccountFlowError(message)
        }
    }
}

@Composable
private fun SecuritySubmitButton(
    state: SecuritySettingsState,
    step: SecurityStep,
    onSubmit: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destructive = step == SecurityStep.DISABLE_EMAIL || step == SecurityStep.DISABLE_TOTP ||
        (step == SecurityStep.TWO_FACTOR && state.status?.factor in setOf(SecondFactor.EMAIL, SecondFactor.TOTP))
    AccountFlowSubmitButton(
        label = stringResource(
            when {
                state.completionPending -> R.string.settings_retry_sync
                step == SecurityStep.TWO_FACTOR -> when (state.status?.factor) {
                    SecondFactor.EMAIL -> R.string.security_disable_email_title
                    SecondFactor.TOTP -> R.string.security_disable_totp_title
                    SecondFactor.NONE, null -> {
                        val flow = state.twoFactor as? TwoFactorFlow.Manage
                        if (flow?.selectedFactor == SecondFactor.TOTP && flow.draft is TotpDraft.Available) {
                            R.string.security_continue_binding
                        } else {
                            R.string.auth_continue
                        }
                    }
                }
                step == SecurityStep.EMAIL -> R.string.security_verify_email
                step == SecurityStep.PASSWORD -> R.string.security_verify_identity
                step == SecurityStep.VERIFY_EMAIL -> R.string.security_change_email
                step == SecurityStep.VERIFY_PASSWORD -> R.string.security_change_password
                step == SecurityStep.SECRETS -> R.string.security_codes_saved
                step == SecurityStep.CHECK_FACTOR -> R.string.security_check_status
                step == SecurityStep.DISABLE_TOTP -> R.string.security_disable_totp_title
                step == SecurityStep.DISABLE_EMAIL -> R.string.security_disable_email_title
                step == SecurityStep.ENABLE_EMAIL -> R.string.security_enable
                step == SecurityStep.TOTP_CONFIRM -> R.string.security_confirm
                else -> R.string.security_save
            },
        ),
        onClick = if (step == SecurityStep.SECRETS) onFinish else onSubmit,
        enabled = state.canSubmit,
        busy = state.busy,
        modifier = modifier,
        colors = if (destructive) settingsDestructiveButtonColors() else ButtonDefaults.buttonColors(),
        actionDescription = when {
            state.completionPending -> stringResource(R.string.profile_finish_save)
            step == SecurityStep.SECRETS -> stringResource(R.string.security_done)
            else -> null
        },
    )
}
