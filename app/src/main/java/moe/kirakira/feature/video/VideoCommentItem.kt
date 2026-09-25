package moe.kirakira.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import moe.kirakira.R
import moe.kirakira.ui.components.PlaceholderAvatar

@Composable
internal fun VideoCommentItem(
    comment: DemoVideoComment,
    index: Int,
    count: Int,
    vote: Int,
    onVote: (Int) -> Unit,
    onReply: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale) }
    val layoutDirection = LocalLayoutDirection.current
    val defaultPadding = ListItemDefaults.ContentPadding
    // 横向按 24dp 图标对齐正文；官方按钮仍以完整触摸尺寸居中，向布局槽两侧延伸。
    val actionButtonModifier = Modifier
        .width(24.dp)
        .wrapContentWidth(unbounded = true)
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(
            index = index,
            count = count,
            defaultShapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraSmall),
        ),
        modifier = modifier.fillMaxWidth(),
        leadingContent = { PlaceholderAvatar(size = 40.dp) },
        verticalAlignment = Alignment.Top,
        // 操作按钮已保留触摸区域，底部只补少量视觉留白。
        contentPadding = PaddingValues(
            start = defaultPadding.calculateStartPadding(layoutDirection),
            top = defaultPadding.calculateTopPadding(),
            end = defaultPadding.calculateEndPadding(layoutDirection),
        ),
    ) {
        Column(Modifier.fillMaxWidth()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SelectionContainer {
                    Column(Modifier.padding(end = 12.dp)) {
                        Text(
                            stringResource(R.string.video_comment_author, comment.author),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.video_comment_handle, comment.author),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SelectionContainer {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            dateFormatter.format(comment.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.video_comment_floor, comment.floor),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SelectionContainer {
                Text(
                    stringResource(comment.bodyRes),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.padding(end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconToggleButton(
                        checked = vote == 1,
                        onCheckedChange = { onVote(1) },
                        modifier = actionButtonModifier,
                    ) {
                        Icon(painterResource(R.drawable.ic_symbol_arrow_upward), stringResource(R.string.video_like))
                    }
                    Text(
                        stringResource(R.string.video_count, comment.score + vote),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconToggleButton(
                        checked = vote == -1,
                        onCheckedChange = { onVote(-1) },
                        modifier = actionButtonModifier,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_symbol_arrow_downward),
                            stringResource(R.string.video_dislike),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    IconButton(onClick = onReply, modifier = actionButtonModifier) {
                        Icon(painterResource(R.drawable.ic_symbol_reply), stringResource(R.string.video_comment_reply))
                    }
                    IconButton(onClick = onMore, modifier = actionButtonModifier) {
                        Icon(painterResource(R.drawable.ic_symbol_more_horiz), stringResource(R.string.video_more))
                    }
                }
            }
        }
    }
}
