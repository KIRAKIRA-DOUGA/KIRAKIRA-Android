@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.management

import android.content.ClipData
import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import moe.kirakira.R
import moe.kirakira.data.settings.Invitation
import moe.kirakira.ui.components.AnimatedNumberText
import moe.kirakira.ui.components.ConnectedLazyColumn
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.IconBadgeTone
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.cardColorShadow
import moe.kirakira.ui.components.connectedListItemShapes
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
    key(accountRevision) {
        val pagerState = rememberPagerState { 3 }
        val listStates = List(3) { rememberLazyListState() }
        var copyMessage by rememberSaveable { mutableStateOf<Int?>(null) }
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val clipboardLabel = stringResource(R.string.settings_invitation_code)
        val codes = state.data.orEmpty()
        InvitationsScaffold(
            pagerState = pagerState,
            signedIn = signedIn,
            loading = state.loading,
            loaded = state.data != null,
            message = copyMessage ?: message,
            onBack = onBack,
            onLogin = onLogin,
            onRefresh = onRefresh,
            onDismissMessage = { if (copyMessage != null) copyMessage = null else onDismissMessage() },
            modifier = modifier,
            onTabReselect = { page -> listStates[page].animateScrollToItem(0) },
            header = { InvitationStatistics(state, busy, accountRevision, onCreate) },
        ) { page, padding ->
            val visible = codes.filter { page == 0 || (if (page == 1) !it.used else it.used) }
                .sortedWith(compareBy<Invitation> { it.used }.thenByDescending { it.createdAt })
            InvitationList(
                listState = listStates[page],
                invitations = visible,
                loaded = state.data != null,
                error = state.error,
                contentPadding = padding,
                onRefresh = onRefresh,
                onCopy = { invitation ->
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
                },
            )
        }
    }
}

@Composable
private fun InvitationStatistics(
    state: SettingsLoad<List<Invitation>>,
    busy: Boolean,
    accountRevision: Long,
    onCreate: () -> Unit,
) {
    val unused = state.data.orEmpty().count { !it.used }
    val shape = MaterialTheme.shapes.extraLarge
    val container = MaterialTheme.colorScheme.primary
    Surface(
        shape = shape, color = container,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .cardColorShadow(shape, container)
            .animateContentSize(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.invitation_unused), style = MaterialTheme.typography.titleMedium)
                    if (state.data == null) Text("—", style = MaterialTheme.typography.displayLarge)
                    else key(accountRevision) {
                        AnimatedNumberText(unused, style = MaterialTheme.typography.displayLarge)
                    }
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
                InvitationStatistic(stringResource(R.string.invitation_total), state.data?.size, accountRevision)
                InvitationStatistic(stringResource(R.string.invitation_used), state.data?.let { it.size - unused }, accountRevision)
            }
            Button(
                onClick = onCreate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight, hasStartIcon = true),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                ),
                elevation = null,
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

@Composable
private fun InvitationList(
    listState: LazyListState,
    invitations: List<Invitation>,
    loaded: Boolean,
    error: Int?,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onCopy: (Invitation) -> Unit,
) {
    ConnectedLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        if (loaded && invitations.isEmpty()) item {
            ManagementEmpty(R.drawable.ic_symbol_confirmation_number, stringResource(R.string.invitation_empty))
        }
        connectedItemsIndexed("invitations", invitations, key = { _, code -> code.code }) { index, invitation ->
            SegmentedListItem(
                shapes = connectedListItemShapes(index, invitations.size),
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
                    IconButton(onClick = { onCopy(invitation) }) {
                        Icon(painterResource(R.drawable.ic_symbol_content_copy), stringResource(R.string.invitation_copy_named, invitation.code), Modifier.size(24.dp))
                    }
                },
                content = { Text(invitation.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium) },
            )
        }
        if (error != null) item {
            ContentUnavailableView(
                state = ContentUnavailableState.ERROR,
                modifier = Modifier.padding(top = 8.dp),
                description = stringResource(error),
                onRetry = onRefresh,
                presentation = ContentUnavailablePresentation.INLINE,
            )
        }
    }
}

@Composable
private fun InvitationStatistic(label: String, count: Int?, accountRevision: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (count == null) Text("—", style = MaterialTheme.typography.headlineSmall)
        else key(accountRevision) {
            AnimatedNumberText(count, style = MaterialTheme.typography.headlineSmall)
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Preview(name = "Invitations · 中文", locale = "zh")
@Preview(name = "Invitations · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Invitations · Large text", fontScale = 2f)
@Composable
private fun InvitationsPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        InvitationsScreen(
            state = SettingsLoad(emptyList()),
            signedIn = true,
            busy = false,
            message = null,
            accountRevision = 0,
            onBack = {},
            onLogin = {},
            onRefresh = {},
            onCreate = {},
            onDismissMessage = {},
        )
    }
}
