package moe.kirakira.ui.components.image

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Transient touch state; never saves a partially dismissed image across recreation. */
internal class ImageDismissState(private val scope: CoroutineScope) {
    var offset by mutableStateOf(Offset.Zero)
        private set
    var active by mutableStateOf(false)
        private set
    var settling by mutableStateOf(false)
        private set
    var dismissing by mutableStateOf(false)
        private set
    private var recovery: Job? = null

    fun start() {
        active = true
    }

    fun drag(delta: Offset) {
        offset = Offset(offset.x + delta.x, (offset.y + delta.y).coerceAtLeast(0f))
    }

    fun fraction(height: Float): Float = (offset.y / (height * 0.5f).coerceAtLeast(1f)).coerceIn(0f, 1f)

    fun recover(velocity: Velocity = Velocity.Zero, onRecovered: () -> Unit) {
        if (!active || settling || dismissing) return
        settling = true
        recovery = scope.launch {
            Animatable(offset, Offset.VectorConverter).animateTo(
                targetValue = Offset.Zero,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = 380f,
                    visibilityThreshold = Offset(0.5f, 0.5f),
                ),
                initialVelocity = Offset(velocity.x, velocity.y),
            ) { offset = value }
            offset = Offset.Zero
            onRecovered()
            settling = false
            active = false
        }
    }

    fun dismiss() {
        dismissing = true
    }

    fun dispose() {
        recovery?.cancel()
    }
}

/** Claim only a downward, single-finger drag before Telephoto starts panning or zooming. */
internal fun Modifier.imageDismissGesture(
    state: ImageDismissState,
    enabled: Boolean,
    canStart: () -> Boolean,
    onStarted: () -> Boolean,
    onCancelled: () -> Unit,
    onDismiss: () -> Unit,
): Modifier = pointerInput(state, enabled) {
    if (!enabled) return@pointerInput
    val distanceThreshold = 120 * density
    val flingThreshold = 900 * density
    try {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (state.active || down.isConsumed || !canStart()) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var total = Offset.Zero
            var claimed = false
            var released = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1) break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    released = true
                    break
                }
                if (!claimed && change.isConsumed) break
                val delta = change.position - change.previousPosition
                total += delta
                if (!claimed) {
                    if (total.getDistance() <= viewConfiguration.touchSlop) continue
                    if (total.y <= abs(total.x) * 1.2f || !canStart() || !onStarted()) break
                    state.start()
                    claimed = true
                    val overSlop = total * (1f - viewConfiguration.touchSlop / total.getDistance())
                    state.drag(overSlop)
                } else {
                    state.drag(delta)
                }
                change.consume()
            }
            if (claimed) {
                val velocity = tracker.calculateVelocity()
                val farEnough = state.offset.y >= minOf(distanceThreshold, size.height * 0.2f)
                val flingDown = state.offset.y > viewConfiguration.touchSlop * 2 && velocity.y >= flingThreshold
                if (released && velocity.y > -flingThreshold && (farEnough || flingDown)) {
                    state.dismiss()
                    onDismiss()
                } else {
                    state.recover(velocity, onCancelled)
                }
            }
        }
    } finally {
        // Pointer cancellation, navigation or a changed viewport must release the local preview.
        state.recover(onRecovered = onCancelled)
    }
}
