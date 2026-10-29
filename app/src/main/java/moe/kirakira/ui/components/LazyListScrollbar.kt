@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NonInteractiveScrollbarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Draggable scrollbar for forward lists without sticky headers. Callers provide track placement
 * and visibility. Intermediate positions use average item height; endpoints target the boundary items.
 */
@Composable
internal fun LazyListScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val restingThickness = NonInteractiveScrollbarDefaults.Thickness
    val minLength = NonInteractiveScrollbarDefaults.ThumbMinLength
    val pressState = remember(state) { MutableTransitionState(false) }
    val transition = rememberTransition(pressState, label = "lazyListScrollbarPress")
    val motion = MaterialTheme.motionScheme
    val thickness = transition.animateDp(
        transitionSpec = { motion.fastSpatialSpec() },
        label = "thumbThickness",
    ) { isPressed -> if (isPressed) restingThickness * 2 else restingThickness }
    val color = transition.animateColor(
        transitionSpec = { motion.fastEffectsSpec() },
        label = "thumbColor",
    ) { isPressed ->
        if (isPressed) colors.primary.copy(alpha = 1f)
        else colors.outline.copy(alpha = NonInteractiveScrollbarDefaults.ThumbOpacity)
    }
    var dragProgress by remember(state) { mutableStateOf<Float?>(null) }

    Box(
        modifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(state.scrollbarMetrics().progress, 0f..1f)
                setProgress { progress ->
                    state.seekScrollProgress(progress)
                    true
                }
            }
            .pointerInput(state) {
                // Observe presses without consuming events or competing with vertical dragging.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressState.targetState = true
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        pressState.targetState = false
                    }
                }
            }
            .pointerInput(state) {
                var pointerY = 0f
                var grabOffset = 0f
                var travel = 0f
                detectVerticalDragGestures(
                    onDragStart = { position ->
                        val metrics = state.scrollbarMetrics()
                        val height = size.height.toFloat()
                        val thumbLength = (height * metrics.visibleFraction)
                            .coerceIn(minLength.toPx().coerceAtMost(height), height)
                        travel = height - thumbLength
                        val thumbTop = travel * metrics.progress
                        pointerY = position.y
                        grabOffset = if (pointerY in thumbTop..(thumbTop + thumbLength)) {
                            pointerY - thumbTop
                        } else {
                            thumbLength / 2f
                        }
                        if (travel > 0f) {
                            val progress = ((pointerY - grabOffset) / travel).coerceIn(0f, 1f)
                            dragProgress = progress
                            state.seekScrollProgress(progress)
                        }
                    },
                    onDragEnd = { dragProgress = null },
                    onDragCancel = { dragProgress = null },
                ) { change, amount ->
                    change.consume()
                    pointerY += amount
                    if (travel > 0f) {
                        val progress = ((pointerY - grabOffset) / travel).coerceIn(0f, 1f)
                        dragProgress = progress
                        state.seekScrollProgress(progress)
                    }
                }
            }
            .drawBehind {
                val metrics = state.scrollbarMetrics()
                val thumbLength = (size.height * metrics.visibleFraction)
                    .coerceIn(minLength.toPx().coerceAtMost(size.height), size.height)
                val width = thickness.value.toPx().coerceAtLeast(0f)
                val edgeInset = 2.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) size.width - edgeInset - width else edgeInset
                drawRoundRect(
                    color = color.value,
                    topLeft = Offset(x, (size.height - thumbLength) * (dragProgress ?: metrics.progress)),
                    size = Size(width, thumbLength),
                    cornerRadius = CornerRadius(width / 2f),
                )
            },
    )
}

private data class ListScrollMetrics(
    val itemSize: Float,
    val scrollRange: Float,
    val visibleFraction: Float,
    val progress: Float,
)

private fun LazyListState.scrollbarMetrics(): ListScrollMetrics {
    val info = layoutInfo
    val items = info.visibleItemsInfo
    val itemSize = if (items.isEmpty()) 1f else items.sumOf { it.size }.toFloat() / items.size
    val stride = (itemSize + info.mainAxisItemSpacing).coerceAtLeast(1f)
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val contentSize = stride * info.totalItemsCount - info.mainAxisItemSpacing +
        info.beforeContentPadding + info.afterContentPadding
    val range = (contentSize - viewport).coerceAtLeast(0f)
    val progress = when {
        !canScrollBackward || range == 0f -> 0f
        !canScrollForward -> 1f
        else -> ((firstVisibleItemIndex * stride + firstVisibleItemScrollOffset) / range).coerceIn(0f, 1f)
    }
    return ListScrollMetrics(
        itemSize = stride,
        scrollRange = range,
        visibleFraction = if (contentSize > 0f) (viewport / contentSize).coerceIn(0f, 1f) else 1f,
        progress = progress,
    )
}

private fun LazyListState.seekScrollProgress(progress: Float) {
    val count = layoutInfo.totalItemsCount
    if (count == 0) return
    val fraction = progress.coerceIn(0f, 1f)
    when (fraction) {
        0f -> requestScrollToItem(0)
        // LazyColumn clamps this request to its actual end, including trailing content padding.
        1f -> requestScrollToItem(count - 1)
        else -> {
            val metrics = scrollbarMetrics()
            val target = fraction * metrics.scrollRange
            val index = (target / metrics.itemSize).toInt().coerceIn(0, count - 1)
            requestScrollToItem(index, (target - index * metrics.itemSize).roundToInt())
        }
    }
}
