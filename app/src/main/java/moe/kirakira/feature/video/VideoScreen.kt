package moe.kirakira.feature.video

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.content.DanmakuStyle
import moe.kirakira.data.content.DanmakuEntry
import moe.kirakira.data.content.PublicProfile
import moe.kirakira.data.content.Reaction
import moe.kirakira.data.content.VideoComment
import moe.kirakira.data.content.VideoDetail
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.PagerTabIndicator
import moe.kirakira.ui.components.rememberTabChangeHandler

internal enum class VideoTab { INTRODUCTION, COMMENTS, DANMAKU }

internal data class VideoUiState(
    val detail: ContentState<VideoDetail>,
    val comments: CommentListState,
    val danmaku: ContentState<List<DanmakuEntry>>,
    val busy: Boolean,
    val signedIn: Boolean,
    val sessionRevision: Long,
    val commentDraft: String,
    val danmakuDraft: String,
    val posted: VideoComment?,
    val danmakuStyle: DanmakuStyle = DanmakuStyle(),
)

@Composable
internal fun VideoScreen(
    state: VideoUiState,
    onRetry: () -> Unit,
    onFollow: () -> Unit,
    onVote: (Reaction) -> Unit,
    onCommentVote: (VideoComment, Reaction) -> Unit,
    onCommentsPage: (Int) -> Unit,
    onCommentsPageRefresh: () -> Unit,
    onCommentsRetry: () -> Unit,
    onCommentsAdjacent: (Boolean, Boolean) -> Unit,
    onCommentLocationConsumed: (Long) -> Unit,
    onLoadTab: (VideoTab) -> Unit,
    onCommentDraft: (String) -> Unit,
    onDanmakuDraft: (String) -> Unit,
    onSendComment: () -> Unit,
    onSendDanmaku: () -> Unit,
    onRefreshDanmaku: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onUnavailable: () -> Unit,
    onLogin: () -> Unit,
    bottomPadding: Dp,
    selectedPart: Int,
    onSelectPart: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDanmakuStyle: (DanmakuStyle) -> Unit = {},
    commentComposer: ComposerState = remember { ComposerState() },
    danmakuComposer: ComposerState = remember { ComposerState() },
    recentKaomoji: List<String> = emptyList(),
    onKaomojiInserted: (String) -> Unit = {},
    isActive: Boolean = true,
) {
    val pager = rememberPagerState(pageCount = { VideoTab.entries.size })
    val changeTab = rememberTabChangeHandler(pager)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(pager.currentPage) {
        commentComposer.panelOpen = false
        danmakuComposer.panelOpen = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    val detail = state.detail.data
    LaunchedEffect(pager.currentPage, state.sessionRevision) { onLoadTab(VideoTab.entries[pager.currentPage]) }
    Column(modifier) {
        PrimaryTabRow(
            selectedTabIndex = pager.currentPage,
            indicator = { PagerTabIndicator(pager) },
            divider = {},
        ) {
            VideoTab.entries.forEach { tab ->
                Tab(selected = pager.currentPage == tab.ordinal, onClick = { changeTab(tab.ordinal) },
                    text = { Text(stringResource(when (tab) {
                        VideoTab.INTRODUCTION -> R.string.video_tab_introduction
                        VideoTab.COMMENTS -> R.string.video_tab_comments
                        VideoTab.DANMAKU -> R.string.video_tab_danmaku
                    })) })
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            when (VideoTab.entries[page]) {
                VideoTab.INTRODUCTION -> ContentPullToRefresh(
                    isRefreshing = state.detail.loading && detail != null,
                    onRefresh = onRetry,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val statusHeight = (maxHeight - bottomPadding - 48.dp).coerceAtLeast(0.dp)
                        LazyColumn(contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, bottomPadding + 24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
                            if (state.detail.error != null || detail == null) {
                                item("status") {
                                    ContentStatus(
                                        state.detail, onRetry,
                                        Modifier.fillMaxWidth().then(if (detail == null) Modifier.heightIn(min = statusHeight) else Modifier),
                                    )
                                }
                            }
                            if (detail != null) {
                                if (detail.parts.size > 1) {
                                    item("parts") {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            itemsIndexed(detail.parts, key = { _, part -> part.id }) { index, part ->
                                                FilterChip(
                                                    selected = selectedPart == index,
                                                    onClick = { onSelectPart(index) },
                                                    label = {
                                                        Text(part.title.ifBlank { stringResource(R.string.player_part, index + 1) })
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                item("author") { VideoAuthor(detail.author, onOpenProfile, onFollow, state.busy || detail.blockedByOther) }
                                item("title") { SelectionContainer { Text(detail.summary.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) } }
                                item("metadata") {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        detail.summary.views?.let {
                                            val count = stringResource(R.string.video_views, it)
                                            VideoMetadata(
                                                R.drawable.ic_symbol_play_circle,
                                                count,
                                                description = stringResource(R.string.video_views_description, count),
                                            )
                                        }
                                        VideoMetadata(R.drawable.ic_symbol_calendar_today, dateText(detail.summary.uploadedAt))
                                        if (detail.category.isNotBlank()) {
                                            VideoMetadata(R.drawable.ic_symbol_category, categoryText(detail.category))
                                        }
                                    }
                                }
                                item("description") { SelectionContainer { Text(detail.description, style = MaterialTheme.typography.bodyLarge) } }
                                item("actions") { VideoActions(detail, state.busy, onVote, onUnavailable) }
                            }
                        }
                    }
                }
                VideoTab.COMMENTS -> Column {
                    if (!state.signedIn) TextButton(onClick = onLogin) { Text(stringResource(R.string.content_login_to_interact)) }
                    VideoCommentsPage(state.comments, state.posted, state.commentDraft, state.busy,
                        detail != null && !detail.blockedByOther, onCommentDraft, onSendComment, onCommentsPage,
                        onCommentVote, onOpenProfile, onUnavailable, onCommentsPageRefresh,
                        onCommentsRetry, onCommentsAdjacent, onCommentLocationConsumed,
                        rememberLazyListState(), bottomPadding,
                        composerState = commentComposer,
                        composerActive = isActive && pager.currentPage == page && !pager.isScrollInProgress,
                        recentKaomoji = recentKaomoji, onKaomojiInserted = onKaomojiInserted)
                }
                VideoTab.DANMAKU -> FloatingComposerLayout(
                    bottomPadding = bottomPadding,
                    composer = { availableHeight ->
                        DanmakuComposer(
                            state.danmakuDraft, state.danmakuStyle, state.sessionRevision,
                            onDanmakuDraft, onSendDanmaku, onDanmakuStyle,
                            enabled = detail != null && !detail.blockedByOther,
                            busy = state.busy,
                            composerState = danmakuComposer,
                            composerActive = isActive && pager.currentPage == page && !pager.isScrollInProgress,
                            availableHeight = availableHeight,
                            recentKaomoji = recentKaomoji, onKaomojiInserted = onKaomojiInserted,
                        )
                    },
                ) { listBottomPadding ->
                    ContentPullToRefresh(
                        isRefreshing = state.danmaku.loading && state.danmaku.data != null,
                        onRefresh = onRefreshDanmaku,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            var headerHeight by remember { mutableIntStateOf(0) }
                            val density = LocalDensity.current
                            val statusHeight = (maxHeight - listBottomPadding - 8.dp -
                                with(density) { headerHeight.toDp() }).coerceAtLeast(0.dp)
                            LazyColumn(modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, listBottomPadding)) {
                                item("count") {
                                    Column(Modifier.onSizeChanged { headerHeight = it.height }) {
                                        VideoListCount(R.plurals.video_danmaku_total, state.danmaku.data?.size ?: 0)
                                        if (!state.signedIn) TextButton(onClick = onLogin) { Text(stringResource(R.string.content_login_to_interact)) }
                                    }
                                }
                                item("status") {
                                    if (state.danmaku.error != null || state.danmaku.data.isNullOrEmpty()) {
                                        ContentStatus(
                                            state.danmaku.copy(loading = state.danmaku.loading && state.danmaku.data == null), onRefreshDanmaku,
                                            Modifier.fillMaxWidth().then(
                                                if (state.danmaku.data.isNullOrEmpty()) Modifier.heightIn(min = statusHeight) else Modifier,
                                            ),
                                            state.danmaku.data.isNullOrEmpty(),
                                            emptyTitle = stringResource(R.string.video_danmaku_empty),
                                            emptyIconRes = R.drawable.ic_custom_danmaku,
                                        )
                                    }
                                }
                                itemsIndexed(state.danmaku.data.orEmpty()) { index, entry ->
                                    DanmakuListItem(durationText((entry.timeSeconds * 1000).toLong()), entry.text, index, state.danmaku.data?.size ?: 0)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoAuthor(
    author: PublicProfile,
    onOpenProfile: (Long) -> Unit,
    onFollow: () -> Unit,
    busy: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackButton = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        val identity: @Composable (Modifier) -> Unit = { modifier ->
            Row(
                modifier = modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onOpenProfile(author.uid) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccountAvatar(author.avatar, size = 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(author.name.ifBlank { stringResource(R.string.content_unknown_author) }, style = MaterialTheme.typography.titleMedium)
                    if (author.username.isNotBlank()) Text(author.username, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (stackButton) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                identity(Modifier.fillMaxWidth())
                if (!author.isSelf) FollowButton(author.following, onFollow, busy)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                identity(Modifier.weight(1f))
                if (!author.isSelf) FollowButton(author.following, onFollow, busy)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FollowButton(following: Boolean, onFollow: () -> Unit, busy: Boolean) {
    ToggleButton(
        checked = following,
        onCheckedChange = { onFollow() },
        enabled = !busy,
        icon = { Icon(painterResource(if (following) R.drawable.ic_symbol_check else R.drawable.ic_symbol_add), null) },
    ) { Text(stringResource(if (following) R.string.video_following else R.string.video_follow)) }
}

@Composable
private fun VideoMetadata(@DrawableRes icon: Int, text: String, description: String? = null) {
    Row(
        modifier = if (description != null) {
            Modifier.clearAndSetSemantics { contentDescription = description }
        } else {
            Modifier
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoActions(
    detail: VideoDetail,
    busy: Boolean,
    onVote: (Reaction) -> Unit,
    onUnavailable: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        ReactionAction(detail.reaction == Reaction.LIKE, R.drawable.ic_symbol_thumb_up, R.string.video_like, detail.upvotes, busy || detail.blockedByOther) { onVote(Reaction.LIKE) }
        ReactionAction(detail.reaction == Reaction.DISLIKE, R.drawable.ic_symbol_thumb_down, R.string.video_dislike, detail.downvotes, busy || detail.blockedByOther) { onVote(Reaction.DISLIKE) }
        ReactionAction(false, R.drawable.ic_symbol_star, R.string.video_save, null, busy = false, onClick = onUnavailable)
        FilledTonalIconButton(onClick = onUnavailable) {
            Icon(painterResource(R.drawable.ic_symbol_download), stringResource(R.string.video_download))
        }
        FilledTonalIconButton(onClick = onUnavailable) {
            Icon(painterResource(R.drawable.ic_symbol_share), stringResource(R.string.video_share))
        }
        FilledTonalIconButton(onClick = onUnavailable) {
            Icon(painterResource(R.drawable.ic_symbol_more_horiz), stringResource(R.string.video_more))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReactionAction(
    checked: Boolean,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    count: Long?,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val description = if (count != null) {
        stringResource(R.string.video_action_count, stringResource(label), count)
    } else {
        stringResource(label)
    }
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        enabled = !busy,
        modifier = Modifier.semantics { contentDescription = description },
        icon = { Icon(painterResource(icon), null) },
    ) {
        Text(count?.let { stringResource(R.string.video_count, it) } ?: "—")
    }
}
