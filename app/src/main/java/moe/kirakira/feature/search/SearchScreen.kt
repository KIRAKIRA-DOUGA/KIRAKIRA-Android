package moe.kirakira.feature.search

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.kirakira.R
import moe.kirakira.data.content.VideoTag
import moe.kirakira.ui.components.AnimatedCountLabel
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.video.VideoCardRow
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
    val listState = remember(state.generation) { LazyListState(state.scrollIndex, state.scrollOffset) }
    val historyListState = rememberLazyListState()
    val activeListState by rememberUpdatedState(if (state.submitted == null) historyListState else listState)
    val scrollToTop: suspend () -> Unit = remember {
        { activeListState.animateScrollToItem(0) }
    }
    DisposableEffect(onScrollToTopActionChange, scrollToTop, activeListState) {
        onScrollToTopActionChange(scrollToTop)
        onDispose { onScrollToTopActionChange(null) }
    }
    val currentEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) ->
                currentEvent(SearchEvent.Scrolled(state.generation, index, offset))
            }
    }
    val videos = remember(state.videos.data, state.sort, state.descending) {
        sortedSearchVideos(state.videos.data.orEmpty(), state.sort, state.descending)
    }
    val rows = remember(videos, state.layout) { videos.chunked(state.layout.columns) }
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    ContentPullToRefresh(
        isRefreshing = !state.pickerOpen && state.videos.loading && state.videos.data != null,
        onRefresh = { onEvent(SearchEvent.Refresh) },
        modifier = modifier.fillMaxSize().imePadding().testTag("search_screen"),
        enabled = state.ready && !state.pickerOpen && state.submitted != null && !state.videos.loading,
        indicatorTopPadding = contentPadding.calculateTopPadding(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val headerHeight = with(density) { headerHeightPx.toDp() }
            val statusHeight = (maxHeight - contentPadding.calculateTopPadding() -
                contentPadding.calculateBottomPadding() - headerHeight - 36.dp).coerceAtLeast(120.dp)
            AnimatedVisibility(
                visible = state.submitted == null,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 24 },
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it / 24 },
            ) {
                SearchHistoryContent(state, onEvent, contentPadding, listState = historyListState)
            }
            if (state.submitted != null) LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.historyError) item("history-error") {
                    SearchHistoryError(onEvent, canDismiss = state.historyLoaded)
                }
                item("search-header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchResultCount(state, Modifier.weight(1f))
                        SearchResultControls(state, onEvent)
                    }
                }
                if (state.videos.error != null || state.videos.data.isNullOrEmpty()) {
                    item("status") {
                        ContentStatus(
                            state.videos.copy(loading = state.videos.loading && state.videos.data == null),
                            onRetry = { onEvent(SearchEvent.Refresh) },
                            modifier = Modifier.fillMaxWidth().then(
                                if (state.videos.data.isNullOrEmpty()) Modifier.heightIn(min = statusHeight) else Modifier,
                            ),
                            empty = state.videos.data.isNullOrEmpty(),
                            emptyTitle = stringResource(R.string.search_videos_empty),
                            emptyIconRes = R.drawable.ic_symbol_search,
                        )
                    }
                }
                items(rows, key = { it.first().id }) { row ->
                    VideoCardRow(row, state.layout, onOpenVideo, Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (state.pickerOpen) TagSearchDialog(state, onEvent)
    ClearSearchHistoryDialog(state, onEvent)
}

@Composable
internal fun SelectedSearchTags(
    tags: List<VideoTag>,
    onRemove: (VideoTag) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            val label = tag.displayName(language)
            InputChip(
                selected = true,
                onClick = { onRemove(tag) },
                enabled = enabled,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.widthIn(max = 280.dp),
                trailingIcon = {
                    Icon(painterResource(R.drawable.ic_symbol_close), stringResource(R.string.search_remove_tag, label),
                        Modifier.size(18.dp))
                },
            )
        }
    }
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

@Composable
internal fun SearchIconButton(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SearchTooltip(label) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Icon(painterResource(icon), label)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
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
