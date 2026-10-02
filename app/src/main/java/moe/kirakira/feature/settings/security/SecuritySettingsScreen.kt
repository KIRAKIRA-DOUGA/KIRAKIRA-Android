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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import moe.kirakira.ui.components.ShadowFilledTonalButton
import moe.kirakira.feature.settings.SettingsDefaults
import moe.kirakira.feature.settings.SettingsErrorCard
import moe.kirakira.feature.settings.SettingsFormCard
import moe.kirakira.feature.settings.SettingsItem
import moe.kirakira.feature.settings.SettingsPrimaryButton
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.feature.settings.SettingsSection
import moe.kirakira.feature.settings.settingsDestructiveButtonColors
import moe.kirakira.ui.components.IconBadge
import moe.kirakira.ui.components.IconBadgeTone
import moe.kirakira.R
import moe.kirakira.core.credentials.passwordCredentialGateway
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.connectedListItemShadow
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.semanticColors

@Composable
internal fun SecuritySettingsPage(
    model: SecuritySettingsViewModel,
    isActive: Boolean,
    onBack: () -> Unit,
    onLogin: (String) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val autofill = LocalAutofillManager.current
    val language = if (LocalConfiguration.current.locales[0].language == "zh") "zh-Hans-CN" else "en-US"
    LaunchedEffect(isActive) {
        if (!isActive) { autofill?.cancel(); model.deactivate() }
    }
    LaunchedEffect(state.step) { autofill?.cancel() }
    LaunchedEffect(state.loginEmail, isActive) {
        val email = state.loginEmail ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        autofill?.cancel()
        passwordCredentialGateway(context).clearSession()
        onLogin(email)
        model.consumeLogin()
    }
    DisposableEffect(Unit) { onDispose { autofill?.cancel() } }
    SecuritySettingsScreen(
        state = if (state.revision == session.revision) state else SecuritySettingsState(),
        email = session.activeProfile?.email,
        sessionBusy = session.isLoading || session.isBusy,
        onBack = { autofill?.cancel(); if (model.requestBack()) onBack() },
        onLogin = { onLogin("") },
        onRefresh = model::refresh,
        onOpen = { autofill?.cancel(); model.open(it) },
        onEdit = model::edit,
        onSendCode = { model.sendCode(language, it) },
        onSubmit = { autofill?.cancel(); model.submit() },
        onFinishCodes = model::finishCodes,
        onCancelDiscard = model::cancelDiscardCodes,
        onDismissMessage = model::dismissMessage,
        onCopied = model::copied,
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
    modifier: Modifier = Modifier,
) {
    val direction = LocalLayoutDirection.current
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    val fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(message) {
        if (message != null) { snackbar.showSnackbar(message); onDismissMessage() }
    }
    SettingsScaffold(
        title = stringResource(state.step.title()),
        onBack = onBack,
        modifier = modifier,
        imePadding = true,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        ContentPullToRefresh(
            isRefreshing = state.loading && state.status != null,
            onRefresh = onRefresh,
            enabled = email != null && !sessionBusy && !state.busy && state.step == SecurityStep.OVERVIEW,
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            indicatorTopPadding = padding.calculateTopPadding(),
        ) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = { fadeIn(fadeInSpec) togetherWith fadeOut(fadeOutSpec) },
                label = "security_step",
            ) { step ->
                // Only the active step can expose credentials during a transition.
                val visible = step == state.step
                key(step) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(
                            Modifier.widthIn(max = SettingsDefaults.MaxContentWidth).fillMaxSize()
                                .verticalScroll(rememberScrollState()).padding(
                                    start = padding.calculateStartPadding(direction) + SettingsDefaults.HorizontalPadding,
                                    end = padding.calculateEndPadding(direction) + SettingsDefaults.HorizontalPadding,
                                    top = padding.calculateTopPadding() + SettingsDefaults.TopPadding,
                                    bottom = padding.calculateBottomPadding() + SettingsDefaults.BottomPadding,
                                ),
                            verticalArrangement = Arrangement.spacedBy(SettingsDefaults.SectionSpacing),
                        ) {
                            when {
                                !visible -> Unit
                                sessionBusy || (state.loading && state.status == null) -> {
                                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                        IndeterminateCircularProgressIndicator()
                                    }
                                }
                                email == null -> ContentUnavailableView(
                                    state = ContentUnavailableState.EMPTY,
                                    title = stringResource(R.string.security_sign_in),
                                    description = null, iconRes = R.drawable.ic_symbol_shield,
                                    presentation = ContentUnavailablePresentation.INLINE,
                                    primaryAction = ContentUnavailableAction(stringResource(R.string.management_login), onLogin),
                                )
                                state.status == null -> ContentUnavailableView(
                                    state = ContentUnavailableState.ERROR,
                                    description = state.error?.let { stringResource(it.messageRes()) },
                                    presentation = ContentUnavailablePresentation.INLINE, onRetry = onRefresh,
                                )
                                step == SecurityStep.OVERVIEW -> {
                                    SecurityOverview(state, email, onOpen)
                                    state.error?.let { SettingsErrorCard(stringResource(it.messageRes())) }
                                }
                                else -> SecurityFlow(state, email, onEdit, onSendCode, onSubmit, onFinishCodes, onCopied)
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.confirmDiscardCodes) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            title = { Text(stringResource(R.string.security_leave_codes_title)) },
            text = { Text(stringResource(R.string.security_leave_codes_message)) },
            confirmButton = { TextButton(onClick = onFinishCodes) { Text(stringResource(R.string.privacy_discard)) } },
            dismissButton = { TextButton(onClick = onCancelDiscard) { Text(stringResource(R.string.profile_keep_editing)) } },
        )
    }
}

@Composable
private fun SecurityOverview(state: SecuritySettingsState, email: String, onOpen: (SecurityStep) -> Unit) {
    val factor = state.status?.factor ?: return
    val protected = factor != SecondFactor.NONE
    val colors = MaterialTheme.semanticColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.success,
        contentColor = colors.onSuccess,
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = MaterialShapes.Cookie12Sided.toShape(),
                color = colors.onSuccess.copy(alpha = 0.12f),
                contentColor = colors.onSuccess,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(if (protected) R.drawable.ic_symbol_shield else R.drawable.ic_symbol_error),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(if (protected) R.string.security_protected else R.string.security_unprotected),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(stringResource(factor.label()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    SettingsSection(stringResource(R.string.security_account_section)) {
        SecurityItem(R.drawable.ic_symbol_mail, stringResource(R.string.auth_email), email, 0, 2,
            enabled = !state.working, onClick = { onOpen(SecurityStep.EMAIL) })
        SecurityItem(R.drawable.ic_symbol_lock_reset, stringResource(R.string.security_password),
            stringResource(R.string.security_change_password), 1, 2, enabled = !state.working,
            onClick = { onOpen(SecurityStep.PASSWORD) })
    }
    SettingsSection(stringResource(R.string.security_two_factor_section)) {
        SecurityItem(R.drawable.ic_symbol_mail, stringResource(R.string.security_email_authenticator),
            if (factor == SecondFactor.EMAIL) email else stringResource(
                if (factor == SecondFactor.TOTP) R.string.security_disable_current_first else R.string.security_not_enabled),
            0, 2, enabled = !state.working && factor != SecondFactor.TOTP,
            active = factor == SecondFactor.EMAIL,
            onClick = { onOpen(if (factor == SecondFactor.EMAIL) SecurityStep.DISABLE_EMAIL else SecurityStep.ENABLE_EMAIL) })
        SecurityItem(R.drawable.ic_symbol_shield, stringResource(R.string.security_totp_authenticator),
            state.status.totpCreationDateTime?.takeIf { factor == SecondFactor.TOTP }?.let {
                stringResource(R.string.security_added_date, DateFormat.getDateInstance().format(Date(it)))
            } ?: stringResource(if (factor == SecondFactor.TOTP) R.string.security_enabled
                else if (factor == SecondFactor.EMAIL) R.string.security_disable_current_first else R.string.security_not_enabled),
            1, 2, enabled = !state.working && factor != SecondFactor.EMAIL,
            active = factor == SecondFactor.TOTP,
            onClick = { onOpen(if (factor == SecondFactor.TOTP) SecurityStep.DISABLE_TOTP else SecurityStep.TOTP_SETUP) })
    }
}

@Composable
private fun SecurityItem(icon: Int, title: String, summary: String, index: Int, count: Int,
    enabled: Boolean, onClick: () -> Unit, active: Boolean = false) {
    SegmentedListItem(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().connectedListItemShadow(index, count),
        shapes = connectedListItemShapes(index, count),
        leadingContent = {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        },
        supportingContent = { Text(summary) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (active) Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text(stringResource(R.string.security_enabled))
                }
                Icon(painterResource(R.drawable.ic_symbol_chevron_right), null, Modifier.size(24.dp))
            }
        },
        content = { Text(title) },
    )
}

@Composable
private fun ExpressiveSecurityIcon(icon: Int, tertiary: Boolean = false, size: Int = 48, enabled: Boolean = true) {
    IconBadge(
        icon = icon,
        shape = (if (tertiary) MaterialShapes.Cookie9Sided else MaterialShapes.Sunny).toShape(),
        tone = if (tertiary) IconBadgeTone.TERTIARY else IconBadgeTone.PRIMARY,
        size = size.dp,
        enabled = enabled,
    )
}

/** Large centered step illustration. */
@Composable
private fun SecurityStepIcon(icon: Int, tertiary: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        ExpressiveSecurityIcon(icon, tertiary, size = 96)
    }
}

@Composable
private fun SecurityFlow(
    state: SecuritySettingsState, email: String,
    onEdit: (SecurityField, String) -> Unit, onSend: (Boolean) -> Unit,
    onSubmit: () -> Unit, onFinish: () -> Unit, onCopied: (Boolean) -> Unit,
) {
    val editable = !state.working && !state.completionPending
    if (state.step == SecurityStep.TOTP_SETUP || state.step == SecurityStep.SECRETS) {
        val step = if (state.step == SecurityStep.SECRETS) 3 else if (state.setup != null) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.auth_step_progress, step, 3), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { step / 3f }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (state.completionPending) {
        Text(stringResource(R.string.security_completion_pending), style = MaterialTheme.typography.bodyLarge)
    } else when (state.step) {
        SecurityStep.EMAIL -> {
            SecurityCurrentEmail(email)
            SettingsFormCard {
                SecurityTextField(state, SecurityField.EMAIL, R.string.security_new_email, onEdit, KeyboardType.Email)
                SecurityTextField(state, SecurityField.PASSWORD, R.string.security_password, onEdit, KeyboardType.Password)
            }
            SettingsFormCard {
                SecurityCodeField(state, onEdit, onSend)
                SecurityCodeField(state, onEdit, onSend, newEmail = true)
            }
        }
        SecurityStep.PASSWORD -> {
            SettingsFormCard {
                SecurityTextField(state, SecurityField.PASSWORD, R.string.security_old_password, onEdit, KeyboardType.Password)
                SecurityTextField(state, SecurityField.NEW_PASSWORD, R.string.auth_new_password, onEdit, KeyboardType.Password)
                SecurityTextField(state, SecurityField.CONFIRM_PASSWORD, R.string.auth_confirm_password, onEdit, KeyboardType.Password)
            }
            SettingsFormCard { SecurityCodeField(state, onEdit, onSend) }
        }
        SecurityStep.ENABLE_EMAIL -> {
            SecurityStepIcon(R.drawable.ic_symbol_mail)
            Text(email, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
        SecurityStep.DISABLE_EMAIL, SecurityStep.DISABLE_TOTP -> {
            SettingsErrorCard(stringResource(R.string.security_disable_warning))
            SettingsFormCard {
                SecurityTextField(state, SecurityField.PASSWORD, R.string.security_password, onEdit, KeyboardType.Password)
                SecurityCodeField(state, onEdit, onSend)
            }
        }
        SecurityStep.TOTP_SETUP -> {
            val setup = state.setup
            if (setup == null) SecurityStepIcon(R.drawable.ic_symbol_shield, tertiary = true)
            else TotpSetupContent(state, onEdit, onCopied)
        }
        SecurityStep.SECRETS -> RecoveryCodes(state, onCopied)
        SecurityStep.OVERVIEW -> Unit
    }
    state.validation?.let { SettingsErrorCard(stringResource(it)) }
    state.error?.let { SettingsErrorCard(stringResource(it.messageRes())) }
    val destructive = state.step == SecurityStep.DISABLE_EMAIL || state.step == SecurityStep.DISABLE_TOTP
    SettingsPrimaryButton(
        label = stringResource(when {
            state.busy -> R.string.auth_working
            state.completionPending -> R.string.profile_finish_save
            state.step == SecurityStep.SECRETS -> R.string.security_done
            destructive -> R.string.security_disable
            state.step == SecurityStep.ENABLE_EMAIL -> R.string.security_enable
            state.step == SecurityStep.TOTP_SETUP && state.setup == null -> R.string.auth_continue
            state.step == SecurityStep.TOTP_SETUP -> R.string.security_confirm
            else -> R.string.security_save
        }),
        onClick = if (state.step == SecurityStep.SECRETS) onFinish else onSubmit,
        icon = if (destructive) R.drawable.ic_symbol_delete else R.drawable.ic_symbol_check,
        enabled = editable || (!state.working && state.completionPending),
        busy = state.busy,
        colors = if (destructive) settingsDestructiveButtonColors() else ButtonDefaults.buttonColors(),
    )
}

/** Current address shown above the change-email form. */
@Composable
private fun SecurityCurrentEmail(email: String) {
    SettingsItem(
        title = email,
        index = 0,
        count = 1,
        icon = R.drawable.ic_symbol_mail,
        supporting = stringResource(R.string.auth_email),
    )
}

@Composable
private fun SecurityTextField(state: SecuritySettingsState, field: SecurityField, label: Int,
    onEdit: (SecurityField, String) -> Unit, keyboardType: KeyboardType = KeyboardType.Text) {
    var visible by remember { mutableStateOf(false) }
    val password = keyboardType == KeyboardType.Password
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = state.form[field], onValueChange = { onEdit(field, it) },
        modifier = Modifier.fillMaxWidth().then(if (password) Modifier.semantics {
            contentType = if (field == SecurityField.PASSWORD) ContentType.Password else ContentType.NewPassword
        } else Modifier),
        label = { Text(stringResource(label)) }, enabled = !state.working && !state.completionPending,
        leadingIcon = { Icon(painterResource(field.iconRes(state)), contentDescription = null) },
        shape = MaterialTheme.shapes.large, singleLine = true,
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
        trailingIcon = if (password) ({
            IconButton(onClick = { visible = !visible }) {
                Icon(painterResource(if (visible) R.drawable.ic_symbol_visibility_off else R.drawable.ic_symbol_visibility),
                    stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password))
            }
        }) else null,
    )
}

@Composable
private fun SecurityCodeField(state: SecuritySettingsState, onEdit: (SecurityField, String) -> Unit,
    onSend: (Boolean) -> Unit, newEmail: Boolean = false) {
    val totp = !newEmail && state.status?.factor == SecondFactor.TOTP
    val backupAllowed = state.step == SecurityStep.DISABLE_TOTP
    val label = when {
        newEmail -> R.string.security_new_email_code
        totp && backupAllowed -> R.string.security_totp_or_backup
        totp -> R.string.security_totp_code
        state.step == SecurityStep.EMAIL -> R.string.security_current_code
        else -> R.string.auth_verification_code
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SecurityTextField(state, if (newEmail) SecurityField.NEW_EMAIL_CODE else SecurityField.CODE, label, onEdit,
            if (backupAllowed) KeyboardType.Ascii else KeyboardType.Number)
        if (!totp) {
            val cooldown = if (newEmail) state.newCooldown else state.currentCooldown
            TextButton(onClick = { onSend(newEmail) }, enabled = !state.working && cooldown == 0 &&
                (!newEmail || state.form[SecurityField.EMAIL].isNotBlank()), modifier = Modifier.align(Alignment.End)) {
                Text(if (cooldown > 0) stringResource(R.string.auth_resend_countdown, cooldown)
                    else stringResource(R.string.auth_resend))
            }
        }
    }
}

@Composable
private fun TotpSetupContent(state: SecuritySettingsState, onEdit: (SecurityField, String) -> Unit, onCopied: (Boolean) -> Unit) {
    val setup = state.setup ?: return
    val context = LocalContext.current
    val clipboard = remember { SensitiveClipboard(context) }
    DisposableEffect(clipboard) { onDispose { clipboard.clear() } }
    val bitmap by produceState<Bitmap?>(null, setup.uri) {
        value = withContext(Dispatchers.Default) { qrBitmap(setup.uri) }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(232.dp).background(Color.White, MaterialTheme.shapes.large).padding(8.dp),
            contentAlignment = Alignment.Center) {
            val image = bitmap
            if (image != null) Image(image.asImageBitmap(), stringResource(R.string.security_qr_code), Modifier.fillMaxSize())
            else Text(stringResource(R.string.security_qr_failed), color = Color.Black)
        }
        OutlinedButton(onClick = {
            val opened = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(setup.uri))); true }.getOrDefault(false)
            if (!opened) onCopied(false)
        }, enabled = !state.working) { Text(stringResource(R.string.security_open_authenticator)) }
        SecretCard(stringResource(R.string.security_setup_key)) {
            Text(setup.secret.chunked(4).joinToString(" "), Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        }
        CopyButton(stringResource(R.string.security_copy_key)) { onCopied(clipboard.copy(setup.secret)) }
    }
    SettingsFormCard {
        SecurityTextField(state, SecurityField.CODE, R.string.security_totp_code, onEdit, KeyboardType.Number)
    }
}

@Composable
private fun RecoveryCodes(state: SecuritySettingsState, onCopied: (Boolean) -> Unit) {
    val secrets = state.secrets ?: return
    val context = LocalContext.current
    val clipboard = remember { SensitiveClipboard(context) }
    DisposableEffect(clipboard) { onDispose { clipboard.clear() } }
    SecurityStepIcon(R.drawable.ic_symbol_check, tertiary = true)
    SecretCard(stringResource(R.string.security_backup_codes)) {
        secrets.backupCodes.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach {
                    Text(it, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    SecretCard(stringResource(R.string.security_recovery_code)) {
        Text(secrets.recoveryCode.chunked(4).joinToString(" "), Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
    }
    CopyButton(stringResource(R.string.security_copy_codes)) {
        onCopied(clipboard.copy(secrets.backupCodes.joinToString("\n") + "\n\n" + secrets.recoveryCode))
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
private fun CopyButton(label: String, onClick: () -> Unit) {
    ShadowFilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
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

private fun SecurityField.iconRes(state: SecuritySettingsState): Int = when (this) {
    SecurityField.EMAIL, SecurityField.NEW_EMAIL_CODE -> R.drawable.ic_symbol_mail
    SecurityField.CODE -> if (state.status?.factor == SecondFactor.TOTP || state.step == SecurityStep.TOTP_SETUP)
        R.drawable.ic_symbol_shield else R.drawable.ic_symbol_mail
    SecurityField.PASSWORD -> R.drawable.ic_symbol_lock
    SecurityField.NEW_PASSWORD, SecurityField.CONFIRM_PASSWORD -> R.drawable.ic_symbol_lock_reset
}

private fun SecondFactor.label(): Int = when (this) {
    SecondFactor.NONE -> R.string.security_not_enabled
    SecondFactor.EMAIL -> R.string.security_email_authenticator
    SecondFactor.TOTP -> R.string.security_totp_authenticator
}

private fun SecurityStep.title(): Int = when (this) {
    SecurityStep.OVERVIEW -> R.string.settings_security
    SecurityStep.EMAIL -> R.string.security_change_email
    SecurityStep.PASSWORD -> R.string.security_change_password
    SecurityStep.ENABLE_EMAIL -> R.string.security_enable_email_title
    SecurityStep.DISABLE_EMAIL -> R.string.security_disable_email_title
    SecurityStep.TOTP_SETUP -> R.string.security_totp_setup_title
    SecurityStep.DISABLE_TOTP -> R.string.security_disable_totp_title
    SecurityStep.SECRETS -> R.string.security_recovery_title
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SecuritySettingsPreview() {
    KIRAKIRATheme {
        SecuritySettingsScreen(SecuritySettingsState(), null, false, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
    }
}
