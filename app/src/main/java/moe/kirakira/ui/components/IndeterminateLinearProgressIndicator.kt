package moe.kirakira.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun IndeterminateLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    drawSegments: DrawScope.((Color) -> Unit) -> Unit = { draw -> draw(color) },
) {
    val transition = rememberInfiniteTransition(label = "Linear progress")
    val firstTranslation by transition.animateFloat(0f, 2.006111f, FirstLineTranslation, label = "First translation")
    val secondTranslation by transition.animateFloat(0f, 1.602778f, SecondLineTranslation, label = "Second translation")
    val firstLength by transition.animateFloat(0.08f, 0.08f, FirstLineLength, label = "First length")
    val secondLength by transition.animateFloat(0.08f, 0.08f, SecondLineLength, label = "Second length")
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]

    Canvas(
        modifier
            .size(width = 240.dp, height = 4.dp)
            .progressSemantics()
            .clip(CircleShape),
    ) {
        if (trackColor.alpha > 0f) {
            drawRoundRect(trackColor, cornerRadius = CornerRadius(size.minDimension / 2f))
        }
        drawSegments { segmentColor ->
            if (durationScale?.scaleFactor == 0f) {
                drawProgressSegment(centerFraction = 0.5f, lengthFraction = 0.4f, color = segmentColor)
            } else {
                // Cerasus offsets each wrapper's left edge and scales its line around the center.
                drawProgressSegment(-1.451666f + 0.5f + firstTranslation, firstLength, segmentColor)
                drawProgressSegment(-0.548889f + 0.5f + secondTranslation, secondLength, segmentColor)
            }
        }
    }
}

private fun DrawScope.drawProgressSegment(centerFraction: Float, lengthFraction: Float, color: Color) {
    val center = if (layoutDirection == LayoutDirection.Ltr) centerFraction else 1f - centerFraction
    val width = size.width * lengthFraction
    val left = size.width * center - width / 2f
    if (width <= 0f || size.height <= 0f || left >= size.width || left + width <= 0f) return

    drawRoundRect(
        color = color,
        topLeft = Offset(left, 0f),
        size = Size(width, size.height),
        cornerRadius = CornerRadius(minOf(width, size.height) / 2f),
    )
}

private const val CycleDurationMillis = 2_000

private val FirstLineTranslation = infiniteRepeatable(keyframes {
    durationMillis = CycleDurationMillis
    0f at 400 using CubicBezierEasing(0.5f, 0f, 0.7017f, 0.4958f)
    0.836714f at 1_183 using CubicBezierEasing(0.3024f, 0.3814f, 0.55f, 0.9563f)
})

private val SecondLineTranslation = infiniteRepeatable(keyframes {
    durationMillis = CycleDurationMillis
    0f at 0 using CubicBezierEasing(0.15f, 0f, 0.5151f, 0.4097f)
    0.376519f at 500 using CubicBezierEasing(0.3103f, 0.2841f, 0.8f, 0.7337f)
    0.843862f at 967 using CubicBezierEasing(0.4f, 0.627f, 0.6f, 0.902f)
})

private val FirstLineLength = infiniteRepeatable(keyframes {
    durationMillis = CycleDurationMillis
    0.08f at 733 using CubicBezierEasing(0.3347f, 0.1248f, 0.7858f, 1f)
    0.6615f at 1_383 using CubicBezierEasing(0.06f, 0.11f, 0.6f, 1f)
})

private val SecondLineLength = infiniteRepeatable(keyframes {
    durationMillis = CycleDurationMillis
    0.08f at 0 using CubicBezierEasing(0.205f, 0.0571f, 0.5766f, 0.454f)
    0.4571f at 383 using CubicBezierEasing(0.1523f, 0.1964f, 0.6484f, 1.0043f)
    0.728f at 883 using CubicBezierEasing(0.2578f, -0.0032f, 0.2118f, 1.3818f)
})
