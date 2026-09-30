package moe.kirakira.ui.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 随 Pager 的实际位置移动，并按 Material Components 的 Elastic 曲线拉伸。 */
@Composable
internal fun TabIndicatorScope.PagerTabIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    TabRowDefaults.PrimaryIndicator(
        modifier = modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
            if (tabPositions.isEmpty()) {
                return@tabIndicatorLayout layout(constraints.maxWidth, 0) {}
            }

            // 在测量阶段读取进度，滚动帧只触发布局，不重组整条 Tab 栏。
            val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, tabPositions.lastIndex.toFloat())
            val startIndex = position.toInt()
            val start = tabPositions[startIndex]
            val end = tabPositions[(startIndex + 1).coerceAtMost(tabPositions.lastIndex)]
            val fraction = position - startIndex
            val leadingFraction = sin(fraction * PI / 2).toFloat()
            val trailingFraction = (1 - cos(fraction * PI / 2)).toFloat()
            val startLeft = start.left + (start.width - start.contentWidth) / 2
            val endLeft = end.left + (end.width - end.contentWidth) / 2
            val left = lerp(startLeft, endLeft, trailingFraction).roundToPx()
            val right = lerp(
                startLeft + start.contentWidth,
                endLeft + end.contentWidth,
                leadingFraction,
            ).roundToPx()
            val width = right - left
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))

            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(left, 0)
            }
        },
        width = Dp.Unspecified,
    )
}
