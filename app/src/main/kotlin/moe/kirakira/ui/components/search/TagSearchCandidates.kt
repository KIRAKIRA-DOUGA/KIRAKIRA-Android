package moe.kirakira.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import moe.kirakira.ui.components.list.ConnectedListGroup
import moe.kirakira.ui.components.list.connectedListItemShapes
import moe.kirakira.ui.components.status.ContentStatus
import moe.kirakira.ui.components.status.ContentStatusViewport
import moe.kirakira.ui.content.ContentState
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
        modifier = modifier.widthIn(max = TagSearchMaxWidth).fillMaxSize(),
        contentPadding = listContentPadding,
        leadingContentHeight = 0.dp,
    ) { statusModifier ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentWidth = maxWidth - 32.dp
            val twoColumns = contentWidth / LocalDensity.current.fontScale >= 600.dp
            val showStatus = search.query.isNotBlank() &&
                (search.candidates.loading || search.candidates.error != null || candidates.isEmpty())
            if (twoColumns) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listContentPadding,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showStatus) {
                        item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                            TagSearchCandidateStatus(
                                state = search.candidates,
                                onRetry = onRetry,
                                empty = candidates.isEmpty(),
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth().then(
                                    if (candidates.isEmpty()) statusModifier else Modifier,
                                ),
                            )
                        }
                    }
                    items(candidates, key = { it.id }) { tag ->
                        val checked = tag.id in selected
                        val name = tag.displayName(language)
                        ConnectedListGroup {
                            TagSearchCandidateItem(
                                tag = tag,
                                checked = checked,
                                name = name,
                                original = tag.originalName()?.takeIf { it != name },
                                shapes = connectedListItemShapes(0, 1),
                                enabled = enabled,
                                onToggle = onToggleTag,
                            )
                        }
                    }
                }
            } else {
                ConnectedLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listContentPadding,
                ) {
                    if (showStatus) {
                        item("status") {
                            TagSearchCandidateStatus(
                                state = search.candidates,
                                onRetry = onRetry,
                                empty = candidates.isEmpty(),
                                enabled = enabled,
                                modifier = Modifier.fillMaxWidth().then(
                                    if (candidates.isEmpty()) statusModifier else Modifier,
                                ),
                            )
                        }
                    }
                    connectedItemsIndexed("tags", candidates, key = { _, tag -> tag.id }) { index, tag ->
                        val checked = tag.id in selected
                        val name = tag.displayName(language)
                        TagSearchCandidateItem(
                            tag = tag,
                            checked = checked,
                            name = name,
                            original = tag.originalName()?.takeIf { it != name },
                            shapes = connectedListItemShapes(index, candidates.size),
                            enabled = enabled,
                            onToggle = onToggleTag,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagSearchCandidateStatus(
    state: ContentState<List<VideoTag>>,
    onRetry: () -> Unit,
    empty: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    ContentStatus(
        state = state,
        onRetry = onRetry,
        modifier = modifier,
        empty = empty,
        emptyTitle = stringResource(R.string.search_tags_empty),
        emptyIconRes = R.drawable.ic_icon_tag,
        retryEnabled = enabled,
    )
}

@Composable
private fun TagSearchCandidateItem(
    tag: VideoTag,
    checked: Boolean,
    name: String,
    original: String?,
    shapes: ListItemShapes,
    enabled: Boolean,
    onToggle: (VideoTag) -> Unit,
) {
    SegmentedListItem(
        onClick = { onToggle(tag) },
        enabled = enabled,
        shapes = shapes,
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
