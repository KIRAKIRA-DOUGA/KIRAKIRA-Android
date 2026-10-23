package moe.kirakira.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import moe.kirakira.R

@Composable
internal fun SearchTopBar(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val tagAnchor = LocalTagSearchAnchor.current
    val closeSearch = {
        focus.clearFocus()
        keyboard?.hide()
        onEvent(SearchEvent.Clear)
    }
    Box(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        onClear = closeSearch,
                    )
                } else {
                    TagSearchField(
                        tags = state.tags,
                        onAdd = {
                            focus.clearFocus()
                            onEvent(SearchEvent.OpenTags)
                        },
                        onRemove = { onEvent(SearchEvent.RemoveTag(it.id)) },
                        modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                            tagAnchor?.boundsOnScreen = Rect(it.positionOnScreen(), it.size.toSize())
                        },
                        enabled = state.ready,
                        onClear = closeSearch,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
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
                            shape = MaterialTheme.shapes.large,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
                            ),
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
            }
        }
    }
}
