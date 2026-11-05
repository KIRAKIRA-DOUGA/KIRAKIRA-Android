package moe.kirakira.feature.player

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round

internal object PlaybackSpeedRules {
    val range: ClosedFloatingPointRange<Float> = 0.25f..4f
    val progressRange: ClosedFloatingPointRange<Float> = -2f..2f
    const val ZERO_ANGLE = 150f
    const val MIN_ANGLE = 180f
    const val MAX_ANGLE = 360f
    val speeds: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)

    fun normalize(value: Float, continuous: Boolean): Float {
        val bounded = value.coerceIn(range)
        return if (continuous) round(bounded * 100) / 100 else speeds.minBy { abs(it - bounded) }
    }

    fun progress(speed: Float): Float = log2(speed.coerceIn(range))

    fun speedAtProgress(progress: Float): Float = 2f.pow(progress.coerceIn(progressRange))

    fun adjustedSpeed(current: Float, progress: Float, continuous: Boolean): Float {
        if (!progress.isFinite()) return current
        val requested = speedAtProgress(progress)
        return if (continuous) normalize(requested, true) else when {
            requested > current -> speeds.firstOrNull { it > current } ?: current
            requested < current -> speeds.lastOrNull { it < current } ?: current
            else -> current
        }
    }

    fun angle(speed: Float): Float =
        if (speed <= 0f) ZERO_ANGLE else MIN_ANGLE + (progress(speed) + 2f) * 45f

    fun speedAtAngle(angle: Float): Float = speedAtProgress((angle.coerceIn(MIN_ANGLE, MAX_ANGLE) - 270f) / 45f)
}
