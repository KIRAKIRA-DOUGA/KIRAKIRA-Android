package moe.kirakira.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.content.ContentState

@Composable
internal fun SearchHistoryContent(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val focus = LocalFocusManager.current
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val placement = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    LazyColumn(
        state = listState,
        modifier = modifier
            .widthIn(max = 840.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding(),
        ),
    ) {
        item("history-heading") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_history_title),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { onEvent(SearchEvent.RequestClearHistory) },
                    enabled = state.ready && !state.historyBusy && state.history.isNotEmpty(),
                ) { Text(stringResource(R.string.search_history_clear)) }
            }
        }
        if (state.historyError) {
            item("history-error") { SearchHistoryError(onEvent, canDismiss = state.historyLoaded) }
        }
        if (!state.historyLoaded || state.history.isEmpty()) {
            item("history-empty") {
                if (!state.historyError) {
                    ContentStatus(
                        state = ContentState<List<Unit>>(loading = !state.historyLoaded && state.historyBusy),
                        onRetry = { onEvent(SearchEvent.RetryHistory) },
                        modifier = Modifier.fillParentMaxHeight(0.65f),
                        empty = state.historyLoaded,
                        emptyTitle = stringResource(R.string.search_history_empty),
                        emptyIconRes = R.drawable.ic_symbol_history,
                    )
                }
            }
        }
        items(state.history, key = { it.key }) { entry ->
            val leading: @Composable () -> Unit = {
                Icon(
                    painterResource(if (entry.keyword != null) R.drawable.ic_symbol_match_word else R.drawable.ic_symbol_label),
                    contentDescription = stringResource(
                        if (entry.keyword != null) R.string.search_mode_keyword else R.string.search_mode_tag,
                    ),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val trailing: @Composable () -> Unit = {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    SearchIconButton(
                        stringResource(R.string.search_history_delete), R.drawable.ic_symbol_close,
                        onClick = { onEvent(SearchEvent.DeleteHistory(entry.key)) },
                        enabled = state.ready && !state.historyBusy,
                    )
                }
            }
            ListItem(
                onClick = {
                    focus.clearFocus()
                    onEvent(SearchEvent.UseHistory(entry.key))
                },
                enabled = state.ready,
                // The trailing button adds 12dp around its icon; keep both icon canvases 16dp from the edge.
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                content = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        leading()
                        if (entry.keyword != null) {
                            Text(
                                entry.keyword,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                entry.tags.forEach { tag ->
                                    Surface(
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        shape = SuggestionChipDefaults.shape,
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .heightIn(min = SuggestionChipDefaults.Height)
                                                .padding(SuggestionChipDefaults.ContentPadding)
                                                .padding(vertical = 4.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                tag.displayName(language),
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        trailing()
                    }
                },
                shapes = ListItemDefaults.shapes(
                    shape = RectangleShape, pressedShape = RectangleShape, focusedShape = RectangleShape,
                    hoveredShape = RectangleShape,
                ),
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.animateItem(
                    fadeInSpec = effects, placementSpec = placement, fadeOutSpec = effects,
                ),
            )
        }
    }
}

@Composable
internal fun SearchHistoryError(
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
    canDismiss: Boolean = true,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.search_history_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row {
            TextButton(onClick = { onEvent(SearchEvent.RetryHistory) }) {
                Text(stringResource(R.string.search_history_reload))
            }
            if (canDismiss) TextButton(onClick = { onEvent(SearchEvent.DismissHistoryError) }) {
                Text(stringResource(R.string.search_history_dismiss))
            }
        }
    }
}

@Composable
internal fun ClearSearchHistoryDialog(state: SearchUiState, onEvent: (SearchEvent) -> Unit) {
    if (!state.clearHistoryOpen) return
    ActionDialog(
        onDismissRequest = { onEvent(SearchEvent.DismissClearHistory) },
        icon = { Icon(painterResource(R.drawable.ic_symbol_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.search_history_clear_title)) },
        actions = {
            ShadowButton(
                onClick = { onEvent(SearchEvent.ClearHistory) },
                destructive = true,
                enabled = state.ready && !state.historyBusy,
            ) { Text(stringResource(R.string.search_history_clear)) }
            TextButton(onClick = { onEvent(SearchEvent.DismissClearHistory) }) {
                Text(stringResource(R.string.account_cancel))
            }
        },
    )
}
