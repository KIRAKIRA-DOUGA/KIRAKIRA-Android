package moe.kirakira.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.kirakira.R
import moe.kirakira.ui.components.illustration.SearchVideosEmptyLogo
import moe.kirakira.ui.components.scaffold.FrostedScaffold
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopEffect
import moe.kirakira.ui.components.scaffold.smoothScrollToTop
import moe.kirakira.ui.components.search.TagSearchDialog
import moe.kirakira.ui.components.status.ContentPullToRefresh
import moe.kirakira.ui.components.status.ContentStatus
import moe.kirakira.ui.components.status.ContentStatusViewport
import moe.kirakira.ui.components.text.AnimatedCountLabel
import moe.kirakira.ui.components.video.FeedVideoCard
import moe.kirakira.ui.components.video.VideoCardRowSpacing
import moe.kirakira.ui.components.video.VideoFeedGrid
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun SearchPage(
    model: SearchViewModel?,
    onOpenVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onScrollToTopActionChange: ((suspend () -> Unit)?) -> Unit = {},
) {
    val state = model?.state?.collectAsStateWithLifecycle()?.value ?: SearchUiState()
    SearchScreen(state, { model?.onEvent(it) }, onOpenVideo, modifier, contentPadding, onScrollToTopActionChange)
}

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    onOpenVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onScrollToTopActionChange: ((suspend () -> Unit)?) -> Unit = {},
) {
    key(state.accountRevision) {
        SearchScreenContent(state, onEvent, onOpenVideo, modifier, contentPadding, onScrollToTopActionChange)
    }
}

@Composable
private fun SearchScreenContent(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    onOpenVideo: (Int) -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
    onScrollToTopActionChange: ((suspend () -> Unit)?) -> Unit,
) {
    val videos = remember(state.videos.data, state.sort, state.descending) {
        sortedSearchVideos(state.videos.data.orEmpty(), state.sort, state.descending)
    }
    val showStatus = state.videos.error != null || videos.isEmpty()
    val listState = remember(state.generation) {
        val headers = buildList {
            if (state.historyError) add("history-error")
            add("search-header")
            if (showStatus) add("status")
        }
        val headerIndex = state.scrollKey?.let(headers::indexOf) ?: -1
        val videoIndex = videos.indexOfFirst { it.id.toString() == state.scrollKey }
        val index = when {
            headerIndex >= 0 -> headerIndex
            videoIndex >= 0 -> headers.size + videoIndex
            else -> 0
        }
        LazyGridState(index, if (headerIndex >= 0 || videoIndex >= 0) state.scrollOffset else 0)
    }
    val historyListState = rememberLazyListState()
    val showResults = state.submitted != null
    val latestShowResults by rememberUpdatedState(showResults)
    val latestGridState by rememberUpdatedState(listState)
    val scrollToTop: suspend () -> Unit = remember {
        { if (latestShowResults) latestGridState.smoothScrollToTop() else historyListState.smoothScrollToTop() }
    }
    DisposableEffect(onScrollToTopActionChange, scrollToTop) {
        onScrollToTopActionChange(scrollToTop)
        onDispose { onScrollToTopActionChange(null) }
    }
    StatusBarScrollToTopEffect(historyListState, enabled = !showResults && !state.pickerOpen)
    StatusBarScrollToTopEffect(listState, enabled = showResults && !state.pickerOpen)
    val currentEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState, showResults) {
        if (!showResults) return@LaunchedEffect
        snapshotFlow {
            val key = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }?.key
            key to listState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { (key, offset) ->
            if (key is String) currentEvent(SearchEvent.Scrolled(state.generation, key, offset))
        }
    }
    val sectionSpacing = 12.dp
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var historyErrorHeightPx by remember { mutableIntStateOf(0) }
    ContentPullToRefresh(
        isRefreshing = !state.pickerOpen && state.videos.loading && state.videos.data != null,
        onRefresh = { onEvent(SearchEvent.Refresh) },
        modifier = modifier.fillMaxSize().imePadding().testTag("search_screen"),
        enabled = state.ready && !state.pickerOpen && state.submitted != null && !state.videos.loading,
        indicatorTopPadding = contentPadding.calculateTopPadding(),
    ) {
        val listContentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        )
        ContentStatusViewport(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listContentPadding,
            leadingContentHeight = with(density) {
                (headerHeightPx + if (state.historyError) historyErrorHeightPx else 0).toDp()
            } + sectionSpacing * (if (state.historyError) 2 else 1),
        ) { statusModifier ->
            AnimatedVisibility(
                visible = state.submitted == null,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 24 },
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it / 24 },
            ) {
                SearchHistoryContent(
                    state, onEvent, contentPadding,
                    listState = historyListState,
                    isActive = state.submitted == null && !state.pickerOpen,
                )
            }
            if (showResults) VideoFeedGrid(
                layout = state.layout,
                state = listState,
                contentPadding = listContentPadding,
            ) { columns ->
                if (state.historyError) item("history-error", span = { GridItemSpan(maxLineSpan) }) {
                    SearchHistoryError(
                        onEvent,
                        modifier = Modifier
                            .padding(bottom = sectionSpacing)
                            .onSizeChanged { historyErrorHeightPx = it.height },
                        canDismiss = state.historyLoaded,
                    )
                }
                item("search-header", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = sectionSpacing)
                            .onSizeChanged { headerHeightPx = it.height },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchResultCount(state, Modifier.weight(1f))
                        SearchResultControls(state, onEvent)
                    }
                }
                if (showStatus) {
                    item("status", span = { GridItemSpan(maxLineSpan) }) {
                        ContentStatus(
                            state.videos.copy(loading = state.videos.loading && state.videos.data == null),
                            onRetry = { onEvent(SearchEvent.Refresh) },
                            modifier = Modifier.fillMaxWidth().then(
                                if (videos.isEmpty()) statusModifier else Modifier.padding(bottom = sectionSpacing),
                            ),
                            empty = state.videos.data.isNullOrEmpty(),
                            emptyTitle = stringResource(R.string.search_videos_empty),
                            emptyDescription = null,
                            emptyIcon = { SearchVideosEmptyLogo(isActive = !state.pickerOpen) },
                            emptyIconRes = R.drawable.ic_icon_search,
                        )
                    }
                }
                itemsIndexed(videos, key = { _, video -> video.id.toString() }) { index, video ->
                    FeedVideoCard(
                        video, state.layout, onOpenVideo,
                        modifier = Modifier.fillMaxWidth().padding(
                            bottom = if (index / columns == videos.lastIndex / columns) 0.dp else VideoCardRowSpacing,
                        ),
                    )
                }
            }
        }
    }
    if (state.pickerOpen) {
        TagSearchDialog(
            search = state.tagSearch,
            selectedTags = state.draftTags,
            onQueryChange = { onEvent(SearchEvent.TagQueryChanged(it)) },
            onRetry = { onEvent(SearchEvent.RetryTags) },
            onToggleTag = { onEvent(SearchEvent.ToggleTag(it)) },
            onDismiss = { onEvent(SearchEvent.CloseTags) },
            onConfirm = { onEvent(SearchEvent.ApplyTags) },
            confirmLabel = stringResource(R.string.search_apply_tags),
            enabled = state.ready,
            confirmEnabled = state.draftTags.isNotEmpty() || state.tags.isNotEmpty(),
            confirmOnExit = true,
            sourceTags = state.tags,
        )
    }
    ClearSearchHistoryDialog(state, onEvent)
}

@Composable
private fun SearchResultCount(state: SearchUiState, modifier: Modifier = Modifier) {
    val count = state.videos.data?.size
    if (count == null) {
        Text(
            text = "",
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        key(state.generation) {
            AnimatedCountLabel(
                count = count.toLong(),
                template = pluralStringResource(R.plurals.search_video_count, count),
                modifier = modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(locale = "zh", showBackground = true)
@Composable
private fun SearchPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        val state = SearchUiState(ready = true)
        FrostedScaffold(topBar = { SearchTopBar(state, {}) }) { padding ->
            SearchScreen(state, {}, {}, contentPadding = padding)
        }
    }
}
