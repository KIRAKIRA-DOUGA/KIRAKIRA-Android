package moe.kirakira.feature.video

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.adaptive.AppWindowLayout
import moe.kirakira.ui.adaptive.PanePair
import moe.kirakira.ui.adaptive.intersectPane

internal data class VideoPaneLayout(
    val viewing: IntRect? = null,
    val details: IntRect? = null,
    val stacked: Boolean = true,
    val tabletop: Boolean = false,
    val introductionColumn: IntRect? = null,
)

internal fun Density.videoPaneLayout(
    window: AppWindowLayout,
    direction: LayoutDirection,
): VideoPaneLayout {
    val landscape = window.size.width > window.size.height
    val safe = window.safeBounds
    val primary = window.primaryPane
    if (primary != null) {
        val panes = window.foldPanes
        if (panes != null) {
            val (first, second) = panes.within(safe)
            val minWidth = 320.dp.roundToPx()
            if (minOf(first.width, second.width) >= minWidth) {
                if (panes.sideBySide && minOf(first.height, second.height) >= 320.dp.roundToPx()) {
                    return panes.videoColumns(safe, landscape)
                }
                if (!panes.sideBySide && window.tabletop &&
                    first.height >= 144.dp.roundToPx() && second.height >= 200.dp.roundToPx()
                ) return VideoPaneLayout(first, second, stacked = false, tabletop = true)
            }
        }
        val pane = primary.intersectPane(safe)
        return VideoPaneLayout(pane, pane)
    }
    if (safe.width < 840.dp.roundToPx() || safe.height < 480.dp.roundToPx()) return VideoPaneLayout()
    val gap = if (landscape) 0 else 16.dp.roundToPx()
    val detailsWidth = ((safe.width - gap) * 0.4f).toInt().coerceAtLeast(320.dp.roundToPx())
    val columns = window.columns(safe.width - gap - detailsWidth, direction, gap, edgeToEdge = landscape)
    return columns.videoColumns(safe, landscape)
}

private fun PanePair.videoColumns(safe: IntRect, landscape: Boolean) = VideoPaneLayout(
    viewing = first.intersectPane(safe),
    details = if (landscape) second else second.intersectPane(safe),
    stacked = false,
    introductionColumn = first.takeIf { landscape },
)
