package moe.kirakira.feature.video

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun DanmakuListItem(
    time: String,
    text: String,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(
            index = index,
            count = count,
            defaultShapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraSmall),
        ),
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            SelectionContainer {
                Text(
                    text = time,
                    modifier = Modifier.widthIn(min = 56.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
    ) {
        SelectionContainer(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
    }
}
