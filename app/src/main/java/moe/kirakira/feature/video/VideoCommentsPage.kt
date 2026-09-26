package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun VideoCommentsPage(
    listState: LazyListState,
    bottomPadding: Dp,
    onUnavailableAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sort by rememberSaveable { mutableStateOf(CommentSort.TIME) }
    var ascending by rememberSaveable { mutableStateOf(false) }
    var votes by rememberSaveable { mutableStateOf(IntArray(demoVideoComments.size)) }
    var showJumpDialog by rememberSaveable { mutableStateOf(false) }
    val comments = remember(sort, ascending, votes) {
        val comparator = when (sort) {
            CommentSort.TIME -> compareBy<DemoVideoComment> { it.createdAt }
            CommentSort.SCORE -> compareBy { it.score + votes[it.floor - 1] }
        }.thenBy { it.floor }
        demoVideoComments.sortedWith(if (ascending) comparator else comparator.reversed())
    }
    val density = LocalDensity.current
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val toolbarClearance = with(density) { toolbarSize.height.toDp() } + 16.dp
    val toolbarClearancePx = with(density) { toolbarClearance.roundToPx() }
    val listTopPadding = 8.dp
    val jumpScrollOffset = with(density) { (listTopPadding - toolbarClearance).roundToPx() }
    val totalPages = (comments.size + COMMENTS_PER_PAGE - 1) / COMMENTS_PER_PAGE
    val currentPage by remember(listState, totalPages, toolbarClearancePx, comments.size) {
        derivedStateOf {
            val layout = listState.layoutInfo
            // Item offsets use the content-padding origin; convert the overlay's lower edge.
            val visibleStart = layout.viewportStartOffset + toolbarClearancePx
            val firstComment = layout.visibleItemsInfo.firstOrNull {
                it.key is Int && it.offset + it.size > visibleStart && it.offset < layout.viewportEndOffset
            }
            val commentIndex = ((firstComment?.index ?: listState.firstVisibleItemIndex) - 1)
                .coerceIn(0, comments.lastIndex)
            (commentIndex / COMMENTS_PER_PAGE + 1).coerceIn(1, totalPages)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = listTopPadding,
                bottom = bottomPadding + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item(key = "comments_count") {
                CommentCountHeader(count = comments.size, toolbarSize = toolbarSize)
            }
            itemsIndexed(comments, key = { _, comment -> comment.floor }) { index, comment ->
                VideoCommentItem(
                    comment = comment,
                    index = index,
                    count = comments.size,
                    vote = votes[comment.floor - 1],
                    onVote = { value ->
                        votes = votes.copyOf().apply {
                            val index = comment.floor - 1
                            this[index] = if (this[index] == value) 0 else value
                        }
                    },
                    onReply = onUnavailableAction,
                    onMore = onUnavailableAction,
                )
            }
            item(key = "comments_end") {
                Text(
                    text = "·",
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        CommentToolbar(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .onSizeChanged { toolbarSize = it },
            currentPage = currentPage,
            totalPages = totalPages,
            sort = sort,
            ascending = ascending,
            onOpenJump = { showJumpDialog = true },
            onSortChange = { newSort, newAscending ->
                if (newSort != sort || newAscending != ascending) {
                    sort = newSort
                    ascending = newAscending
                    // 显式回到新排序的第一页，避免稳定 key 自动保留旧位置。
                    listState.requestScrollToItem(0)
                }
            },
        )
    }
    if (showJumpDialog) {
        CommentJumpDialog(
            currentPage = currentPage,
            totalPages = totalPages,
            maxFloor = demoVideoComments.size,
            onDismiss = { showJumpDialog = false },
            onJump = { byFloor, value ->
                val index = if (byFloor) {
                    comments.indexOfFirst { it.floor == value }
                } else {
                    (value - 1) * COMMENTS_PER_PAGE
                }
                if (index in comments.indices) {
                    // The header adds one item; the negative offset clears the floating toolbar
                    // now that the list itself has only a compact top inset.
                    listState.requestScrollToItem(index + 1, scrollOffset = jumpScrollOffset)
                    showJumpDialog = false
                }
            },
        )
    }
}

@Composable
private fun CommentCountHeader(count: Int, toolbarSize: IntSize) {
    val label = pluralStringResource(R.plurals.video_comments_total, count, count)
    val style = MaterialTheme.typography.bodyMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = with(density) { textMeasurer.measure(label, style).size.width.toDp() }
    val toolbarWidth = with(density) { toolbarSize.width.toDp() }
    val toolbarHeight = with(density) { toolbarSize.height.toDp() }

    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // Measure the localized label so narrow layouts and large fonts stack without overlap.
        val inline = toolbarSize.width > 0 && labelWidth + toolbarWidth + 24.dp <= maxWidth
        Text(
            text = label,
            modifier = if (inline) {
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = toolbarWidth + 16.dp)
                    .heightIn(min = toolbarHeight)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .semantics { heading() }
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = toolbarHeight + 8.dp, bottom = 8.dp)
                    .semantics { heading() }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = style,
        )
    }
}

@Preview(name = "评论 · 中文", locale = "zh", widthDp = 412, heightDp = 640)
@Preview(name = "Comments · English", locale = "en", widthDp = 412, heightDp = 640)
@Preview(name = "Comments · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 640)
@Preview(name = "Comments · Wide", locale = "en", widthDp = 840, heightDp = 640)
@Preview(name = "Comments · Large text", locale = "zh", fontScale = 2f, widthDp = 320, heightDp = 640)
@Composable
private fun VideoCommentsPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        Surface {
            VideoCommentsPage(rememberLazyListState(), 0.dp, onUnavailableAction = {})
        }
    }
}
