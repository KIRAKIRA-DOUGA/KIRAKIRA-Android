package moe.kirakira.ui.components.search

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import moe.kirakira.R
import moe.kirakira.data.content.VideoTag
import moe.kirakira.ui.components.selection.AppChipDefaults

@Composable
internal fun TagSearchField(
    tags: List<VideoTag>,
    onAdd: () -> Unit,
    onRemove: (VideoTag) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showClear: Boolean = tags.isNotEmpty(),
    onClear: () -> Unit = {},
    clearDescription: String = stringResource(R.string.search_clear),
    scrollState: ScrollState = rememberScrollState(),
    animateSize: Boolean = true,
    backgroundModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
) {
    val sizeModifier = if (animateSize) {
        Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
    } else Modifier
    SearchFieldContainer(modifier, backgroundModifier, contentModifier) { containerColor ->
        Row(
            modifier = sizeModifier
                .heightIn(min = SearchBarDefaults.InputFieldHeight)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.search_choose_tags),
                    onClick = onAdd,
                )
                // Keep the same 4dp horizontal inset as the input field's text and icons.
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_icon_tag), contentDescription = null)
            }
            if (tags.isEmpty()) {
                Text(
                    stringResource(R.string.search_choose_tags),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SelectedSearchTags(
                    tags = tags,
                    onRemove = onRemove,
                    parentContainerColor = containerColor,
                    modifier = Modifier
                        .weight(1f)
                        // The group reserves its outer touch padding, keeping one row at the shared input height.
                        .padding(vertical = 4.dp),
                    enabled = enabled,
                    scrollState = scrollState,
                )
            }
            SearchIconButton(
                stringResource(R.string.search_choose_tags), R.drawable.ic_icon_add,
                onClick = onAdd, enabled = enabled,
            )
            if (showClear) {
                SearchIconButton(
                    clearDescription, R.drawable.ic_icon_close, onClick = onClear, enabled = enabled,
                )
            }
        }
    }
}

@Composable
internal fun SelectedSearchTags(
    tags: List<VideoTag>,
    onRemove: (VideoTag) -> Unit,
    parentContainerColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
) {
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val chipColors = AppChipDefaults.inputColors(parentContainerColor)
    val minimumInteractiveSize = LocalMinimumInteractiveComponentSize.current.takeOrElse { 0.dp }
    val outerTouchPadding = ((minimumInteractiveSize - InputChipDefaults.Height) / 2f).coerceAtLeast(0.dp)
    val spacing = Arrangement.spacedBy(8.dp)
    val rows: @Composable (Modifier, Int) -> Unit = { rowModifier, maxLines ->
        FlowRow(
            modifier = rowModifier.padding(vertical = outerTouchPadding),
            horizontalArrangement = spacing,
            verticalArrangement = spacing,
            maxLines = maxLines,
        ) {
            tags.forEach { tag ->
                val label = tag.displayName(language)
                InputChip(
                    selected = true,
                    onClick = { onRemove(tag) },
                    enabled = enabled,
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    shape = CircleShape,
                    border = null,
                    colors = chipColors,
                    modifier = Modifier.widthIn(min = minimumInteractiveSize, max = 280.dp),
                    trailingIcon = {
                        Icon(painterResource(R.drawable.ic_icon_close), stringResource(R.string.search_remove_tag, label),
                            Modifier.size(18.dp))
                    },
                )
            }
        }
    }
    // Space the visible pills. Compose still expands each chip's touch target at the input layer.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides InputChipDefaults.Height) {
        SubcomposeLayout(modifier) { constraints ->
            // Measure two wrapped rows at the actual width and font scale before sizing the scroll viewport.
            val visibleRows = subcompose("visibleRows") { rows(Modifier, 2) }
                .single().measure(constraints.copy(minHeight = 0))
            val height = constraints.constrainHeight(visibleRows.height)
            val scrollableRows = subcompose("scrollableRows") {
                rows(Modifier.verticalScroll(scrollState), Int.MAX_VALUE)
            }.single().measure(constraints.copy(minHeight = height, maxHeight = height))
            layout(scrollableRows.width, height) {
                scrollableRows.placeRelative(0, 0)
            }
        }
    }
}
