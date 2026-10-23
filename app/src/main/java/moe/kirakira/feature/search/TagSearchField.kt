package moe.kirakira.feature.search

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.content.VideoTag

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
) {
    SearchFieldContainer(modifier = modifier) {
        Row(
            modifier = Modifier
                .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
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
                Icon(painterResource(R.drawable.ic_symbol_label), contentDescription = null)
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
                    modifier = Modifier
                        .weight(1f)
                        // One 48dp chip row plus this padding keeps the field at the shared input height.
                        .padding(vertical = 4.dp)
                        .heightIn(max = SearchBarDefaults.InputFieldHeight * 2)
                        .verticalScroll(rememberScrollState()),
                    enabled = enabled,
                )
            }
            SearchIconButton(
                stringResource(R.string.search_choose_tags), R.drawable.ic_symbol_add,
                onClick = onAdd, enabled = enabled,
            )
            if (showClear) {
                SearchIconButton(
                    clearDescription, R.drawable.ic_symbol_close, onClick = onClear, enabled = enabled,
                )
            }
        }
    }
}
