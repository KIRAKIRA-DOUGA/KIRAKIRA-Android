package moe.kirakira.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Panes are in reading order for columns and top-to-bottom for rows. */
internal data class PanePair(val first: IntRect, val second: IntRect) {
    val sideBySide: Boolean get() = first.right <= second.left || second.right <= first.left

    fun within(bounds: IntRect) = PanePair(first.intersectPane(bounds), second.intersectPane(bounds))
}

internal data class AppWindowLayout(
    val size: IntSize = IntSize.Zero,
    val safeBounds: IntRect = IntRect.Zero,
    val primaryPane: IntRect? = null,
    val foldPanes: PanePair? = null,
    val tabletop: Boolean = false,
    val bookFold: Int? = null,
) {
    /** Ordinary pages share the full surface across a non-occluding book fold. */
    val pagePane: IntRect? get() = primaryPane.takeIf { bookFold == null }
    val safePageBounds: IntRect get() = pagePane?.intersectPane(safeBounds) ?: safeBounds

    /** Position the split in the safe area. Surfaces can extend to the outer window edges. */
    fun columns(startWidth: Int, direction: LayoutDirection, gap: Int = 0, edgeToEdge: Boolean = false): PanePair {
        val bounds = if (edgeToEdge) IntRect(0, 0, size.width, size.height) else safeBounds
        return if (direction == LayoutDirection.Ltr) {
            val x = safeBounds.left + startWidth
            PanePair(bounds.copy(right = x), bounds.copy(left = x + gap))
        } else {
            val x = safeBounds.right - startWidth
            PanePair(bounds.copy(left = x), bounds.copy(right = x - gap))
        }
    }
}

internal val LocalAppWindowLayout = compositionLocalOf { AppWindowLayout() }
// A separate dialog window inherits the source pane, but starts with its own unconsumed insets.
internal val LocalWindowPane = compositionLocalOf<IntRect?> { null }

@Composable
internal fun rememberAppWindowLayout(windowOrigin: Offset): AppWindowLayout {
    val size = LocalWindowInfo.current.containerSize
    val safe = safeWindowRect(size)
    val posture = if (LocalInspectionMode.current) null else currentWindowAdaptiveInfoV2().windowPosture
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(size, safe, posture, windowOrigin, rtl) {
        val window = IntRect(0, 0, size.width, size.height)
        var panes = listOf(window)
        var occluded = false
        for (hinge in posture?.hingeList.orEmpty()) {
            // A flat, unobstructed display can lay out content continuously across the crease.
            if (!hinge.isOccluding && (hinge.isFlat || !hinge.isSeparating)) continue
            val bounds = hinge.bounds.translate(-windowOrigin)
            if (!window.intersectsHinge(bounds)) continue
            occluded = occluded || hinge.isOccluding
            panes = panes.flatMap { pane ->
                if (pane.intersectsHinge(bounds)) pane.splitAt(bounds, hinge.isVertical, rtl) else listOf(pane)
            }
        }
        val split = if (panes.size == 2) PanePair(panes[0], panes[1]) else null
        val bookFold = if (split != null && !occluded) split.first.sharedColumnEdge(split.second) else null
        // Prefer the first pane in reading order when two areas tie.
        val primary = panes.takeUnless { it.singleOrNull() == window }
            ?.maxByOrNull { it.width.toLong() * it.height }
        AppWindowLayout(size, safe, primary, split, posture?.isTabletop == true, bookFold)
    }
}

private fun IntRect.intersectsHinge(hinge: Rect): Boolean =
    hinge.right > left && hinge.left < right && hinge.bottom > top && hinge.top < bottom

private fun IntRect.splitAt(hinge: Rect, vertical: Boolean, rtl: Boolean): List<IntRect> {
    val start = if (vertical) hinge.left else hinge.top
    val end = if (vertical) hinge.right else hinge.bottom
    // Round a zero-width crease once. Only a hinge with area can remove pixels.
    val before = if (start == end) start.roundToInt() else floor(start).toInt()
    val after = if (start == end) before else ceil(end).toInt()
    val pieces = if (vertical) {
        val leftPane = copy(right = before.coerceIn(left, right))
        val rightPane = copy(left = after.coerceIn(left, right))
        if (rtl) listOf(rightPane, leftPane) else listOf(leftPane, rightPane)
    } else {
        listOf(copy(bottom = before.coerceIn(top, bottom)), copy(top = after.coerceIn(top, bottom)))
    }
    return pieces.filter { it.width > 0 && it.height > 0 }
}

/** Remove only the insets already excluded by the pane; pages still own their remaining insets. */
@Composable
internal fun WindowPane(
    pane: IntRect?,
    modifier: Modifier = Modifier,
    clipContent: Boolean = true,
    content: @Composable () -> Unit,
) {
    val window = LocalAppWindowLayout.current
    val ownerPane = pane ?: LocalWindowPane.current
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            val bounds = if (pane != null && clipContent) Modifier.clipToBounds() else Modifier
            val insets = if (pane == null) Modifier else Modifier.consumeWindowInsets(pane.excludedInsets(window.size))
            Box(bounds.then(insets)) {
                CompositionLocalProvider(LocalWindowPane provides ownerPane, content = content)
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val rect = (pane ?: IntRect(0, 0, width, height)).clampTo(width, height)
        val child = measurables.single().measure(Constraints.fixed(rect.width, rect.height))
        layout(width, height) { child.place(rect.left, rect.top) }
    }
}

internal fun IntRect.excludedInsets(window: IntSize): WindowInsets = WindowInsets(
    left = left.coerceAtLeast(0),
    top = top.coerceAtLeast(0),
    right = (window.width - right).coerceAtLeast(0),
    bottom = (window.height - bottom).coerceAtLeast(0),
)

internal fun IntRect.sharedColumnEdge(other: IntRect): Int? = when {
    right == other.left -> right
    left == other.right -> left
    else -> null
}

internal fun IntRect.clampTo(width: Int, height: Int) = IntRect(
    left.coerceIn(0, width), top.coerceIn(0, height), right.coerceIn(0, width), bottom.coerceIn(0, height),
)

/** Stable insets for deciding a layout. IME insets are applied separately to the controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun safeWindowRect(size: IntSize): IntRect {
    val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val left = insets.getLeft(density, direction).coerceIn(0, size.width)
    val top = insets.getTop(density).coerceIn(0, size.height)
    return IntRect(left, top, (size.width - insets.getRight(density, direction)).coerceAtLeast(left),
        (size.height - insets.getBottom(density)).coerceAtLeast(top))
}

internal fun IntRect.intersectPane(other: IntRect): IntRect {
    val x = maxOf(left, other.left)
    val y = maxOf(top, other.top)
    return IntRect(x, y, minOf(right, other.right).coerceAtLeast(x), minOf(bottom, other.bottom).coerceAtLeast(y))
}
