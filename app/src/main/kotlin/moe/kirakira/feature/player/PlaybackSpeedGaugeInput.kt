package moe.kirakira.feature.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import moe.kirakira.feature.player.PlaybackSpeedRules.MAX_ANGLE
import moe.kirakira.feature.player.PlaybackSpeedRules.ZERO_ANGLE

internal data class PlaybackSpeedGaugeGeometry(val center: Offset, val radius: Float) {
    val needleLength = radius * 0.68f
}

/** Claim presses near the arc or needle, leaving the rest of the sheet available for scrolling. */
internal fun Modifier.playbackSpeedDrag(
    width: Float,
    geometry: () -> PlaybackSpeedGaugeGeometry,
    displayedNeedleAngle: () -> Float,
    onDraggingChange: (Boolean) -> Unit,
    onSpeed: (Float) -> Unit,
): Modifier = pointerInput(width) {
    val arcTouchRadius = 24.dp.toPx()
    val needleTouchRadius = 12.dp.toPx()
    val needlePivotRadius = 8.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        // Keep the angular reference stable if the label animation moves the pivot mid-drag.
        val gauge = geometry()
        val (center, radius) = gauge
        if (radius <= 0f) return@awaitEachGesture
        val offset = down.position - center
        val downAngle = pointerAngle(offset)
        val arcAngle = (if (downAngle < 90f) downAngle + 360f else downAngle).coerceIn(ZERO_ANGLE, MAX_ANGLE)
        val needleAngle = displayedNeedleAngle().coerceIn(ZERO_ANGLE, MAX_ANGLE)
        val tip = gaugePoint(Offset.Zero, gauge.needleLength, needleAngle)
        val fraction = ((offset.x * tip.x + offset.y * tip.y) / tip.getDistanceSquared()).coerceIn(0f, 1f)
        val onNeedle = offset.getDistance() >= needlePivotRadius &&
            (offset - tip * fraction).getDistance() <= needleTouchRadius
        val arcPoint = gaugePoint(Offset.Zero, radius, arcAngle)
        if (!onNeedle && (offset - arcPoint).getDistance() > arcTouchRadius) return@awaitEachGesture

        val pivotRadius = if (onNeedle) needlePivotRadius else arcTouchRadius
        var angle = if (onNeedle) needleAngle else arcAngle
        var previousAngle: Float? = downAngle
        var dragging = false
        down.consume()
        try {
            if (!onNeedle) {
                dragging = true
                onDraggingChange(true)
                onSpeed(PlaybackSpeedRules.speedAtAngle(angle))
            }
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed || event.changes.count { it.pressed } > 1) break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                val position = change.position - center
                // Rebase the touch angle after crossing the pivot, where direction is undefined.
                if (position.getDistance() >= pivotRadius) {
                    val touchAngle = pointerAngle(position)
                    val delta = previousAngle?.let { (touchAngle - it + 540f) % 360f - 180f } ?: 0f
                    previousAngle = touchAngle
                    val nextAngle = (angle + delta).coerceIn(ZERO_ANGLE, MAX_ANGLE)
                    if (nextAngle != angle) {
                        angle = nextAngle
                        if (!dragging) {
                            dragging = true
                            onDraggingChange(true)
                        }
                        onSpeed(PlaybackSpeedRules.speedAtAngle(angle))
                    }
                } else {
                    previousAngle = null
                }
                change.consume()
            }
        } finally {
            if (dragging) onDraggingChange(false)
        }
    }
}

private fun pointerAngle(offset: Offset): Float =
    (Math.toDegrees(atan2(offset.y, offset.x).toDouble()).toFloat() + 360f) % 360f
