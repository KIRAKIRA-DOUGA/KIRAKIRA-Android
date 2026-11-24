package moe.kirakira.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * How far one video pushes the page behind it, as one continuous quantity. While the video expands
 * the push follows the zoom's progress; while it returns the push runs its own time line. Every
 * hand-over starts from the value and velocity on screen, so an interruption in either direction
 * neither jumps nor drops the push for a frame.
 *
 * The motions run in [scope] rather than in the effect that starts them: a motion ends only by
 * settling or by being replaced, never by being cancelled half way.
 */
internal class BackgroundPushMotion(private val scope: CoroutineScope) {
    /** The push that matches the zoom's progress one to one; supplied by the host. */
    var follow: () -> Float = { 0f }

    // While returning the motions hold the push itself; otherwise the remainder of an interrupted
    // return, added to the progress and decaying to zero. Each hand-over installs new instances
    // together with the mode, so no frame observes one without the other.
    private var returning by mutableStateOf(false)
    private var strengthMotion by mutableStateOf(Animatable(0f))
    private var depthMotion by mutableStateOf(Animatable(0f))
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    private val base: Float get() = if (returning) 0f else follow()

    /** Blur and dim, capped at the entry's full push. */
    val strength: Float get() = (base + strengthMotion.value).coerceIn(0f, 1f)

    /** The shrink, in units of the entry's full push; never shallower than the blur it accompanies. */
    val depth: Float get() = maxOf(strength, base + depthMotion.value)

    /**
     * Brings the page forward within [durationMillis], the time the video takes to land.
     *
     * A [hidden] page sits behind the full-size video, so it starts deeper than the entry left it and
     * comes forward at once: the exit travels further than the entry without a visible step. A visible
     * page continues from where it is, at the speed it has: one still being pushed back coasts on,
     * turns and then comes forward, as the video itself does.
     */
    fun startReturn(durationMillis: Int, hidden: Boolean, followVelocity: Float) {
        val carried = if (returning) 0f else followVelocity
        val strengthVelocity = strengthMotion.velocity + carried
        val depthVelocity = depthMotion.velocity + carried
        val strengthStart = Animatable(if (hidden) 1f else strength)
        val depthStart = Animatable(if (hidden) maxOf(depth, RETURN_BACKGROUND_DEPTH) else depth)
        val millis = durationMillis.coerceAtLeast(1)
        // Hold the settled background at zero until expansion takes over. The foreground can
        // still be bouncing when these animations finish.
        run(returning = true, strengthStart, depthStart) {
            if (hidden) {
                val spec = tween<Float>(millis, easing = BackgroundRestoreEasing)
                launch { strengthStart.animateTo(0f, spec) }
                depthStart.animateTo(0f, spec)
            } else {
                // Critically damped, so it keeps its velocity, never crosses zero into a bounce and
                // settles as the video lands.
                val frequency = RETURN_SETTLE_PERIODS * 1_000f / millis
                val spec = spring<Float>(dampingRatio = 1f, stiffness = frequency * frequency, visibilityThreshold = 0.001f)
                launch { strengthStart.animateTo(0f, spec, strengthVelocity) }
                depthStart.animateTo(0f, spec, depthVelocity)
            }
        }
    }

    /** Follows the progress again; what an interrupted return left over decays along [spec]. */
    fun startFollow(spec: AnimationSpec<Float>, followVelocity: Float) {
        val target = follow()
        val strengthOffset = base + strengthMotion.value - target
        val depthOffset = depth - target
        if (!returning && strengthOffset == 0f && depthOffset == 0f) return
        val carried = if (returning) -followVelocity else 0f
        val strengthVelocity = strengthMotion.velocity + carried
        val depthVelocity = depthMotion.velocity + carried
        val strengthRest = Animatable(strengthOffset)
        val depthRest = Animatable(depthOffset)
        run(returning = false, strengthRest, depthRest) {
            launch { strengthRest.animateTo(0f, spec, strengthVelocity) }
            depthRest.animateTo(0f, spec, depthVelocity)
        }
    }

    private fun run(
        returning: Boolean,
        strength: Animatable<Float, AnimationVector1D>,
        depth: Animatable<Float, AnimationVector1D>,
        motion: suspend CoroutineScope.() -> Unit,
    ) {
        strengthMotion = strength
        depthMotion = depth
        this.returning = returning
        job?.cancel()
        job = scope.launch { coroutineScope(motion) }
    }
}

/**
 * Material 3 emphasized decelerate: the page starts coming forward in the same frame the video leaps
 * away, then eases into the landing over the spring's settling tail.
 */
private val BackgroundRestoreEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * How much deeper than the entry's push a return from the full page starts the background's shrink.
 * Blur and dim keep the entry's strength; only the scale travels further.
 */
private const val RETURN_BACKGROUND_DEPTH = 1.8f

/** `ωt` at which a critically damped spring is within a thousandth of its target. */
private const val RETURN_SETTLE_PERIODS = 10f
