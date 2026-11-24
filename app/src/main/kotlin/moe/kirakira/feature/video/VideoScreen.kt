package moe.kirakira.feature.video

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import moe.kirakira.R
import moe.kirakira.data.content.DanmakuEntry
import moe.kirakira.data.content.DanmakuStyle
import moe.kirakira.data.content.PublicProfile
import moe.kirakira.data.content.Reaction
import moe.kirakira.data.content.VideoComment
import moe.kirakira.data.content.VideoDetail
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.ui.components.list.LazyListScrollbar
import moe.kirakira.ui.components.scaffold.PagerTabRow
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopEffect
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopPauseEffect
import moe.kirakira.ui.components.scaffold.rememberTabChangeHandler
import moe.kirakira.ui.components.scaffold.smoothScrollToTop
import moe.kirakira.ui.components.selection.AppChipDefaults
import moe.kirakira.ui.components.status.ContentPullToRefresh
import moe.kirakira.ui.components.status.ContentStatus
import moe.kirakira.ui.components.status.ContentStatusViewport
import moe.kirakira.ui.components.text.ExpandableText
import moe.kirakira.ui.components.text.LocalNumberAnimationIdentity
import moe.kirakira.ui.components.video.categoryText
import moe.kirakira.ui.components.video.dateText
import moe.kirakira.ui.components.video.durationText
import moe.kirakira.ui.content.ContentMutation
import moe.kirakira.ui.content.ContentState
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorDefaults

internal enum class VideoTab { INTRODUCTION, COMMENTS, DANMAKU }

private val ConversationTabs = listOf(VideoTab.COMMENTS, VideoTab.DANMAKU)

internal data class VideoUiState(
    val detail: ContentState<VideoDetail>,
    val access: VideoAccessState,
    val comments: CommentListState,
    val danmaku: ContentState<List<DanmakuEntry>>,
    val busy: Boolean,
    val signedIn: Boolean,
    val sessionRevision: Long,
    val commentDraft: String,
    val danmakuDraft: String,
    val posted: VideoComment?,
    val danmakuStyle: DanmakuStyle = DanmakuStyle(),
    val mutation: ContentMutation? = null,
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
    onRetryDanmaku: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onUnavailable: () -> Unit,
    onShare: () -> Unit,
    onLogin: () -> Unit,
    contentPadding: PaddingValues,
    onViewCover: () -> Unit,
    selectedPart: Int,
    onSelectPart: (Int) -> Unit,
    onOpenTag: (Long) -> Unit,
    playerHeight: Dp,
    modifier: Modifier = Modifier,
    onDanmakuStyle: (DanmakuStyle) -> Unit = {},
    commentComposer: ComposerState = remember { ComposerState() },
    danmakuComposer: ComposerState = remember { ComposerState() },
    recentKaomoji: List<String> = emptyList(),
    onKaomojiInserted: (String) -> Unit = {},
    isActive: Boolean = true,
    detailPane: IntRect? = null,
    introductionPane: IntRect? = null,
    introductionPadding: PaddingValues = PaddingValues(0.dp),
    wideWindow: Boolean = false,
    aboveTabs: @Composable () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val horizontalPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection),
    )
    val splitIntroduction = introductionPane != null
    val tabs = if (splitIntroduction) ConversationTabs else VideoTab.entries
    var selectedTab by rememberSaveable { mutableStateOf(VideoTab.INTRODUCTION) }
    // Page indices change with the layout; preserve the semantic tab, not its old index.
    val pager = remember(splitIntroduction) {
        PagerState(currentPage = tabs.indexOf(selectedTab).coerceAtLeast(0), pageCount = { tabs.size })
    }
    val kaomojiPanelOpen = commentComposer.panelOpen || danmakuComposer.panelOpen
    val panelNestedScrollConnection = remember { object : NestedScrollConnection {} }
    val pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(pager, Orientation.Horizontal)
    val introductionListState = rememberLazyListState()
    val commentsListState = rememberLazyListState()
    val danmakuListState = rememberLazyListState()
    val currentTab = tabs[pager.currentPage]
    SideEffect { selectedTab = currentTab }
    val activeListState = when (currentTab) {
        VideoTab.INTRODUCTION -> introductionListState
        VideoTab.COMMENTS -> commentsListState
        VideoTab.DANMAKU -> danmakuListState
    }
    val showDanmakuScrollbar by remember(danmakuListState) {
        derivedStateOf { danmakuListState.canScrollBackward || danmakuListState.canScrollForward }
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(currentTab) {
        commentComposer.panelOpen = false
        danmakuComposer.panelOpen = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }
    val detail = state.detail.data
    var descriptionExpanded by rememberSaveable(detail?.summary?.id) { mutableStateOf(false) }
    val accessBlocked = state.access.unavailable || state.access.playbackInvalidationPending
    val checkingAccess = state.access.unavailable && state.detail.loading
    val emptyCommentPages = remember { CommentPages() }
    val visibleComments = if (accessBlocked) {
        state.comments.copy(
            content = emptyCommentPages,
            total = 0,
            previous = ContentState(),
            next = ContentState(),
            location = null,
            updatingPages = emptySet(),
        )
    } else state.comments
    val commentsRefreshing = checkingAccess && state.access.refreshingTab == VideoTab.COMMENTS
    val comments = if (checkingAccess) visibleComments.copy(
        loading = true,
        refreshing = commentsRefreshing,
        error = visibleComments.error.takeIf { commentsRefreshing },
    ) else visibleComments
    val commentTopNeedsPageJump = comments.pages.isNotEmpty() && (
        comments.firstPage > 1 || (comments.location?.page ?: 1) > 1 || (comments.loading && !comments.refreshing)
    )
    val commentsGoingToFirstPage = comments.refreshing || comments.location?.page == 1
    suspend fun scrollCommentsToTop() {
        if (commentsGoingToFirstPage) return
        if (commentTopNeedsPageJump) {
            // An explicit jump supersedes edge loading and uses the new page's list layout.
            onCommentsPage(1)
        } else {
            commentsListState.smoothScrollToTop()
        }
    }
    val statusBarScrollEnabled = isActive && !kaomojiPanelOpen && !pager.isScrollInProgress
    if (currentTab == VideoTab.COMMENTS) {
        val dragging by commentsListState.interactionSource.collectIsDraggedAsState()
        StatusBarScrollToTopEffect(
            identity = commentsListState,
            canScrollToTop = {
                !commentsGoingToFirstPage && (commentTopNeedsPageJump || commentsListState.canScrollBackward)
            },
            enabled = statusBarScrollEnabled && !dragging,
            onScrollToTop = ::scrollCommentsToTop,
        )
    } else {
        StatusBarScrollToTopEffect(activeListState, enabled = statusBarScrollEnabled)
    }
    StatusBarScrollToTopPauseEffect(paused = isActive && kaomojiPanelOpen)
    val changeTab = key(pager) {
        rememberTabChangeHandler(pager) { page ->
            when (tabs[page]) {
                VideoTab.INTRODUCTION -> introductionListState.smoothScrollToTop()
                VideoTab.COMMENTS -> scrollCommentsToTop()
                VideoTab.DANMAKU -> danmakuListState.smoothScrollToTop()
            }
        }
    }
    val posted = state.posted.takeUnless { accessBlocked }
    val danmaku = if (accessBlocked) state.danmaku.copy(data = null) else state.danmaku
    val danmakuRefreshing = checkingAccess && state.access.refreshingTab == VideoTab.DANMAKU
    val danmakuStatus = if (checkingAccess) {
        danmaku.copy(loading = !danmakuRefreshing, error = danmaku.error.takeIf { danmakuRefreshing })
    } else danmaku.copy(loading = danmaku.loading && danmaku.data == null)
    LaunchedEffect(currentTab, state.sessionRevision, isActive, detail != null) {
        if (isActive) onLoadTab(currentTab)
    }
    val hazeState = rememberHazeState()
    var tabRowHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val tabTopInset = if (splitIntroduction) contentPadding.calculateTopPadding() else 0.dp
    val tabPadding = tabTopInset + with(density) { tabRowHeight.toDp() }
    val introduction: @Composable (PaddingValues, Dp) -> Unit = { panePadding, topPadding ->
        ContentPullToRefresh(
            isRefreshing = state.detail.loading && detail != null,
            onRefresh = onRetry,
            modifier = Modifier.fillMaxSize().padding(
                start = panePadding.calculateStartPadding(layoutDirection),
                end = panePadding.calculateEndPadding(layoutDirection),
            ),
            indicatorTopPadding = topPadding,
        ) {
            val listContentPadding = PaddingValues(
                20.dp, topPadding + 24.dp, 20.dp, panePadding.calculateBottomPadding() + 24.dp,
            )
            val pageColor = MaterialTheme.colorScheme.background
            val chipContainerColor = ThemeColorDefaults.controlContainerColor(pageColor)
            ContentStatusViewport(
                modifier = Modifier.fillMaxSize(),
                contentPadding = listContentPadding,
                leadingContentHeight = 0.dp,
            ) { statusModifier ->
                LazyColumn(state = introductionListState, contentPadding = listContentPadding,
                    verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
                    if (state.detail.error != null || detail == null) {
                        item("status") {
                            ContentStatus(
                                state.detail, onRetry,
                                Modifier.fillMaxWidth().then(if (detail == null) statusModifier else Modifier),
                            )
                        }
                    }
                    if (detail != null) {
                        if (detail.parts.size > 1) {
                            item("parts") {
                                val partChipColors = AppChipDefaults.filterColors(pageColor)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(detail.parts, key = { _, part -> part.id }) { index, part ->
                                        FilterChip(
                                            selected = selectedPart == index,
                                            onClick = { onSelectPart(index) },
                                            label = {
                                                Text(part.title.ifBlank { stringResource(R.string.player_part, part.sourceIndex + 1) })
                                            },
                                            colors = partChipColors,
                                            shape = CircleShape,
                                            border = null,
                                        )
                                    }
                                }
                            }
                        }
                        item("author") {
                            VideoAuthor(
                                detail.author, onOpenProfile, onFollow,
                                enabled = !state.busy && !state.detail.loading &&
                                    state.detail.error == null && !detail.blockedByOther,
                                loading = state.mutation == ContentMutation.FOLLOW,
                            )
                        }
                        item("title") { SelectionContainer { Text(detail.summary.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) } }
                        item("metadata") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                detail.summary.views?.let {
                                    val count = stringResource(R.string.video_views, it)
                                    VideoMetadata(
                                        R.drawable.ic_icon_play,
                                        count,
                                        description = stringResource(R.string.video_views_description, count),
                                    )
                                }
                                VideoMetadata(R.drawable.ic_icon_calendar, dateText(detail.summary.uploadedAt))
                                if (detail.category.isNotBlank()) {
                                    VideoMetadata(R.drawable.ic_icon_category, categoryText(detail.category))
                                }
                            }
                        }
                        item("actions") {
                            CompositionLocalProvider(
                                LocalNumberAnimationIdentity provides Pair(state.sessionRevision, detail.summary.id),
                            ) {
                                VideoActions(
                                    detail = detail,
                                    busy = state.busy || state.detail.loading || state.detail.error != null,
                                    onVote = onVote,
                                    onUnavailable = onUnavailable,
                                    onShare = onShare,
                                    onViewCover = onViewCover,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = if (wideWindow) Alignment.TopStart else Alignment.TopCenter,
                                )
                            }
                        }
                        item("description") {
                            ExpandableText(
                                text = detail.description,
                                expanded = descriptionExpanded,
                                onExpandedChange = { descriptionExpanded = it },
                                collapsedMaxLines = 3,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        if (detail.tags.isNotEmpty()) {
                            item("tags") {
                                val language = LocalConfiguration.current.locales[0].toLanguageTag()
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    detail.tags.forEach { tag ->
                                        AssistChip(
                                            onClick = { onOpenTag(tag.id) },
                                            label = { Text(tag.displayName(language)) },
                                            shape = CircleShape,
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = chipContainerColor,
                                                labelColor = MaterialTheme.colorScheme.onSurface,
                                                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                trailingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                            border = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    VideoContentLayout(
        playerHeight = playerHeight,
        hazeState = hazeState,
        detailPane = detailPane,
        introductionPane = introductionPane,
        introduction = { introduction(introductionPadding, introductionPadding.calculateTopPadding()) },
        aboveTabs = aboveTabs,
        modifier = modifier,
        tabs = {
            PagerTabRow(
                pagerState = pager,
                titles = tabs.map { tab ->
                    stringResource(
                        when (tab) {
                            VideoTab.INTRODUCTION -> R.string.video_tab_introduction
                            VideoTab.COMMENTS -> R.string.video_tab_comments
                            VideoTab.DANMAKU -> R.string.video_tab_danmaku
                        },
                    )
                },
                onTabChange = changeTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontalPadding)
                    .padding(top = tabTopInset)
                    .onSizeChanged { tabRowHeight = it.height },
            )
        },
    ) {
        Spacer(Modifier.fillMaxWidth().height(playerHeight))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            HorizontalPager(
                state = pager,
                key = { tabs[it] },
                modifier = Modifier.fillMaxSize().then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.hazeSource(hazeState) else Modifier,
                ),
                // Leave boundary deltas to the child's overscroll instead of consuming them here.
                userScrollEnabled = !kaomojiPanelOpen,
                pageNestedScrollConnection = if (kaomojiPanelOpen) {
                    panelNestedScrollConnection
                } else {
                    pageNestedScrollConnection
                },
            ) { page ->
                // Child flings can mark the pager as scrolling without changing the active tab.
                val composerActive = isActive && pager.currentPage == page
                when (tabs[page]) {
                    VideoTab.INTRODUCTION -> introduction(contentPadding, tabPadding)
                    VideoTab.COMMENTS -> CompositionLocalProvider(
                        LocalNumberAnimationIdentity provides Triple(
                            state.sessionRevision, detail?.summary?.id, comments.pages.isNotEmpty(),
                        ),
                    ) {
                        VideoCommentsPage(
                            comments, posted, state.commentDraft, state.busy,
                            detail != null && !detail.blockedByOther, onCommentDraft, onSendComment, onCommentsPage,
                            onCommentVote, onOpenProfile, onUnavailable, onCommentsPageRefresh,
                            onCommentsRetry, onCommentsAdjacent, onCommentLocationConsumed,
                            commentsListState, contentPadding,
                            topPadding = tabPadding,
                            onLogin = onLogin.takeUnless { state.signedIn },
                            composerState = commentComposer,
                            composerActive = composerActive,
                            sending = state.mutation == ContentMutation.SEND_COMMENT,
                            recentKaomoji = recentKaomoji, onKaomojiInserted = onKaomojiInserted,
                        )
                    }
                    VideoTab.DANMAKU -> FloatingComposerLayout(
                        contentPadding = contentPadding,
                        topPadding = tabPadding,
                        composer = { availableHeight ->
                            DanmakuComposer(
                                state.danmakuDraft, state.danmakuStyle, state.sessionRevision,
                                onDanmakuDraft, onSendDanmaku, onDanmakuStyle,
                                enabled = detail != null && !detail.blockedByOther,
                                busy = state.busy,
                                sending = state.mutation == ContentMutation.SEND_DANMAKU,
                                onLogin = onLogin.takeUnless { state.signedIn },
                                composerState = danmakuComposer,
                                composerActive = composerActive,
                                availableHeight = availableHeight,
                                contentPadding = contentPadding,
                                recentKaomoji = recentKaomoji, onKaomojiInserted = onKaomojiInserted,
                            )
                        },
                    ) { listBottomPadding ->
                        ContentPullToRefresh(
                            isRefreshing = danmakuRefreshing || (danmaku.loading && danmaku.data != null),
                            onRefresh = onRefreshDanmaku,
                            modifier = Modifier.fillMaxSize(),
                            indicatorTopPadding = tabPadding,
                        ) {
                            var headerHeight by remember { mutableIntStateOf(0) }
                            val listContentPadding = PaddingValues(8.dp, tabPadding + 8.dp, 8.dp, listBottomPadding)
                            ContentStatusViewport(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = listContentPadding,
                                leadingContentHeight = with(density) { headerHeight.toDp() },
                            ) { statusModifier ->
                                LazyColumn(state = danmakuListState, modifier = Modifier.fillMaxSize(),
                                    contentPadding = listContentPadding) {
                                    item("count") {
                                        Column(Modifier.onSizeChanged { headerHeight = it.height }) {
                                            CompositionLocalProvider(
                                                LocalNumberAnimationIdentity provides Triple(
                                                    state.sessionRevision, detail?.summary?.id, danmaku.data != null,
                                                ),
                                            ) {
                                                VideoListCount(R.plurals.video_danmaku_total, danmaku.data?.size ?: 0)
                                            }
                                        }
                                    }
                                    item("status") {
                                        if (danmaku.error != null || danmaku.data.isNullOrEmpty()) {
                                            ContentStatus(
                                                danmakuStatus, onRetryDanmaku,
                                                Modifier.fillMaxWidth().then(
                                                    if (danmaku.data.isNullOrEmpty()) statusModifier else Modifier,
                                                ),
                                                danmaku.data.isNullOrEmpty(),
                                                emptyTitle = stringResource(R.string.video_danmaku_empty),
                                                emptyIconRes = R.drawable.ic_custom_danmaku,
                                                retryEnabled = !checkingAccess && !danmaku.loading,
                                            )
                                        }
                                    }
                                    items(danmaku.data.orEmpty(), contentType = { "danmaku" }) { entry ->
                                        DanmakuListItem(durationText((entry.timeSeconds * 1000).toLong()), entry.text)
                                    }
                                }
                                if (!danmaku.data.isNullOrEmpty() && showDanmakuScrollbar) {
                                    LazyListScrollbar(
                                        state = danmakuListState,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(top = tabPadding + 8.dp, bottom = listBottomPadding)
                                            .width(28.dp)
                                            .fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Video · Light", widthDp = 360, heightDp = 900)
@Preview(name = "Video · Dark", widthDp = 360, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Video · Narrow · Large text", widthDp = 320, heightDp = 1000, fontScale = 2f)
@Composable
private fun VideoScreenPreview() {
    KIRAKIRATheme {
        Surface {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                VideoScreen(
                    state = VideoUiState(
                        detail = ContentState(
                            data = VideoDetail(
                                summary = VideoSummary(id = 0, title = "Video detail layout preview", views = 1280L),
                                parts = emptyList(),
                                author = PublicProfile(uid = 0L, name = "Preview", username = "preview"),
                                description = "This preview uses local data to show the video title, author, " +
                                    "metadata, action buttons, and description. The text is long enough to " +
                                    "check the collapsed description and its expansion button.",
                                category = "",
                                upvotes = 1280L,
                                downvotes = 24L,
                                reaction = Reaction.LIKE,
                            ),
                        ),
                        access = VideoAccessState(),
                        comments = CommentListState(),
                        danmaku = ContentState(data = emptyList()),
                        busy = false,
                        signedIn = false,
                        sessionRevision = 0L,
                        commentDraft = "",
                        danmakuDraft = "",
                        posted = null,
                    ),
                    onRetry = {},
                    onFollow = {},
                    onVote = {},
                    onCommentVote = { _, _ -> },
                    onCommentsPage = {},
                    onCommentsPageRefresh = {},
                    onCommentsRetry = {},
                    onCommentsAdjacent = { _, _ -> },
                    onCommentLocationConsumed = {},
                    onLoadTab = {},
                    onCommentDraft = {},
                    onDanmakuDraft = {},
                    onSendComment = {},
                    onSendDanmaku = {},
                    onRefreshDanmaku = {},
                    onRetryDanmaku = {},
                    onOpenProfile = {},
                    onUnavailable = {},
                    onShare = {},
                    onLogin = {},
                    contentPadding = PaddingValues(0.dp),
                    onViewCover = {},
                    selectedPart = 0,
                    onSelectPart = {},
                    onOpenTag = {},
                    playerHeight = maxWidth * 9 / 16,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
