package moe.kirakira.feature.settings.security

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.kirakira.R
import moe.kirakira.data.security.TotpSecrets
import moe.kirakira.data.security.TotpSetup
import moe.kirakira.ui.components.AccountFlowDefaults
import moe.kirakira.ui.components.AccountFlowSection
import moe.kirakira.ui.components.accountFlowCodeTextStyle

@Composable
internal fun TotpSetupContent(
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
        verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.SectionSpacing),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 232.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Color.White, MaterialTheme.shapes.large)
                        .padding(8.dp),
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
                            context.startActivity(Intent(Intent.ACTION_VIEW, setup.uri.toUri()))
                            true
                        }.getOrDefault(false)
                        if (!opened) onCopied(false)
                    },
                    enabled = isActive && !state.working,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.security_open_authenticator))
                }
            }
        }
        SecretCard(
            title = stringResource(R.string.security_setup_key),
            actionContent = {
                CopyButton(stringResource(R.string.security_copy_key), enabled = isActive && !state.working) {
                    onCopied(clipboard.copy(setup.secret))
                }
            },
        ) {
            GroupedSecret(setup.secret)
        }
        AccountFlowSection(title = stringResource(R.string.security_confirm_binding)) {
            SecurityTextField(
                state, SecurityField.CODE, R.string.security_totp_code, onEdit,
                keyboardType = KeyboardType.Number, isActive = isActive, onDone = onSubmit,
            )
        }
    }
}

@Composable
internal fun RecoveryCodes(
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
    val codeStyle = accountFlowCodeTextStyle()
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AccountFlowDefaults.SectionSpacing)) {
        SecretCard(
            title = stringResource(R.string.security_backup_codes),
            supportingContent = {
                Text(
                    text = stringResource(R.string.security_backup_codes_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val widestCode = secrets.backupCodes.maxOfOrNull {
                    textMeasurer.measure(it, style = codeStyle, softWrap = false, maxLines = 1).size.width
                } ?: 0
                val gap = with(density) { 16.dp.roundToPx() }
                val columns = if (widestCode * 2 + gap <= constraints.maxWidth) 2 else 1
                Column {
                    secrets.backupCodes.chunked(columns).forEachIndexed { index, codes ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            codes.forEach { code ->
                                Text(
                                    text = code,
                                    modifier = Modifier.weight(1f),
                                    style = codeStyle,
                                    textAlign = TextAlign.Center,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
        SecretCard(
            title = stringResource(R.string.security_recovery_code),
            supportingContent = {
                Text(
                    text = stringResource(R.string.security_recovery_code_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            actionContent = {
                CopyButton(stringResource(R.string.security_copy_codes), enabled = isActive) {
                    onCopied(clipboard.copy(secrets.backupCodes.joinToString("\n") + "\n\n" + secrets.recoveryCode))
                }
            },
        ) {
            GroupedSecret(secrets.recoveryCode)
        }
    }
}

/** Monospace card for one-time secrets copied through dedicated actions. */
@Composable
private fun SecretCard(
    title: String,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    actionContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                supportingContent?.invoke()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth(), content = content)
            if (actionContent != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { actionContent() }
            }
        }
    }
}

@Composable
private fun GroupedSecret(value: String, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        value.chunked(4).forEach { group ->
            Text(group, style = accountFlowCodeTextStyle(), softWrap = false)
        }
    }
}

@Composable
private fun CopyButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(painterResource(R.drawable.ic_symbol_content_copy), null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
}
