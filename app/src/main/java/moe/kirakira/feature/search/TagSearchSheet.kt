package moe.kirakira.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ConnectedLazyColumn
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.connectedListItemShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagSearchSheet(state: SearchUiState, onEvent: (SearchEvent) -> Unit) {
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val focus = LocalFocusManager.current
    val selected = remember(state.tags) { state.tags.map { it.id }.toSet() }
    val candidates = state.candidates.data.orEmpty()
    ModalBottomSheet(
        onDismissRequest = { onEvent(SearchEvent.CloseTags) },
        containerColor = if (candidates.isNotEmpty()) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.search_choose_tags), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() })
                SearchIconButton(stringResource(R.string.search_close_tags), R.drawable.ic_symbol_close,
                    { onEvent(SearchEvent.CloseTags) })
            }
            QuerySearchBar(
                query = state.tagQuery,
                onQueryChange = { onEvent(SearchEvent.TagQueryChanged(it)) },
                onSearch = {
                    focus.clearFocus()
                    onEvent(SearchEvent.RetryTags)
                },
                placeholder = stringResource(R.string.management_search_tags),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                enabled = state.ready,
                searchEnabled = state.tagQuery.isNotBlank(),
                onClear = { onEvent(SearchEvent.TagQueryChanged("")) },
                searchDescription = stringResource(R.string.management_search_tags),
            )
            ConnectedLazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            ) {
                if (state.tagQuery.isNotBlank() && (state.candidates.error != null || candidates.isEmpty())) {
                    item("status") {
                        ContentStatus(state.candidates, { onEvent(SearchEvent.RetryTags) },
                            Modifier.fillMaxWidth().heightIn(min = 180.dp), empty = candidates.isEmpty(),
                            emptyTitle = stringResource(R.string.search_tags_empty), emptyIconRes = R.drawable.ic_symbol_label)
                    }
                }
                connectedItemsIndexed("tags", candidates, key = { _, tag -> tag.id }) { index, tag ->
                    val checked = tag.id in selected
                    val name = tag.displayName(language)
                    val original = tag.originalName()?.takeIf { it != name }
                    SegmentedListItem(
                        onClick = { onEvent(SearchEvent.ToggleTag(tag)) },
                        enabled = state.ready,
                        shapes = connectedListItemShapes(index, candidates.size),
                        modifier = Modifier.semantics {
                            role = Role.Checkbox
                            toggleableState = ToggleableState(checked)
                        },
                        leadingContent = { Icon(painterResource(R.drawable.ic_symbol_label), null, Modifier.size(20.dp)) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = state.ready) },
                        supportingContent = original?.let { { Text(it) } },
                        content = { Text(name) },
                    )
                }
            }
        }
    }
}
