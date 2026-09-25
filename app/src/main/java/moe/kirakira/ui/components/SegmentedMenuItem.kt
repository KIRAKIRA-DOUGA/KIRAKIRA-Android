package moe.kirakira.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import moe.kirakira.R

@Composable
fun SegmentedMenuItem(
    title: String,
    @DrawableRes icon: Int,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
    destructive: Boolean = false,
) {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val leadingContent: @Composable () -> Unit = {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val trailingContent: (@Composable () -> Unit)? = if (showChevron) {
        { Icon(painterResource(R.drawable.ic_symbol_chevron_right), contentDescription = null) }
    } else null
    val content: @Composable () -> Unit = {
        Text(title, color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified)
    }

    if (onClick == null) {
        // Static destinations must not expose a click action before they are implemented.
        SegmentedListItem(
            shapes = shapes,
            modifier = modifier.fillMaxWidth(),
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            content = content,
        )
    } else {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier.fillMaxWidth(),
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            content = content,
        )
    }
}
