package moe.kirakira.ui.components.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.adaptive.LocalAppWindowLayout
import moe.kirakira.ui.adaptive.browsePageWidth

private val MinVideoColumnWidth = 128.dp

@Composable
internal fun VideoFeedGrid(
    layout: VideoCardLayout,
    state: LazyGridState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyGridScope.(columns: Int) -> Unit,
) {
    BoxWithConstraints(modifier.browsePageWidth().fillMaxSize()) {
        val direction = LocalLayoutDirection.current
        val startPadding = contentPadding.calculateStartPadding(direction)
        val endPadding = contentPadding.calculateEndPadding(direction)
        val available = maxWidth - startPadding - endPadding
        val density = LocalDensity.current
        val window = LocalAppWindowLayout.current
        val fold = window.bookFold?.let { seam ->
            // The grid ends at the Scaffold's safe end edge. Measuring from there also accounts
            // for a rail of any width without depending on how navigation lays out the start edge.
            val insets = ScaffoldDefaults.contentWindowInsets
            val endWidth = with(density) {
                if (direction == LayoutDirection.Ltr) {
                    (window.size.width - seam - insets.getRight(this, direction)).toDp()
                } else {
                    (seam - insets.getLeft(this, direction)).toDp()
                }
            }
            (maxWidth - endWidth - startPadding).takeIf {
                minOf(it, available - it) >= MinVideoColumnWidth + VideoCardRowSpacing / 2
            }
        }
        val ordinaryColumns = when {
            layout == VideoCardLayout.LIST -> if (available / density.fontScale >= 840.dp) 2 else 1
            available >= 840.dp -> 4
            available >= 600.dp -> 3
            else -> 2
        }
        val foldLayout = fold?.let {
            val minTwoColumnWidth = MinVideoColumnWidth * 2 + VideoCardRowSpacing * 1.5f
            FoldVideoGridLayout(
                fold = it,
                layout = layout,
                startColumns = if (layout == VideoCardLayout.GRID && it >= minTwoColumnWidth) 2 else 1,
                endColumns = if (layout == VideoCardLayout.GRID && available - it >= minTwoColumnWidth) 2 else 1,
            )
        }
        LazyVerticalGrid(
            columns = foldLayout ?: GridCells.Fixed(ordinaryColumns),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = foldLayout ?: Arrangement.spacedBy(VideoCardRowSpacing),
            content = { content(foldLayout?.columns ?: ordinaryColumns) },
        )
    }
}

private data class FoldVideoGridLayout(
    val fold: Dp,
    val layout: VideoCardLayout,
    val startColumns: Int,
    val endColumns: Int,
) : GridCells, Arrangement.Horizontal {
    val columns: Int get() = startColumns + endColumns
    override val spacing: Dp get() = VideoCardRowSpacing

    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        // Reserve at least half the standard gap on each side of the fold.
        val startWidth = fold.roundToPx() - spacing / 2
        val endWidth = availableSize - startWidth - spacing
        if (layout == VideoCardLayout.LIST) return listOf(startWidth, endWidth)

        val startCellWidth = (startWidth - spacing * (startColumns - 1)) / startColumns
        val endCellWidth = (endWidth - spacing * (endColumns - 1)) / endColumns
        val cellWidth = minOf(startCellWidth, endCellWidth)
        return List(columns) { cellWidth }
    }

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) {
        // LazyVerticalGrid mirrors placement for RTL. Anchor each group at its outer edge so the
        // extra width stays at the fold and full-span items still fill the grid.
        val gap = spacing.roundToPx()
        var start = 0
        for (index in 0 until startColumns) {
            outPositions[index] = start
            start += sizes[index] + gap
        }
        var end = totalSize
        for (index in sizes.lastIndex downTo startColumns) {
            end -= sizes[index]
            outPositions[index] = end
            end -= gap
        }
    }
}
