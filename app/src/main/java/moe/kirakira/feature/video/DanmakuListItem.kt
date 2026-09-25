package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

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

@Preview(name = "弹幕列表 · 中文", locale = "zh", widthDp = 360)
@Preview(name = "Danmaku · English", locale = "en", widthDp = 360)
@Preview(name = "Danmaku · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Preview(name = "Danmaku · Large text", locale = "zh", fontScale = 2f, widthDp = 320)
@Composable
private fun DanmakuListPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                VideoListCount(R.plurals.video_danmaku_total, demoVideoDanmaku.size)
                demoVideoDanmaku.forEachIndexed { index, entry ->
                    DanmakuListItem(
                        time = stringResource(R.string.video_danmaku_time, entry.seconds / 60, entry.seconds % 60),
                        text = stringResource(entry.bodyRes),
                        index = index,
                        count = demoVideoDanmaku.size,
                    )
                }
            }
        }
    }
}
