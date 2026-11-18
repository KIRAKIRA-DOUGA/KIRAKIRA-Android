package moe.kirakira.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val PointerEntryScale = 0.72f

/** Read a single-pointer update. The navigation bar owns its entire touch stream. */
internal suspend fun AwaitPointerEventScope.awaitSlidingSelectionChange(
    pointerId: PointerId,
    consumeEvent: Boolean = false,
): PointerInputChange? {
    val event = awaitPointerEvent(PointerEventPass.Initial)
    val change = event.changes.firstOrNull { it.id == pointerId } ?: return null
    val slop = viewConfiguration.touchSlop
    val cancelled = change.isConsumed ||
        event.changes.any { it.id != pointerId && it.pressed } ||
        change.position.y < -slop || change.position.y > size.height + slop
    if (consumeEvent) event.changes.forEach { it.consume() }
    return change.takeUnless { cancelled }
}

@Composable
internal fun rememberSlidingSelection(selectedIndex: Int?): SlidingSelection {
    val motion = remember { SlidingSelection(selectedIndex) }
    val emphasized = rememberEmphasizedEasing()
    val leading = remember(emphasized) { Easing { TabIndicatorLeadingEasing.transform(emphasized.transform(it)) } }
    val trailing = remember(emphasized) { Easing { TabIndicatorTrailingEasing.transform(emphasized.transform(it)) } }
    val enterSpec = tween<Float>(durationMillis = 150, easing = LinearOutSlowInEasing)
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val followingPointer = motion.isFollowingPointer
    val target = if (followingPointer) null else motion.pressedPosition ?: selectedIndex?.toFloat()
    val dragging = !followingPointer && motion.isDragging
    val settlement = motion.dragSettlement
    LaunchedEffect(motion, target, followingPointer, dragging, settlement, leading, trailing, exitSpec) {
        if (followingPointer) {
            motion.preparePointerVisibility()
            motion.animateVisibility(visible = true, enterSpec)
        } else {
            val releasedPointer = motion.finishPointerFollow()
            if (target == null) {
                motion.animateVisibility(visible = false, exitSpec)
            } else {
                // Position a hidden indicator before the first visible frame.
                if (motion.visibilityScale == 0f) motion.snapTo(target)
                coroutineScope {
                    launch { motion.animateVisibility(visible = true, enterSpec) }
                    motion.animate(target, dragging, releasedPointer || settlement == selectedIndex, leading, trailing)
                }
            }
        }
        if (!dragging && motion.dragSettlement == settlement) motion.clearSettlement()
    }
    return motion
}

@Composable
internal fun rememberSlidingSelectionPressProgress(pressed: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (pressed) 1f else 0f,
    animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
    label = "Selection indicator press",
)

internal class SlidingSelection(initialIndex: Int?) {
    private val start = Animatable(initialIndex?.toFloat() ?: 0f, visibilityThreshold = 0.001f)
    private val end = Animatable(start.value, visibilityThreshold = 0.001f)
    private val visibility = Animatable(if (initialIndex != null) 1f else 0f, visibilityThreshold = 0.001f)
    private var pointerPosition by mutableStateOf<Float?>(null)
    val visibilityScale: Float
        get() = visibility.value.coerceIn(if (pointerPosition != null) PointerEntryScale else 0f, 1.02f)
    private var dragPosition by mutableFloatStateOf(0f)
    var pressedPosition by mutableStateOf<Float?>(null)
        private set
    var isFollowingPointer by mutableStateOf(false)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var dragSettlement by mutableStateOf<Int?>(null)
        private set

    fun press(position: Float, fromEmptySelection: Boolean = false) {
        pressedPosition = position
        dragSettlement = null
        isFollowingPointer = fromEmptySelection
        updatePointerPreview(position)
    }

    fun updatePointerPreview(position: Float) {
        if (isFollowingPointer) pointerPosition = position
    }

    fun dragTo(position: Float) {
        if (isFollowingPointer) updatePointerPreview(position) else dragPosition = position
        isDragging = true
    }

    fun release(settleIndex: Int?) {
        if (isDragging) dragSettlement = settleIndex
        isDragging = false
        isFollowingPointer = false
        pressedPosition = null
    }

    fun clearSettlement() {
        dragSettlement = null
    }

    suspend fun snapTo(position: Float) {
        start.snapTo(position)
        end.snapTo(position)
    }

    suspend fun preparePointerVisibility() {
        if (visibility.value < PointerEntryScale) visibility.snapTo(PointerEntryScale)
    }

    suspend fun finishPointerFollow(): Boolean {
        val position = pointerPosition ?: return false
        // Keep drawing at the last touch position until the settling animation can take over.
        preparePointerVisibility()
        snapTo(position)
        if (!isFollowingPointer) pointerPosition = null
        return true
    }

    suspend fun animateVisibility(visible: Boolean, spec: FiniteAnimationSpec<Float>) {
        val target = if (visible) 1f else 0f
        if (visibility.value != target) visibility.animateTo(target, spec)
    }

    suspend fun animate(target: Float, dragging: Boolean, settle: Boolean, leading: Easing, trailing: Easing) {
        if (dragging) {
            val followSpec = spring<Float>(dampingRatio = 1f, stiffness = 1600f, visibilityThreshold = 0.001f)
            coroutineScope {
                snapshotFlow { dragPosition }.collect { position ->
                    launch { start.animateTo(position, followSpec) }
                    launch { end.animateTo(position, followSpec) }
                }
            }
        } else if (start.value != target || end.value != target) {
            val center = (start.value + end.value) / 2f
            val duration = tabTransitionDurationMillis(abs(target - center))
            val forward = target >= center
            val settleSpec = spring<Float>(dampingRatio = 1f, stiffness = 600f, visibilityThreshold = 0.001f)
            val startSpec = if (settle) settleSpec else tween(duration, easing = if (forward) trailing else leading)
            val endSpec = if (settle) settleSpec else tween(duration, easing = if (forward) leading else trailing)
            coroutineScope {
                launch { start.animateTo(target, startSpec) }
                launch { end.animateTo(target, endSpec) }
            }
        }
    }

    fun bounds(lastIndex: Int, pressProgress: Float, slot: (Float) -> Rect?): Rect? {
        val visibility = visibilityScale
        if (visibility == 0f) return null
        val pointer = pointerPosition
        val startPosition = (pointer ?: start.value).coerceIn(0f, lastIndex.toFloat())
        val endPosition = (pointer ?: end.value).coerceIn(startPosition, lastIndex.toFloat())
        val first = slot(startPosition) ?: return null
        val last = slot(endPosition) ?: return null
        val left = minOf(first.left, last.left)
        val right = maxOf(first.right, last.right)
        val scale = visibility * (1f - pressProgress.coerceIn(0f, 1f) * 0.1f)
        val inset = (1f - scale) / 2f
        val insetX = (right - left) * inset
        val insetY = first.height * inset
        return Rect(left + insetX, first.top + insetY, right - insetX, first.bottom - insetY)
    }
}

internal fun Modifier.slidingSelectionIndicator(
    shape: Shape,
    bounds: DrawScope.() -> Rect?,
    containerColor: () -> Color,
    contentColor: Color?,
): Modifier = drawWithCache {
    val mask = Path()
    val foregroundPaint = Paint()
    val foregroundBounds = Rect(Offset.Zero, size)
    onDrawWithContent {
        val indicator = bounds()
        if (indicator == null || indicator.isEmpty) {
            drawContent()
            return@onDrawWithContent
        }
        mask.rewind()
        mask.addOutline(shape.createOutline(indicator.size, layoutDirection, this))
        mask.translate(indicator.topLeft)
        drawPath(mask, containerColor())
        if (contentColor == null) {
            drawContent()
            return@onDrawWithContent
        }
        // Tint only the foreground pixels covered by the indicator.
        val canvas = drawContext.canvas
        canvas.saveLayer(foregroundBounds, foregroundPaint)
        try {
            drawContent()
            clipPath(mask) { drawRect(color = contentColor, blendMode = BlendMode.SrcAtop) }
        } finally {
            canvas.restore()
        }
    }
}
