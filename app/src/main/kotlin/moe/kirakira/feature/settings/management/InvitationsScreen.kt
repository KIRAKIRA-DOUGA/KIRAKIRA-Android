@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.feature.settings.management

import android.content.ClipData
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.data.settings.Invitation
import moe.kirakira.ui.components.button.FlatTextButton
import moe.kirakira.ui.components.button.ShadowButton
import moe.kirakira.ui.components.button.ShadowFloatingActionButton
import moe.kirakira.ui.components.icon.IconBadgeTone
import moe.kirakira.ui.components.list.ConnectedLazyColumn
import moe.kirakira.ui.components.list.LazyListScrollbar
import moe.kirakira.ui.components.list.connectedListItemShapes
import moe.kirakira.ui.components.overlay.ActionDialog
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopEffect
import moe.kirakira.ui.components.scaffold.smoothScrollToTop
import moe.kirakira.ui.components.status.ContentStatusViewport
import moe.kirakira.ui.components.status.ContentUnavailablePresentation
import moe.kirakira.ui.components.status.ContentUnavailableState
import moe.kirakira.ui.components.status.ContentUnavailableView
import moe.kirakira.ui.components.text.AnimatedNumberText
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun InvitationsPage(model: InvitationsViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by model.invitations.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val confirmCreation by model.creationConfirmation.collectAsStateWithLifecycle()
    InvitationsScreen(state, session.activeProfile != null && !session.isBusy, busy, message, session.revision,
        onBack, onLogin, model::refresh, model::create, model::dismissMessage)
    if (confirmCreation && session.activeProfile != null && !session.isBusy) {
        InvitationRetryDialog(busy || state.loading, model::dismissCreation, model::refresh, model::confirmCreation)
    }
}

@Composable
private fun InvitationRetryDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onCreate: () -> Unit,
) {
    ActionDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invitation_creation_unconfirmed_title)) },
        text = { Text(stringResource(R.string.invitation_creation_retry_message)) },
        actions = {
            ShadowButton(onClick = { onDismiss(); onCheck() }, enabled = !busy) {
                Text(stringResource(R.string.security_check_status))
            }
            FlatTextButton(onClick = onCreate, enabled = !busy) {
                Text(stringResource(R.string.invitation_generate_again))
            }
        },
    )
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
            onTabReselect = { page -> listStates[page].smoothScrollToTop() },
            header = { InvitationStatistics(state, accountRevision) },
            floatingActionButton = { InvitationFab(busy, onCreate) },
        ) { page, padding, obscuredBottom ->
            StatusBarScrollToTopEffect(listStates[page])
            val visible = codes.filter { page == 0 || (if (page == 1) !it.used else it.used) }
                .sortedWith(compareBy<Invitation> { it.used }.thenByDescending { it.createdAt })
            InvitationList(
                listState = listStates[page],
                invitations = visible,
                loaded = state.data != null,
                error = state.error,
                contentPadding = padding,
                obscuredBottom = obscuredBottom,
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
private fun InvitationStatistics(state: SettingsLoad<List<Invitation>>, accountRevision: Long) {
    val codes = state.data
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 8.dp)) {
        InvitationStatistic(
            stringResource(R.string.invitation_unused), codes?.count { !it.used }, accountRevision,
            Modifier.weight(1f), MaterialTheme.colorScheme.primary,
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InvitationStatistic(stringResource(R.string.invitation_total), codes?.size, accountRevision, Modifier.weight(1f))
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InvitationStatistic(
            stringResource(R.string.invitation_used), codes?.count { it.used }, accountRevision, Modifier.weight(1f),
        )
    }
}

@Composable
private fun InvitationFab(busy: Boolean, onCreate: () -> Unit) {
    ShadowFloatingActionButton(
        onClick = onCreate,
        enabled = !busy,
        // Together with Scaffold's 16dp margins, fit the 12dp elevation shadow at the edges.
        modifier = Modifier.padding(end = 2.dp, bottom = 10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_icon_add), stringResource(R.string.invitation_generate))
    }
}

@Composable
private fun InvitationList(
    listState: LazyListState,
    invitations: List<Invitation>,
    loaded: Boolean,
    error: Int?,
    contentPadding: PaddingValues,
    obscuredBottom: Dp,
    onRefresh: () -> Unit,
    onCopy: (Invitation) -> Unit,
) {
    val showScrollbar by remember(listState) {
        derivedStateOf { listState.canScrollBackward || listState.canScrollForward }
    }
    // The pager is shifted down by the expanded statistics; reserve that same space
    // in both the list viewport and scrollbar so their bottom stays on screen.
    ContentStatusViewport(
        modifier = Modifier.fillMaxSize().padding(bottom = obscuredBottom),
        contentPadding = contentPadding,
    ) { statusModifier ->
        ConnectedLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (loaded && invitations.isEmpty() && error == null) item {
                ManagementEmpty(
                    R.drawable.ic_icon_gift,
                    stringResource(R.string.invitation_empty),
                    statusModifier,
                )
            }
            connectedItemsIndexed("invitations", invitations, key = { _, code -> code.code }) { index, invitation ->
                SegmentedListItem(
                    shapes = connectedListItemShapes(index, invitations.size),
                    leadingContent = {
                        ManagementIcon(
                            if (invitation.used) R.drawable.ic_icon_check else R.drawable.ic_icon_gift,
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
                            Icon(painterResource(R.drawable.ic_icon_copy), stringResource(R.string.invitation_copy_named, invitation.code), Modifier.size(24.dp))
                        }
                    },
                    content = { Text(invitation.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium) },
                )
            }
            if (error != null) item {
                ContentUnavailableView(
                    state = ContentUnavailableState.ERROR,
                    modifier = Modifier.fillMaxWidth().then(
                        if (invitations.isEmpty()) statusModifier
                        else Modifier.padding(top = 8.dp),
                    ),
                    description = stringResource(error),
                    onRetry = onRefresh,
                    presentation = ContentUnavailablePresentation.INLINE,
                )
            }
        }
        if (showScrollbar) {
            LazyListScrollbar(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    )
                    .width(28.dp)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun InvitationStatistic(
    label: String,
    count: Int?,
    accountRevision: Long,
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
) {
    Column(
        modifier.padding(horizontal = 16.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = accent.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val style = MaterialTheme.typography.headlineLarge.copy(color = accent.takeOrElse { MaterialTheme.colorScheme.onSurface })
        if (count == null) Text("—", style = style)
        else key(accountRevision) { AnimatedNumberText(count, style = style) }
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
