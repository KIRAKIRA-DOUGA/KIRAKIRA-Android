package moe.kirakira.ui.components.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal val navigationIconPulseScale = MotionTrack(0f, 1f, 0.2f, 0.88f, 0.46f, 1.11f, 0.72f, 0.98f, 1f, 1f)

@Composable
internal fun rememberTabSelectionMotion(selected: Boolean, durationMillis: Int): TabSelectionMotion {
    val motion = remember(durationMillis) { TabSelectionMotion(durationMillis) }
    var previouslySelected by remember(motion) { mutableStateOf(selected) }
    LaunchedEffect(motion, selected) {
        // The initial or restored selection starts at rest, without an entrance motion.
        if (previouslySelected == selected) return@LaunchedEffect
        previouslySelected = selected
        motion.onSelectionChanged(selected)
    }
    return motion
}

/** Each new selection adds a rotation step that continues even when the selection moves away. */
@Composable
internal fun rememberNavigationIconRotationSteps(
    selected: Boolean,
    durationMillis: Int,
    easing: Easing = FastOutSlowInEasing,
): State<Float> {
    var targetSteps by remember { mutableIntStateOf(0) }
    var previouslySelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !previouslySelected) targetSteps++
        previouslySelected = selected
    }
    return animateFloatAsState(
        targetValue = targetSteps.toFloat(),
        animationSpec = tween(durationMillis, easing = easing),
        label = "Navigation icon rotation steps",
    )
}

/**
 * Shared selection clock for navigation icons.
 *
 * [progress] runs once from 0 to 1 per new selection and rests at 1, where every track returns
 * its identity value. Deselection freezes the clock and fades [amplitude] so any pose eases home.
 */
internal class TabSelectionMotion(private val durationMillis: Int) {
    val progress = Animatable(1f)
    val amplitude = Animatable(1f)

    suspend fun onSelectionChanged(selected: Boolean) {
        if (selected) {
            // A quick return to this tab may interrupt its deselection. Ease out that pose
            // before restarting the clock at identity, so the filled contour never jumps.
            if (progress.value < 1f && amplitude.value > 0f) {
                amplitude.animateTo(0f, tween(durationMillis = 70, easing = FastOutSlowInEasing))
            }
            amplitude.snapTo(1f)
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
        } else {
            amplitude.animateTo(0f, tween(durationMillis = 180, easing = FastOutSlowInEasing))
        }
    }

    /** Blends a track value toward [identity] by the current amplitude. */
    fun sample(track: MotionTrack, identity: Float): Float =
        identity + (track.at(progress.value) - identity) * amplitude.value
}

/**
 * Keyframe track interpolated by a cubic Hermite spline with Catmull-Rom tangents, so poses flow
 * through each key instead of stopping on it. Pass alternating time/value pairs; times must
 * increase from 0 to 1.
 */
internal class MotionTrack(vararg knots: Float) {
    private val times = FloatArray(knots.size / 2) { knots[it * 2] }
    private val values = FloatArray(knots.size / 2) { knots[it * 2 + 1] }

    fun at(t: Float): Float {
        if (t <= times.first()) return values.first()
        if (t >= times.last()) return values.last()
        var i = 0
        while (t > times[i + 1]) i++
        val t0 = times[i]
        val t1 = times[i + 1]
        val span = t1 - t0
        val u = (t - t0) / span
        val m0 = tangent(i) * span
        val m1 = tangent(i + 1) * span
        val u2 = u * u
        val u3 = u2 * u
        return (2 * u3 - 3 * u2 + 1) * values[i] + (u3 - 2 * u2 + u) * m0 +
            (-2 * u3 + 3 * u2) * values[i + 1] + (u3 - u2) * m1
    }

    // Ends rest with zero slope so the motion leaves and lands softly.
    private fun tangent(i: Int): Float = if (i == 0 || i == times.lastIndex) {
        0f
    } else {
        (values[i + 1] - values[i - 1]) / (times[i + 1] - times[i - 1])
    }
}
