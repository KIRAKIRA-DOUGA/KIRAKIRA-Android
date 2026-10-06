package moe.kirakira.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val shapes = connectedListItemShapes(index = index, count = count)
    val leadingContent: @Composable () -> Unit = {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current,
        )
    }
    val trailingContent: (@Composable () -> Unit)? = if (showChevron) {
        { Icon(painterResource(R.drawable.ic_symbol_chevron_right), contentDescription = null, modifier = Modifier.size(24.dp)) }
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
