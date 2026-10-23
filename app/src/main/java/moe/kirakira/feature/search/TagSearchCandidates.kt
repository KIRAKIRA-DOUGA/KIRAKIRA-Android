package moe.kirakira.feature.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ConnectedLazyColumn
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.connectedListItemShapes

@Composable
internal fun TagSearchCandidates(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val selected = remember(state.draftTags) { state.draftTags.map { it.id }.toSet() }
    val candidates = state.candidates.data.orEmpty()
    ConnectedLazyColumn(
        modifier = modifier.widthIn(max = 840.dp).fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
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
