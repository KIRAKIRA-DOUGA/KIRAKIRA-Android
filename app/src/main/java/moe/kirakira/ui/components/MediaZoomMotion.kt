package moe.kirakira.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Shared timing for expanding media and returning it to its source. */
internal fun <T> mediaZoomSpec(
    expanding: Boolean,
    easing: Easing,
    visibilityThreshold: T,
): FiniteAnimationSpec<T> = if (expanding) {
    tween(durationMillis = 450, easing = easing)
} else {
    spring(dampingRatio = 0.85f, stiffness = 380f, visibilityThreshold = visibilityThreshold)
}
