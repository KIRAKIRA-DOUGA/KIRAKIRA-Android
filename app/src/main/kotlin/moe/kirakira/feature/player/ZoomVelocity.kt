package moe.kirakira.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D

/**
 * Carries a zoom's velocity across an interruption. [Animatable] resets its velocity when the
 * coroutine running it is cancelled, and every interruption here cancels one effect before the next
 * starts, so without this the video would stop dead and set off again from rest.
 */
internal class ZoomVelocity {
    var value = 0f
}

/** Animates from the velocity [memory] carried over, and keeps it current for the next interruption. */
internal suspend fun Animatable<Float, AnimationVector1D>.animateTo(
    target: Float,
    spec: AnimationSpec<Float>,
    memory: ZoomVelocity,
) {
    animateTo(target, spec, memory.value) { memory.value = velocity }
    memory.value = 0f
}
