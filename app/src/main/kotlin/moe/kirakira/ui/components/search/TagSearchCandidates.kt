package moe.kirakira.ui.components.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import moe.kirakira.data.content.VideoTag
import moe.kirakira.ui.components.list.ConnectedLazyColumn
import moe.kirakira.ui.components.list.connectedListItemShapes
import moe.kirakira.ui.components.status.ContentStatus
import moe.kirakira.ui.components.status.ContentStatusViewport
import moe.kirakira.ui.content.TagSearchState

@Composable
internal fun TagSearchCandidates(
    search: TagSearchState,
    selectedTags: List<VideoTag>,
    onToggleTag: (VideoTag) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val selected = remember(selectedTags) { selectedTags.map { it.id }.toSet() }
    val candidates = search.candidates.data.orEmpty()
    val listContentPadding = PaddingValues(
        start = 16.dp, end = 16.dp,
        top = contentPadding.calculateTopPadding() + 12.dp,
        bottom = contentPadding.calculateBottomPadding() + 16.dp,
    )
    ContentStatusViewport(
        modifier = modifier.widthIn(max = 840.dp).fillMaxSize(),
        contentPadding = listContentPadding,
        leadingContentHeight = 0.dp,
    ) { statusModifier ->
        ConnectedLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listContentPadding,
        ) {
            if (search.query.isNotBlank() &&
                (search.candidates.loading || search.candidates.error != null || candidates.isEmpty())
            ) {
                item("status") {
                    ContentStatus(
                        state = search.candidates,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth().then(
                            if (candidates.isEmpty()) statusModifier else Modifier,
                        ),
                        empty = candidates.isEmpty(),
                        emptyTitle = stringResource(R.string.search_tags_empty),
                        emptyIconRes = R.drawable.ic_icon_tag,
                        retryEnabled = enabled,
                    )
                }
            }
            connectedItemsIndexed("tags", candidates, key = { _, tag -> tag.id }) { index, tag ->
                val checked = tag.id in selected
                val name = tag.displayName(language)
                val original = tag.originalName()?.takeIf { it != name }
                SegmentedListItem(
                    onClick = { onToggleTag(tag) },
                    enabled = enabled,
                    shapes = connectedListItemShapes(index, candidates.size),
                    modifier = Modifier.semantics {
                        role = Role.Checkbox
                        toggleableState = ToggleableState(checked)
                    },
                    leadingContent = { Icon(painterResource(R.drawable.ic_icon_tag), null, Modifier.size(20.dp)) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
                    supportingContent = original?.let { { Text(it) } },
                    content = { Text(name) },
                )
            }
        }
    }
}
