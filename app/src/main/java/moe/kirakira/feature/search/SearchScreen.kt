package moe.kirakira.feature.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
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
import moe.kirakira.feature.settings.VideoCardLayout
import moe.kirakira.feature.video.VideoCardRow
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun SearchPage(
    model: SearchViewModel?,
    onOpenVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state = model?.state?.collectAsStateWithLifecycle()?.value ?: SearchUiState()
    SearchScreen(state, { model?.onEvent(it) }, onOpenVideo, modifier, contentPadding)
}

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    onOpenVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val listState = remember(state.generation) { LazyListState(state.scrollIndex, state.scrollOffset) }
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
    ContentPullToRefresh(
        isRefreshing = state.videos.loading && state.videos.data != null,
        onRefresh = { onEvent(SearchEvent.Refresh) },
        modifier = modifier.fillMaxSize().imePadding().testTag("search_screen"),
        enabled = state.ready && state.submitted != null && !state.videos.loading,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val statusHeight = (maxHeight - contentPadding.calculateBottomPadding() - 240.dp).coerceAtLeast(120.dp)
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item("search-form") { SearchForm(state, onEvent, Modifier.widthIn(max = 640.dp).fillMaxWidth()) }
                if (state.submitted != null) {
                    item("tools") { SearchResultControls(state, onEvent) }
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
    }
    if (state.pickerOpen) TagSearchSheet(state, onEvent)
}

@Composable
private fun SearchForm(state: SearchUiState, onEvent: (SearchEvent) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.mode == SearchMode.KEYWORD) {
            QuerySearchBar(
                query = state.keyword,
                onQueryChange = { onEvent(SearchEvent.KeywordChanged(it)) },
                onSearch = {
                    focus.clearFocus()
                    onEvent(SearchEvent.Submit)
                },
                placeholder = stringResource(R.string.search_placeholder),
                modifier = Modifier.fillMaxWidth(),
                enabled = state.ready,
                searchEnabled = state.keyword.isNotBlank() &&
                    !(state.videos.loading && state.submitted == SearchCriteria.Keyword(state.keyword.trim())),
                showClear = state.keyword.isNotEmpty() || state.submitted != null,
                onClear = { onEvent(SearchEvent.Clear) },
            )
        } else {
            QuerySearchBar(
                query = "",
                onQueryChange = {},
                onSearch = {
                    focus.clearFocus()
                    onEvent(SearchEvent.OpenTags)
                },
                placeholder = stringResource(R.string.search_choose_tags),
                modifier = Modifier.fillMaxWidth(),
                enabled = state.ready,
                readOnly = true,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SearchMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = {
                        focus.clearFocus()
                        onEvent(SearchEvent.ModeChanged(mode))
                    },
                    enabled = state.ready,
                    label = { Text(stringResource(mode.label)) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (mode == SearchMode.KEYWORD) R.drawable.ic_symbol_match_word
                                else R.drawable.ic_symbol_label),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
        if (state.mode == SearchMode.TAG && state.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.tags.forEach { tag ->
                    val label = tag.displayName(language)
                    InputChip(
                        selected = true,
                        onClick = { onEvent(SearchEvent.RemoveTag(tag.id)) },
                        enabled = state.ready,
                        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.widthIn(max = 280.dp),
                        trailingIcon = {
                            Icon(painterResource(R.drawable.ic_symbol_close), stringResource(R.string.search_remove_tag, label),
                                Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultControls(state: SearchUiState, onEvent: (SearchEvent) -> Unit) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = state.videos.data?.let { pluralStringResource(R.plurals.search_video_count, it.size, it.size) }.orEmpty(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            SearchIconButton(stringResource(state.sort.label), R.drawable.ic_symbol_sort, { sortMenu = true })
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SearchSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.label)) },
                        onClick = {
                            sortMenu = false
                            onEvent(SearchEvent.SortChanged(sort))
                        },
                        trailingIcon = if (state.sort != sort) null else {
                            { Icon(painterResource(R.drawable.ic_symbol_check), stringResource(R.string.search_selected)) }
                        },
                    )
                }
            }
        }
        SearchIconButton(
            stringResource(if (state.descending) R.string.search_sort_descending else R.string.search_sort_ascending),
            if (state.descending) R.drawable.ic_symbol_arrow_downward else R.drawable.ic_symbol_arrow_upward,
            { onEvent(SearchEvent.ToggleDirection) },
            enabled = state.sort != SearchSort.DEFAULT,
        )
        val nextLayout = if (state.layout == VideoCardLayout.GRID) VideoCardLayout.LIST else VideoCardLayout.GRID
        SearchIconButton(
            stringResource(nextLayout.titleRes),
            if (nextLayout == VideoCardLayout.GRID) R.drawable.ic_symbol_grid_view else R.drawable.ic_symbol_view_list,
            { onEvent(SearchEvent.LayoutChanged(nextLayout)) },
        )
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
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
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
    KIRAKIRATheme(dynamicColor = false) { SearchScreen(SearchUiState(ready = true), {}, {}) }
}
