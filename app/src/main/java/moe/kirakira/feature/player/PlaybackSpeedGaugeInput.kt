package moe.kirakira.feature.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Claim only presses near the arc, leaving the rest of the sheet available for scrolling. */
internal fun Modifier.playbackSpeedDrag(
    center: Offset,
    radius: Float,
    onDraggingChange: (Boolean) -> Unit,
    onSpeed: (Float) -> Unit,
): Modifier = pointerInput(center, radius) {
    val touchRadius = 24.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        val offset = down.position - center
        var previousAngle = pointerAngle(offset)
        var angle = (if (previousAngle < 90f) previousAngle + 360f else previousAngle).coerceIn(150f, 360f)
        val radians = Math.toRadians(angle.toDouble())
        val nearest = Offset(cos(radians).toFloat() * radius, sin(radians).toFloat() * radius)
        if (radius <= 0f || (offset - nearest).getDistance() > touchRadius) return@awaitEachGesture

        down.consume()
        onDraggingChange(true)
        try {
            onSpeed(speedAtAngle(angle))
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed || event.changes.count { it.pressed } > 1) break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                val position = change.position - center
                // Ignore the pivot, where direction is undefined and small movements cause large jumps.
                if (position.getDistance() >= touchRadius) {
                    val nextAngle = pointerAngle(position)
                    val delta = (nextAngle - previousAngle + 540f) % 360f - 180f
                    angle = (angle + delta).coerceIn(150f, 360f)
                    previousAngle = nextAngle
                    onSpeed(speedAtAngle(angle))
                }
                change.consume()
            }
        } finally {
            onDraggingChange(false)
        }
    }
}

private fun pointerAngle(offset: Offset): Float =
    (Math.toDegrees(atan2(offset.y, offset.x).toDouble()).toFloat() + 360f) % 360f

private fun speedAtAngle(angle: Float): Float = 2f.pow((angle.coerceIn(180f, 360f) - 270f) / 45f)
