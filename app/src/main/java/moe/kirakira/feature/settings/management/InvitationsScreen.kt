@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.management

import android.content.ClipData
import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.foundation.layout.heightIn
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.IconBadgeTone
import moe.kirakira.R
import moe.kirakira.data.settings.Invitation
import moe.kirakira.ui.components.connectedListItemShadow
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun InvitationsPage(model: InvitationsViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by model.invitations.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    InvitationsScreen(state, session.activeProfile != null && !session.isBusy, busy, message, session.revision,
        onBack, onLogin, model::refresh, model::create, model::dismissMessage)
}

@Composable
internal fun InvitationsScreen(
    state: SettingsLoad<List<Invitation>>,
    signedIn: Boolean,
    busy: Boolean,
    message: Int?,
    accountRevision: Long,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable(accountRevision) { mutableIntStateOf(0) }
    var copyMessage by rememberSaveable(accountRevision) { mutableStateOf<Int?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipboardLabel = stringResource(R.string.settings_invitation_code)
    val codes = state.data.orEmpty()
    val unused = codes.count { !it.used }
    val visible = codes.filter { filter == 0 || (if (filter == 1) !it.used else it.used) }
        .sortedWith(compareBy<Invitation> { it.used }.thenByDescending { it.createdAt })
    ManagementFrame(
        title = stringResource(R.string.settings_invitation_code), signedIn = signedIn,
        loading = state.loading, loaded = state.data != null, error = state.error,
        onBack = onBack, onLogin = onLogin, onRefresh = onRefresh, modifier = modifier,
        message = copyMessage ?: message,
        onDismissMessage = { if (copyMessage != null) copyMessage = null else onDismissMessage() },
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().animateContentSize(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.invitation_unused), style = MaterialTheme.typography.titleMedium)
                            Text(if (state.data == null) "—" else unused.toString(), style = MaterialTheme.typography.displayLarge)
                        }
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = MaterialShapes.Cookie12Sided.toShape(),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(R.drawable.ic_symbol_confirmation_number),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InvitationStatistic(stringResource(R.string.invitation_total), state.data?.size)
                        InvitationStatistic(stringResource(R.string.invitation_used), state.data?.let { it.size - unused })
                    }
                    ShadowButton(
                        onClick = onCreate,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight, hasStartIcon = true),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                        ),
                    ) {
                        Icon(painterResource(R.drawable.ic_symbol_add), null,
                            Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                        Text(stringResource(R.string.invitation_generate),
                            style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                    }
                    if (busy) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            IndeterminateCircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(R.string.invitation_all, R.string.invitation_unused, R.string.invitation_used).forEachIndexed { index, title ->
                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(stringResource(title)) })
                }
            }
        }
        if (state.data != null && visible.isEmpty()) item {
            ManagementEmpty(R.drawable.ic_symbol_confirmation_number, stringResource(R.string.invitation_empty))
        }
        itemsIndexed(visible, key = { _, code -> code.code }) { index, invitation ->
            SegmentedListItem(
                shapes = connectedListItemShapes(index, visible.size),
                modifier = Modifier.connectedListItemShadow(index, visible.size),
                leadingContent = {
                    ManagementIcon(
                        if (invitation.used) R.drawable.ic_symbol_check else R.drawable.ic_symbol_confirmation_number,
                        shape = (if (invitation.used) MaterialShapes.Circle else MaterialShapes.Cookie9Sided).toShape(),
                        tone = if (invitation.used) IconBadgeTone.NEUTRAL else IconBadgeTone.PRIMARY,
                    )
                },
                supportingContent = {
                    Text(stringResource(if (invitation.used) R.string.invitation_used else R.string.invitation_unused),
                        color = if (invitation.used) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    IconButton(onClick = {
                        scope.launch {
                            copyMessage = try {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipboardLabel, invitation.code)))
                                R.string.invitation_copied
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                R.string.invitation_copy_failed
                            }
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_symbol_content_copy), stringResource(R.string.invitation_copy_named, invitation.code), Modifier.size(24.dp))
                    }
                },
                content = { Text(invitation.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium) },
            )
        }
    }
}

@Composable
private fun InvitationStatistic(label: String, count: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(count?.toString() ?: "—", style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(name = "Invitations · 中文", locale = "zh")
@Preview(name = "Invitations · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Invitations · Large text", fontScale = 2f)
@Composable
private fun InvitationsPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        InvitationsScreen(SettingsLoad(emptyList()), true, false, null, 0,
            {}, {}, {}, {}, {})
    }
}
