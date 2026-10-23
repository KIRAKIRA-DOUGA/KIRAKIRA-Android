package moe.kirakira.feature.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ExpressiveMenuPopup
import moe.kirakira.ui.components.video.VideoCardLayout

@Composable
internal fun SearchResultControls(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun SearchSortMenu(state: SearchUiState, onEvent: (SearchEvent) -> Unit) {
    var expanded by remember(state.generation, state.ready) { mutableStateOf(false) }
    val itemColors = MenuDefaults.selectableItemColors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        selectedTrailingContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    Box {
        SearchIconButton(
            label = stringResource(state.sort.label),
            icon = R.drawable.ic_symbol_sort,
            onClick = { expanded = true },
            enabled = state.ready,
        )
        ExpressiveMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val sorts = SearchSort.entries
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                sorts.forEachIndexed { index, sort ->
                    val isSelected = state.sort == sort
                    SelectableDropdownMenuItem(
                        selected = isSelected,
                        text = { Text(stringResource(sort.label)) },
                        onClick = { onEvent(SearchEvent.SortChanged(sort)) },
                        shapes = MenuDefaults.itemShape(index = index, count = sorts.size),
                        colors = itemColors,
                        enabled = state.ready,
                        trailingContent = if (sort == SearchSort.DEFAULT) null else {
                            {
                                val descending = !isSelected || state.descending
                                val rotation = animateFloatAsState(
                                    targetValue = if (descending) 0f else 180f,
                                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                    label = "searchSortDirection",
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_symbol_arrow_downward),
                                    contentDescription = stringResource(
                                        if (descending) R.string.search_sort_descending else R.string.search_sort_ascending,
                                    ),
                                    modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
