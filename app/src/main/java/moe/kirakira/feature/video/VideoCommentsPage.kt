package moe.kirakira.feature.video

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import moe.kirakira.R
import moe.kirakira.data.content.Reaction
import moe.kirakira.data.content.VideoComment
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus

@Composable
internal fun VideoCommentsPage(
    state: CommentListState,
    posted: VideoComment?,
    draft: String,
    busy: Boolean,
    canInteract: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onPage: (Int) -> Unit,
    onVote: (VideoComment, Reaction) -> Unit,
    onOpenProfile: (Long) -> Unit,
    onUnavailable: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onAdjacent: (Boolean, Boolean) -> Unit,
    onLocationConsumed: (Long) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    onLogin: (() -> Unit)? = null,
    composerState: ComposerState = remember { ComposerState() },
    composerActive: Boolean = true,
    recentKaomoji: List<String> = emptyList(),
    onKaomojiInserted: (String) -> Unit = {},
) {
    val entries = remember(state.pages) { state.entries }
    val showLoadingStatus = state.loading && !state.refreshing
    val showPosted = posted != null && entries.none { it.comment.id == posted.id }
    val commentStart = 3 + if (showPosted) 1 else 0
    val totalPages = state.totalPages
    val navigationEnabled = state.pages.isNotEmpty() && !busy
    var showJumpDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(composerActive) {
        if (!composerActive) showJumpDialog = false
    }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    var headerHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val currentPage by remember(entries, state.firstPage) {
        derivedStateOf {
            // visibleItemsInfo also includes the preceding item inside contentPadding.
            // Use the logical scroll anchor so a page-boundary jump cannot report the old page.
            val visibleKeys = listState.layoutInfo.visibleItemsInfo
                .filter { it.index >= listState.firstVisibleItemIndex && it.offset + it.size > 0 }
                .map { it.key }
            entries.firstOrNull { it.comment.id in visibleKeys }?.page ?: state.firstPage
        }
    }
    val atTop by remember { derivedStateOf { !listState.canScrollBackward } }
    var previousFirstPage by remember { mutableStateOf(state.firstPage) }
    // Capture the old layout before LazyColumn receives prepended items, including when
    // a header (rather than a comment) is its first visible key.
    val prependAnchor = remember(state.pages) {
        if (state.firstPage < previousFirstPage && state.location == null) {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                entries.any { it.page >= previousFirstPage && it.comment.id == item.key }
            }
        } else null
    }
    SideEffect {
        if (state.firstPage < previousFirstPage && prependAnchor != null) {
            val index = entries.indexOfFirst { it.comment.id == prependAnchor.key }
            listState.requestScrollToItem(commentStart + index, -prependAnchor.offset)
        }
        previousFirstPage = state.firstPage
    }
    LaunchedEffect(state.location) {
        state.location?.let { location ->
            val index = entries.indexOfFirst { it.page == location.page }
            val targetIndex = if (location.page == 1 || index < 0) 0 else commentStart + index
            val targetKey = if (targetIndex == 0) "count" else entries[index].comment.id
            // Apply the request during the next measure, with the new item provider. Keep
            // adjacent loading paused until that provider has actually placed the target.
            listState.requestScrollToItem(targetIndex)
            snapshotFlow {
                val layout = listState.layoutInfo
                layout.totalItemsCount == commentStart + entries.size + 1 &&
                    layout.visibleItemsInfo.any { it.index == targetIndex && it.key == targetKey } &&
                    (listState.firstVisibleItemIndex == targetIndex || !listState.canScrollForward)
            }.first { it }
            onLocationConsumed(location.request)
        }
    }
    LaunchedEffect(entries, state.loading, state.location, state.previous, state.next, composerActive) {
        if (!composerActive || state.loading || state.location != null || entries.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
            val first = entries.indexOfFirst { it.comment.id in visible }
            val last = entries.indexOfLast { it.comment.id in visible }
            (first in 0..3) to (last >= (entries.size - 4).coerceAtLeast(0))
        }.distinctUntilChanged().collect { (nearStart, nearEnd) ->
            if (nearStart) onAdjacent(true, false)
            if (nearEnd) onAdjacent(false, false)
        }
    }
    FloatingComposerLayout(
        contentPadding = contentPadding,
        topPadding = topPadding,
        composer = { availableHeight ->
            ContentComposer(
                draft, R.string.comment_write, onDraft, onSend,
                enabled = canInteract, busy = busy, maxLength = 19999,
                state = composerState, active = composerActive, availableHeight = availableHeight,
                contentPadding = contentPadding,
                recent = recentKaomoji, onKaomojiInserted = onKaomojiInserted,
                onLogin = onLogin,
            )
        },
        modifier = modifier,
    ) { listBottomPadding ->
        ContentPullToRefresh(
            isRefreshing = state.refreshing,
            enabled = state.firstPage == 1 && atTop && (!state.loading || state.refreshing),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTopPadding = topPadding,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val statusHeight = (maxHeight - topPadding - listBottomPadding - 8.dp -
                    with(density) { headerHeight.toDp() }).coerceAtLeast(0.dp)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = topPadding + 8.dp,
                        bottom = listBottomPadding,
                    ),
                ) {
                    item("count") {
                        Column(Modifier.onSizeChanged { headerHeight = it.height }) {
                            CommentCountHeader(
                                count = state.total,
                                toolbarSize = toolbarSize,
                            )
                        }
                    }
                    item("status") {
                        if (state.error != null || showLoadingStatus || entries.isEmpty()) {
                            ContentStatus(
                                ContentState(data = state.pages.takeIf { it.isNotEmpty() }, loading = showLoadingStatus, error = state.error),
                                onRetry,
                                Modifier.fillMaxWidth().then(
                                    if (entries.isEmpty() && posted == null) Modifier.heightIn(min = statusHeight)
                                    else Modifier,
                                ),
                                empty = entries.isEmpty() && posted == null,
                                emptyTitle = stringResource(R.string.video_comments_empty),
                                emptyIconRes = R.drawable.ic_symbol_chat_bubble,
                            )
                        }
                    }
                    item("previous") {
                        ContentStatus(state.previous, { onAdjacent(true, true) }, Modifier.fillMaxWidth(), empty = false)
                    }
                    if (showPosted) {
                        item("posted") { Text(stringResource(R.string.comment_posted, posted.text)) }
                    }
                    items(entries, key = { entry -> entry.comment.id }) { entry ->
                        val comment = entry.comment
                        VideoCommentItem(
                            comment,
                            vote = when (comment.reaction) {
                                Reaction.LIKE -> 1
                                Reaction.DISLIKE -> -1
                                Reaction.NONE -> 0
                            },
                            enabled = !busy && !state.loading && entry.page !in state.updatingPages && canInteract,
                            onOpenAuthor = { onOpenProfile(comment.author.uid) },
                            onVote = { onVote(comment, if (it == 1) Reaction.LIKE else Reaction.DISLIKE) },
                            onReply = onUnavailable,
                            onMore = onUnavailable,
                        )
                    }
                    item("next") {
                        ContentStatus(state.next, { onAdjacent(false, true) }, Modifier.fillMaxWidth(), empty = false)
                    }
                }
                CommentToolbar(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    enabled = navigationEnabled,
                    onPage = onPage,
                    onOpenJump = { showJumpDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 8.dp, end = 8.dp, top = topPadding + 8.dp, bottom = 8.dp)
                        .onSizeChanged { toolbarSize = it },
                )
            }
        }
    }
    if (showJumpDialog && composerActive) {
        CommentJumpDialog(
            currentPage = currentPage,
            totalPages = totalPages,
            enabled = navigationEnabled,
            onDismiss = { showJumpDialog = false },
            onJump = {
                onPage(it)
                showJumpDialog = false
            },
        )
    }
}

@Composable
private fun CommentCountHeader(count: Int, toolbarSize: IntSize, modifier: Modifier = Modifier) {
    val label = pluralStringResource(R.plurals.video_comments_total, count, count)
    val style = MaterialTheme.typography.bodyMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = with(density) { textMeasurer.measure(label, style).size.width.toDp() }
    val toolbarWidth = with(density) { toolbarSize.width.toDp() }
    val toolbarHeight = with(density) { toolbarSize.height.toDp() }

    BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // Measure the localized label so narrow layouts and large fonts stack without overlap.
        val inline = toolbarSize.width > 0 && labelWidth + toolbarWidth + 24.dp <= maxWidth
        Text(
            text = label,
            modifier = if (inline) {
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = toolbarWidth + 16.dp)
                    .heightIn(min = toolbarHeight)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .semantics { heading() }
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = toolbarHeight + 8.dp, bottom = 8.dp)
                    .semantics { heading() }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = style,
        )
    }
}
