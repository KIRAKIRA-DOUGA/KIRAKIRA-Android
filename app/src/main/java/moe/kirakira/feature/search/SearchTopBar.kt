package moe.kirakira.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.feature.settings.VideoCardLayout

@Composable
internal fun SearchTopBar(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Box(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
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
                                        painter = painterResource(
                                            if (mode == SearchMode.KEYWORD) R.drawable.ic_symbol_match_word
                                            else R.drawable.ic_symbol_label,
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SearchSortMenu(state, onEvent)
                        val nextLayout = if (state.layout == VideoCardLayout.GRID) {
                            VideoCardLayout.LIST
                        } else VideoCardLayout.GRID
                        SearchIconButton(
                            label = stringResource(nextLayout.titleRes),
                            icon = if (nextLayout == VideoCardLayout.GRID) {
                                R.drawable.ic_symbol_grid_view
                            } else R.drawable.ic_symbol_view_list,
                            onClick = { onEvent(SearchEvent.LayoutChanged(nextLayout)) },
                            enabled = state.ready,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSortMenu(state: SearchUiState, onEvent: (SearchEvent) -> Unit) {
    var expanded by remember(state.generation, state.ready) { mutableStateOf(false) }
    Box {
        SearchIconButton(
            label = stringResource(state.sort.label),
            icon = R.drawable.ic_symbol_sort,
            onClick = { expanded = true },
            enabled = state.ready,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SearchSort.entries.forEach { sort ->
                val isSelected = state.sort == sort
                DropdownMenuItem(
                    text = { Text(stringResource(sort.label)) },
                    onClick = {
                        expanded = false
                        onEvent(SearchEvent.SortChanged(sort))
                    },
                    modifier = Modifier.semantics { selected = isSelected },
                    enabled = state.ready,
                    trailingIcon = if (!isSelected) null else {
                        { Icon(painterResource(R.drawable.ic_symbol_check), stringResource(R.string.search_selected)) }
                    },
                )
            }
            listOf(true, false).forEach { descending ->
                val directionEnabled = state.ready && state.sort != SearchSort.DEFAULT
                val isSelected = state.sort != SearchSort.DEFAULT && state.descending == descending
                DropdownMenuItem(
                    text = {
                        Text(stringResource(
                            if (descending) R.string.search_sort_descending else R.string.search_sort_ascending,
                        ))
                    },
                    onClick = {
                        expanded = false
                        if (state.descending != descending) onEvent(SearchEvent.ToggleDirection)
                    },
                    modifier = Modifier.semantics { selected = isSelected },
                    enabled = directionEnabled,
                    leadingIcon = {
                        Icon(
                            painterResource(
                                if (descending) R.drawable.ic_symbol_arrow_downward else R.drawable.ic_symbol_arrow_upward,
                            ),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (!isSelected) null else {
                        { Icon(painterResource(R.drawable.ic_symbol_check), stringResource(R.string.search_selected)) }
                    },
                )
            }
        }
    }
}
