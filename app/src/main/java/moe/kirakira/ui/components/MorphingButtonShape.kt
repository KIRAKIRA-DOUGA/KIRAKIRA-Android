package moe.kirakira.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** One outline for the button and its shadows; its identity also keeps Material ripple alive. */
@Composable
internal fun rememberMorphingButtonShape(
    targetShape: Shape,
    animationSpec: FiniteAnimationSpec<Float>,
): Shape {
    val shape = remember { MorphingButtonShape(targetShape) }
    LaunchedEffect(shape, targetShape, animationSpec) {
        shape.animateTo(targetShape, animationSpec)
    }
    return shape
}

private class MorphingButtonShape(initialShape: Shape) : Shape {
    private var startShape by mutableStateOf(initialShape)
    private var targetShape by mutableStateOf(initialShape)
    private val progress = Animatable(1f)
    // Animatable clears its velocity on cancellation; retain the last frame for retargeting.
    private var lastVelocity = 0f
    private var cachedProgress = Float.NaN
    private var cachedShape: Shape = initialShape

    private fun currentShape(): Shape {
        val fraction = progress.value
        // Read both snapshot values even on cache hits so every draw consumer observes them.
        val start = startShape
        val target = targetShape
        if (fraction != cachedProgress) {
            cachedShape = when (fraction) {
                0f -> start
                1f -> target
                else -> Interpolatable.lerp(start, target, fraction) as? Shape ?: target
            }
            cachedProgress = fraction
        }
        return cachedShape
    }

    suspend fun animateTo(shape: Shape, animationSpec: FiniteAnimationSpec<Float>) {
        if (shape == targetShape) {
            // A motion-scheme change can cancel an animation without changing its destination.
            if (progress.value != 1f) animateProgress(animationSpec, lastVelocity)
            return
        }
        if (shape !is CornerBasedShape || targetShape !is CornerBasedShape) {
            startShape = shape
            targetShape = shape
            cachedProgress = Float.NaN
            lastVelocity = 0f
            progress.snapTo(1f)
            return
        }

        val reversing = shape == startShape
        val fraction = progress.value
        val velocity = lastVelocity
        // A changed destination starts at the visible outline, including mid-animation changes.
        val nextStart = if (reversing) targetShape else currentShape()
        startShape = nextStart
        targetShape = shape
        cachedProgress = Float.NaN
        progress.snapTo(if (reversing) 1f - fraction else 0f)
        animateProgress(animationSpec, if (reversing) -velocity else 0f)
    }

    private suspend fun animateProgress(animationSpec: FiniteAnimationSpec<Float>, initialVelocity: Float) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = animationSpec,
            initialVelocity = initialVelocity,
        ) {
            lastVelocity = velocity
        }
        lastVelocity = 0f
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        currentShape().createOutline(size, layoutDirection, density)
}
