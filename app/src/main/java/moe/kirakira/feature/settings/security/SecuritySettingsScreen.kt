@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.kirakira.R
import moe.kirakira.core.credentials.passwordCredentialGateway
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.data.security.TotpSecrets
import moe.kirakira.data.security.TotpSetup
import moe.kirakira.feature.settings.SettingsActionBar
import moe.kirakira.feature.settings.SettingsDefaults
import moe.kirakira.feature.settings.SettingsErrorCard
import moe.kirakira.feature.settings.SettingsItem
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.feature.settings.SettingsSection
import moe.kirakira.feature.settings.settingsDestructiveButtonColors
import moe.kirakira.ui.components.AccountFlowColumn
import moe.kirakira.ui.components.AccountFlowDefaults
import moe.kirakira.ui.components.AccountFlowError
import moe.kirakira.ui.components.AccountFlowHeader
import moe.kirakira.ui.components.AccountFlowProgress
import moe.kirakira.ui.components.AccountFlowScaffold
import moe.kirakira.ui.components.AccountFlowSubmitButton
import moe.kirakira.ui.components.AnimatedSlashIcon
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.ShadowFilledTonalButton
import moe.kirakira.ui.components.SlashIconType
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.semanticColors

@Composable
internal fun SecuritySettingsPage(
    model: SecuritySettingsViewModel,
    isActive: Boolean,
    onBack: () -> Unit,
    onLogin: (String) -> Unit,
    predictiveBackEnabled: Boolean = false,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val autofill = LocalAutofillManager.current
    LaunchedEffect(model, isActive) {
        if (!isActive) { autofill?.cancel(); model.deactivate() }
        else model.refresh()
    }
    LaunchedEffect(state.loginEmail, isActive) {
        val email = state.loginEmail ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        autofill?.cancel()
        passwordCredentialGateway(context).clearSession()
        onLogin(email)
        model.consumeLogin()
    }
    DisposableEffect(Unit) { onDispose { autofill?.cancel() } }
    SecuritySettingsNavigation(
        state = if (state.revision == session.revision) state else SecuritySettingsState(revision = session.revision),
        model = model,
        email = session.activeProfile?.email,
        sessionBusy = session.isLoading || session.isBusy,
        isActive = isActive,
        predictiveBackEnabled = predictiveBackEnabled,
        onBack = onBack,
        onLogin = { onLogin("") },
    )
}

@Composable
internal fun SecuritySettingsScreen(
    state: SecuritySettingsState,
    email: String?,
    sessionBusy: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (SecurityStep) -> Unit,
    onEdit: (SecurityField, String) -> Unit,
    onSendCode: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onFinishCodes: () -> Unit,
    onCancelDiscard: () -> Unit,
    onDismissMessage: () -> Unit,
    onCopied: (Boolean) -> Unit,
    onCancelDiscardCredentials: () -> Unit,
    onDiscardCredentials: () -> Unit,
    step: SecurityStep = state.step,
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val direction = LocalLayoutDirection.current
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message, isActive) {
        if (message != null && isActive) { snackbar.showSnackbar(message); onDismissMessage() }
        if (!isActive) snackbar.currentSnackbarData?.dismiss()
    }
    if (step == SecurityStep.OVERVIEW || step == SecurityStep.TWO_FACTOR) {
        SettingsScaffold(
            title = stringResource(step.title()),
            onBack = onBack,
            shadingIcon = R.drawable.ic_symbol_lock,
            modifier = modifier,
            imePadding = true,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                val factor = state.status?.factor
                if (email != null && !sessionBusy && step == SecurityStep.TWO_FACTOR &&
                    (factor == SecondFactor.EMAIL || factor == SecondFactor.TOTP)
                ) {
                    SecurityActionBar(state, onOpen)
                }
            },
        ) { padding ->
            ContentPullToRefresh(
                isRefreshing = email != null && state.loading && state.status != null,
                onRefresh = onRefresh,
                enabled = isActive && email != null && !sessionBusy && !state.busy && state.canRefresh,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                indicatorTopPadding = padding.calculateTopPadding(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    when {
                        sessionBusy || (state.loading && state.status == null && !state.statusPending) -> Box(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            IndeterminateCircularProgressIndicator()
                        }
                        email == null -> ContentUnavailableView(
                            state = ContentUnavailableState.EMPTY,
                            modifier = Modifier.padding(padding),
                            title = stringResource(R.string.security_sign_in),
                            description = null,
                            iconRes = R.drawable.ic_symbol_lock,
                            presentation = ContentUnavailablePresentation.PAGE,
                            primaryAction = ContentUnavailableAction(
                                stringResource(R.string.management_login), onLogin,
                            ),
                        )
                        else -> Column(
                            modifier = Modifier
                                .widthIn(max = SettingsDefaults.MaxContentWidth)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(
                                    start = padding.calculateStartPadding(direction) + SettingsDefaults.HorizontalPadding,
                                    end = padding.calculateEndPadding(direction) + SettingsDefaults.HorizontalPadding,
                                    top = padding.calculateTopPadding() + SettingsDefaults.TopPadding,
                                    bottom = padding.calculateBottomPadding() + SettingsDefaults.BottomPadding,
                                ),
                            verticalArrangement = Arrangement.spacedBy(SettingsDefaults.SectionSpacing),
                        ) {
                            when {
                                step == SecurityStep.OVERVIEW && (state.status != null || state.statusPending) -> {
                                    SecurityOverview(state, email, onOpen)
                                    state.error?.let { SettingsErrorCard(stringResource(it.messageRes())) }
                                }
                                state.status == null -> ContentUnavailableView(
                                    state = ContentUnavailableState.ERROR,
                                    description = state.error?.let { stringResource(it.messageRes()) },
                                    presentation = ContentUnavailablePresentation.INLINE,
                                    onRetry = onRefresh,
                                )
                                else -> {
                                    TwoFactorManagement(state, email, onOpen)
                                    state.error?.let { SettingsErrorCard(stringResource(it.messageRes())) }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        SecurityFlowScreen(
            state = state,
            step = step,
            email = email,
            sessionBusy = sessionBusy,
            isActive = isActive,
            snackbarHostState = snackbar,
            onBack = onBack,
            onLogin = onLogin,
            onRefresh = onRefresh,
            onEdit = onEdit,
            onSendCode = onSendCode,
            onSubmit = onSubmit,
            onFinishCodes = onFinishCodes,
            onCopied = onCopied,
            modifier = modifier,
        )
    }
    if (state.confirmDiscardCodes && isActive) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.security_leave_codes_title)) },
            text = { Text(stringResource(R.string.security_leave_codes_message)) },
            confirmButton = { TextButton(onClick = onFinishCodes) { Text(stringResource(R.string.privacy_discard)) } },
            dismissButton = { TextButton(onClick = onCancelDiscard) { Text(stringResource(R.string.profile_keep_editing)) } },
        )
    }
    if (state.confirmDiscardCredentials && isActive) {
        AlertDialog(
            onDismissRequest = onCancelDiscardCredentials,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.security_leave_changes_title)) },
            text = { Text(stringResource(R.string.profile_discard_message)) },
            confirmButton = { TextButton(onClick = onDiscardCredentials) { Text(stringResource(R.string.privacy_discard)) } },
            dismissButton = { TextButton(onClick = onCancelDiscardCredentials) { Text(stringResource(R.string.profile_keep_editing)) } },
        )
    }
}

@Composable
private fun SecurityFlowScreen(
    state: SecuritySettingsState,
    step: SecurityStep,
    email: String?,
    sessionBusy: Boolean,
    isActive: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
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
                primaryAction = ContentUnavailableAction(stringResource(R.string.management_login), onLogin),
            )
            state.status == null && !state.statusPending -> ContentUnavailableView(
                state = ContentUnavailableState.ERROR,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                description = state.error?.let { stringResource(it.messageRes()) },
                presentation = ContentUnavailablePresentation.PAGE,
                onRetry = onRefresh,
            )
            else -> AccountFlowColumn(padding) {
                SecurityFlow(
                    state = state,
                    step = step,
                    email = email,
                    isActive = isActive,
                    onEdit = onEdit,
                    onSend = onSendCode,
                    onSubmit = onSubmit,
                    onFinish = onFinishCodes,
                    onCopied = onCopied,
                )
            }
        }
    }
}

@Composable
private fun SecurityOverview(state: SecuritySettingsState, email: String, onOpen: (SecurityStep) -> Unit) {
    SecurityStatusBanner(state)
    val canEdit = !state.working && state.status != null && !state.statusPending
    SettingsSection(stringResource(R.string.security_account_section)) {
        SecurityItem(R.drawable.ic_symbol_mail, stringResource(R.string.auth_email), email, 0, 2,
            enabled = canEdit, onClick = { onOpen(SecurityStep.EMAIL) })
        SecurityItem(R.drawable.ic_symbol_password, stringResource(R.string.security_password),
            stringResource(R.string.security_change_password), 1, 2, enabled = canEdit,
            onClick = { onOpen(SecurityStep.PASSWORD) })
    }
    SettingsSection(stringResource(R.string.security_two_factor_section)) {
        SecurityItem(R.drawable.ic_symbol_lock, stringResource(R.string.security_two_factor_section),
            stringResource(state.status?.factor?.label() ?: R.string.security_status_pending),
            0, 1, enabled = !state.working, onClick = { onOpen(SecurityStep.TWO_FACTOR) })
    }
}

@Composable
private fun SecurityStatusBanner(state: SecuritySettingsState) {
    val factor = state.status?.factor
    val protected = factor != null && factor != SecondFactor.NONE
    val colors = MaterialTheme.semanticColors
    val containerColor = if (protected) colors.success else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (protected) colors.onSuccess else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = MaterialShapes.Cookie12Sided.toShape(),
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(if (protected) R.drawable.ic_symbol_lock else R.drawable.ic_symbol_error),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(when {
                        state.statusPending -> R.string.security_status_pending
                        protected -> R.string.security_protected
                        else -> R.string.security_unprotected
                    }),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (factor != null) Text(stringResource(factor.label()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TwoFactorManagement(state: SecuritySettingsState, email: String, onOpen: (SecurityStep) -> Unit) {
    val status = state.status ?: return
    val flow = state.twoFactor as? TwoFactorFlow.Manage ?: return
    SecurityStatusBanner(state)
    when (status.factor) {
        SecondFactor.NONE -> SettingsSection(stringResource(R.string.security_verification_method)) {
            SecurityItem(R.drawable.ic_symbol_mail, stringResource(R.string.security_email_authenticator),
                stringResource(R.string.security_not_enabled), 0, 2, enabled = !state.working,
                onClick = { onOpen(SecurityStep.ENABLE_EMAIL) })
            SecurityItem(R.drawable.ic_symbol_lock, stringResource(R.string.security_totp_authenticator),
                stringResource(when (flow.draft) {
                    null -> R.string.security_start_binding
                    is TotpDraft.Available -> R.string.security_continue_binding
                    TotpDraft.Unavailable -> R.string.security_not_enabled
                }),
                1, 2, enabled = !state.working && flow.draft != TotpDraft.Unavailable,
                busy = state.busy,
                onClick = { onOpen(SecurityStep.TOTP_CONFIRM) })
        }
        SecondFactor.EMAIL, SecondFactor.TOTP -> SettingsSection(stringResource(R.string.security_current_method)) {
            SettingsItem(
                title = stringResource(status.factor.label()),
                index = 0,
                count = 1,
                icon = if (status.factor == SecondFactor.EMAIL) R.drawable.ic_symbol_mail else R.drawable.ic_symbol_lock,
                supporting = if (status.factor == SecondFactor.EMAIL) email else {
                    status.totpCreationDateTime?.let {
                        stringResource(R.string.security_added_date, DateFormat.getDateInstance().format(Date(it)))
                    } ?: stringResource(R.string.security_enabled)
                },
            )
        }
    }
    flow.notice?.let { SettingsErrorCard(stringResource(it)) }
}

@Composable
private fun SecurityItem(icon: Int, title: String, summary: String, index: Int, count: Int,
    enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shapes = connectedListItemShapes(index, count),
        leadingContent = {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        },
        supportingContent = { Text(summary) },
        trailingContent = {
            if (busy) {
                IndeterminateCircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_symbol_chevron_right), null, Modifier.size(24.dp))
            }
        },
        content = { Text(title) },
    )
}

@Composable
private fun SecurityFlow(
    state: SecuritySettingsState,
    step: SecurityStep,
    email: String,
    isActive: Boolean,
    onEdit: (SecurityField, String) -> Unit,
    onSend: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onFinish: () -> Unit,
    onCopied: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressStep = when (step) {
        SecurityStep.EMAIL, SecurityStep.PASSWORD, SecurityStep.TOTP_CONFIRM -> 1
        SecurityStep.VERIFY_EMAIL, SecurityStep.VERIFY_PASSWORD, SecurityStep.SECRETS -> 2
        SecurityStep.OVERVIEW, SecurityStep.TWO_FACTOR,
        SecurityStep.ENABLE_EMAIL, SecurityStep.DISABLE_EMAIL, SecurityStep.DISABLE_TOTP, SecurityStep.CHECK_FACTOR -> null
    }
    Column(modifier.fillMaxWidth()) {
        AccountFlowHeader(
            title = stringResource(step.title()),
            description = if (step == SecurityStep.VERIFY_PASSWORD && !state.completionPending) {
                stringResource(R.string.security_password_sign_in_required)
            } else {
                null
            },
            progressContent = progressStep?.let { currentStep -> { AccountFlowProgress(currentStep, 2) } },
        )
        Spacer(Modifier.height(AccountFlowDefaults.SectionSpacing))
        Column(verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
            if (state.completionPending) {
                Text(stringResource(R.string.security_completion_pending), style = MaterialTheme.typography.bodyLarge)
            } else when (step) {
                SecurityStep.EMAIL -> {
                    SecurityCurrentEmail(email)
                    SecurityTextField(
                        state, SecurityField.EMAIL, R.string.security_new_email, onEdit,
                        keyboardType = KeyboardType.Email, isActive = isActive, onDone = onSubmit,
                    )
                }
                SecurityStep.VERIFY_EMAIL -> {
                    Column(verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
                        Text(
                            text = stringResource(R.string.security_verify_identity),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        SecurityTextField(
                            state, SecurityField.PASSWORD, R.string.security_old_password, onEdit,
                            keyboardType = KeyboardType.Password, isActive = isActive,
                        )
                        SecurityCodeField(
                            state, onEdit, onSend, isActive = isActive,
                            recipient = email.takeUnless { state.status?.factor == SecondFactor.TOTP },
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
                        Text(
                            text = stringResource(R.string.security_new_email),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        SecurityCodeField(
                            state, onEdit, onSend, newEmail = true, isActive = isActive,
                            recipient = state.form[SecurityField.EMAIL], onDone = onSubmit,
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
                    )
                    SecurityCodeField(
                        state, onEdit, onSend, isActive = isActive,
                        recipient = email.takeUnless { state.status?.factor == SecondFactor.TOTP }, onDone = onSubmit,
                    )
                }
                SecurityStep.ENABLE_EMAIL -> Text(email, style = MaterialTheme.typography.bodyLarge)
                SecurityStep.DISABLE_EMAIL, SecurityStep.DISABLE_TOTP -> {
                    AccountFlowError(stringResource(R.string.security_disable_warning))
                    SecurityTextField(
                        state, SecurityField.PASSWORD, R.string.security_password, onEdit,
                        keyboardType = KeyboardType.Password, isActive = isActive,
                    )
                    SecurityCodeField(state, onEdit, onSend, isActive = isActive, onDone = onSubmit)
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
                SecurityStep.OVERVIEW, SecurityStep.TWO_FACTOR -> Unit
            }
        }
        Spacer(Modifier.height(AccountFlowDefaults.SectionSpacing))
        Column(verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FeedbackSpacing)) {
            state.validation?.let { AccountFlowError(stringResource(it)) }
            state.error?.let { AccountFlowError(stringResource(it.messageRes())) }
            SecuritySubmitButton(state, step, onSubmit, onFinish)
        }
    }
}

/** Only the two-factor management page retains a fixed settings action bar. */
@Composable
private fun SecurityActionBar(state: SecuritySettingsState, onOpen: (SecurityStep) -> Unit) {
    val totp = state.status?.factor == SecondFactor.TOTP
    SettingsActionBar(
        label = stringResource(
            when {
                state.busy -> R.string.auth_working
                totp -> R.string.security_disable_totp_title
                else -> R.string.security_disable_email_title
            },
        ),
        onClick = { onOpen(if (totp) SecurityStep.DISABLE_TOTP else SecurityStep.DISABLE_EMAIL) },
        enabled = state.canSubmit,
        busy = state.busy,
        colors = settingsDestructiveButtonColors(),
    )
}

@Composable
private fun SecuritySubmitButton(
    state: SecuritySettingsState,
    step: SecurityStep,
    onSubmit: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destructive = step == SecurityStep.DISABLE_EMAIL || step == SecurityStep.DISABLE_TOTP
    AccountFlowSubmitButton(
        label = stringResource(
            when {
                state.completionPending -> R.string.settings_retry_sync
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

@Composable
private fun SecurityCurrentEmail(email: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.security_current_email),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(email, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SecurityTextField(
    state: SecuritySettingsState,
    field: SecurityField,
    label: Int,
    onEdit: (SecurityField, String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isActive: Boolean = true,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) { visible = false }
    val password = keyboardType == KeyboardType.Password
    val showPassword = visible && isActive
    val enabled = !state.working && !state.completionPending
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val error = state.fieldErrors[field]?.let { stringResource(it) }
    LaunchedEffect(state.validationAttempt, isActive, lifecycle) {
        if (isActive && state.fieldErrors.keys.firstOrNull() == field) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }
    OutlinedTextField(
        value = state.form[field],
        onValueChange = { onEdit(field, it) },
        modifier = modifier
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
            ),
        label = { Text(stringResource(label)) },
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
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
            onNext = { focus.moveFocus(FocusDirection.Next) },
            onDone = { if (isActive && enabled) onDone?.invoke() },
        ),
        trailingIcon = if (password) {
            {
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
        } else {
            null
        },
    )
}

@Composable
private fun SecurityCodeField(
    state: SecuritySettingsState,
    onEdit: (SecurityField, String) -> Unit,
    onSend: (Boolean) -> Unit,
    newEmail: Boolean = false,
    isActive: Boolean = true,
    recipient: String? = null,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!recipient.isNullOrBlank()) {
            Text(
                text = recipient,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SecurityTextField(
            state, if (newEmail) SecurityField.NEW_EMAIL_CODE else SecurityField.CODE, label, onEdit,
            keyboardType = if (backupAllowed) KeyboardType.Ascii else KeyboardType.Number,
            isActive = isActive,
            onDone = onDone,
        )
        if (!totp) {
            val cooldown = if (newEmail) state.newCooldown else state.currentCooldown
            TextButton(
                onClick = { onSend(newEmail) },
                enabled = isActive && !state.working && !state.completionPending && cooldown == 0 &&
                    (!newEmail || state.form[SecurityField.EMAIL].isNotBlank()),
            ) {
                Text(
                    if (cooldown > 0) stringResource(R.string.auth_resend_countdown, cooldown)
                    else stringResource(R.string.auth_resend),
                )
            }
        }
    }
}

@Composable
private fun TotpSetupContent(
    state: SecuritySettingsState,
    setup: TotpSetup,
    isActive: Boolean,
    onEdit: (SecurityField, String) -> Unit,
    onCopied: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = remember { SensitiveClipboard(context) }
    DisposableEffect(clipboard, isActive) {
        if (!isActive) clipboard.clear()
        onDispose { clipboard.clear() }
    }
    val bitmap by produceState<Bitmap?>(null, setup.uri) {
        value = withContext(Dispatchers.Default) { qrBitmap(setup.uri) }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing),
    ) {
        Box(
            modifier = Modifier.size(232.dp).background(Color.White, MaterialTheme.shapes.large).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(image.asImageBitmap(), stringResource(R.string.security_qr_code), Modifier.fillMaxSize())
            } else {
                Text(stringResource(R.string.security_qr_failed), color = Color.Black)
            }
        }
        OutlinedButton(
            onClick = {
                val opened = runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(setup.uri)))
                    true
                }.getOrDefault(false)
                if (!opened) onCopied(false)
            },
            enabled = isActive && !state.working,
        ) {
            Text(stringResource(R.string.security_open_authenticator))
        }
        SecretCard(stringResource(R.string.security_setup_key)) {
            Text(
                text = setup.secret.chunked(4).joinToString(" "),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
        CopyButton(stringResource(R.string.security_copy_key), enabled = isActive && !state.working) {
            onCopied(clipboard.copy(setup.secret))
        }
        SecurityTextField(
            state, SecurityField.CODE, R.string.security_totp_code, onEdit,
            keyboardType = KeyboardType.Number, isActive = isActive, onDone = onSubmit,
        )
    }
}

@Composable
private fun RecoveryCodes(
    secrets: TotpSecrets,
    isActive: Boolean,
    onCopied: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = remember { SensitiveClipboard(context) }
    DisposableEffect(clipboard, isActive) {
        if (!isActive) clipboard.clear()
        onDispose { clipboard.clear() }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.FieldSpacing)) {
        SecretCard(stringResource(R.string.security_backup_codes)) {
            secrets.backupCodes.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEach {
                        Text(
                            text = it,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        SecretCard(stringResource(R.string.security_recovery_code)) {
            Text(
                text = secrets.recoveryCode.chunked(4).joinToString(" "),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
        CopyButton(stringResource(R.string.security_copy_codes), enabled = isActive) {
            onCopied(clipboard.copy(secrets.backupCodes.joinToString("\n") + "\n\n" + secrets.recoveryCode))
        }
    }
}

/** Selectable monospace card for one-time secrets. */
@Composable
private fun SecretCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        SelectionContainer {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }
}

@Composable
private fun CopyButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    ShadowFilledTonalButton(onClick = onClick, enabled = enabled, shapes = ButtonDefaults.shapes()) {
        Icon(painterResource(R.drawable.ic_symbol_content_copy), null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

private class SensitiveClipboard(context: Context) {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val owner = UUID.randomUUID().toString()
    fun copy(value: String): Boolean = runCatching {
        val clip = ClipData.newPlainText("", value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
            putString("moe.kirakira.security.owner", owner)
        }
        manager.setPrimaryClip(clip)
        true
    }.getOrDefault(false)
    fun clear() {
        runCatching {
            if (manager.primaryClipDescription?.extras?.getString("moe.kirakira.security.owner") == owner) {
                if (android.os.Build.VERSION.SDK_INT >= 28) manager.clearPrimaryClip()
                else manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}

private fun qrBitmap(value: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 560, 560,
        mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
    val pixels = IntArray(560 * 560) { index -> if (matrix[index % 560, index / 560])
        android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    Bitmap.createBitmap(pixels, 560, 560, Bitmap.Config.ARGB_8888)
}.getOrNull()

private fun SecurityStep.iconRes(): Int = when (this) {
    SecurityStep.EMAIL, SecurityStep.VERIFY_EMAIL, SecurityStep.ENABLE_EMAIL, SecurityStep.DISABLE_EMAIL ->
        R.drawable.ic_symbol_mail
    SecurityStep.PASSWORD, SecurityStep.VERIFY_PASSWORD -> R.drawable.ic_symbol_password
    SecurityStep.SECRETS -> R.drawable.ic_symbol_check
    SecurityStep.OVERVIEW, SecurityStep.TWO_FACTOR, SecurityStep.TOTP_CONFIRM,
    SecurityStep.DISABLE_TOTP, SecurityStep.CHECK_FACTOR -> R.drawable.ic_symbol_lock
}

private fun SecondFactor.label(): Int = when (this) {
    SecondFactor.NONE -> R.string.security_not_enabled
    SecondFactor.EMAIL -> R.string.security_email_authenticator
    SecondFactor.TOTP -> R.string.security_totp_authenticator
}

private fun SecurityStep.title(): Int = when (this) {
    SecurityStep.OVERVIEW -> R.string.settings_security
    SecurityStep.EMAIL -> R.string.security_change_email
    SecurityStep.VERIFY_EMAIL -> R.string.security_verify_email_title
    SecurityStep.PASSWORD -> R.string.security_change_password
    SecurityStep.VERIFY_PASSWORD -> R.string.security_verify_identity
    SecurityStep.TWO_FACTOR -> R.string.security_two_factor_section
    SecurityStep.ENABLE_EMAIL -> R.string.security_enable_email_title
    SecurityStep.DISABLE_EMAIL -> R.string.security_disable_email_title
    SecurityStep.TOTP_CONFIRM -> R.string.security_totp_setup_title
    SecurityStep.DISABLE_TOTP -> R.string.security_disable_totp_title
    SecurityStep.SECRETS -> R.string.security_recovery_title
    SecurityStep.CHECK_FACTOR -> R.string.security_two_factor_section
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SecuritySettingsPreview() {
    KIRAKIRATheme {
        SecuritySettingsScreen(
            state = SecuritySettingsState(),
            email = null,
            sessionBusy = false,
            onBack = {},
            onLogin = {},
            onRefresh = {},
            onOpen = {},
            onEdit = { _, _ -> },
            onSendCode = {},
            onSubmit = {},
            onFinishCodes = {},
            onCancelDiscard = {},
            onDismissMessage = {},
            onCopied = {},
            onCancelDiscardCredentials = {},
            onDiscardCredentials = {},
        )
    }
}
