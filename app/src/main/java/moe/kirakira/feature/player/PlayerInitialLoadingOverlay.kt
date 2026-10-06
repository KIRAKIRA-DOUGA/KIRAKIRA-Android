package moe.kirakira.feature.player

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

private const val INITIAL_LOADING_FADE_MILLIS = 500
// Common period of the upstream 6/8/12/16/32-second motions, without a discontinuity at wraparound.
private const val COVER_PERIOD_MILLIS = 96_000
private val coverEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val triangleMovementEasing = CubicBezierEasing(0f, 0.5f, 1f, 0.5f)
private val coverScalingEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
private val coverFadeEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

@Composable
internal fun rememberPlayerInitialLoadingAlpha(
    visible: Boolean,
    immediatelyHidden: Boolean,
): State<Float> = animateFloatAsState(
    targetValue = if (visible && !immediatelyHidden) 1f else 0f,
    animationSpec = if (visible || immediatelyHidden) snap() else tween(
        durationMillis = INITIAL_LOADING_FADE_MILLIS,
        easing = coverFadeEasing,
    ),
    label = "PlayerInitialLoadingAlpha",
)

/** Cerasus LogoCover geometry and motion; see third_party/cerasus-icons/README.md. */
@Composable
internal fun PlayerInitialLoadingOverlay(
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    showBranding: Boolean = false,
    fullscreen: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surface)
            .clearAndSetSemantics { },
    ) {
        PlayerLoadingGeometry(accent, Modifier.fillMaxSize())
        if (showBranding && maxWidth >= 640.dp) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                    .padding(top = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.logo_kirakira_wordmark),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(width = 108.dp, height = 18.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.logo_kirakira),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(33.dp),
                )
            }
        }
    }
}

@Composable
private fun PlayerLoadingGeometry(accent: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "PlayerLoadingGeometry")
    val elapsed by transition.animateFloat(
        initialValue = 0f,
        targetValue = COVER_PERIOD_MILLIS.toFloat(),
        animationSpec = infiniteRepeatable(tween(COVER_PERIOD_MILLIS, easing = LinearEasing)),
        label = "PlayerLoadingGeometryClock",
    )
    val outlinedTriangle = remember {
        Path().apply {
            moveTo(4f, 4f)
            lineTo(4f, 66f)
            lineTo(56f, 35f)
            close()
        }
    }
    val filledTriangle = remember {
        Path().apply {
            moveTo(0f, 0f)
            lineTo(27.712f, 16f)
            lineTo(0f, 32f)
            close()
        }
    }
    val stripeClip = remember { Path().apply { addOval(Rect(0f, 0f, 128f, 128f)) } }
    Canvas(modifier) {
        val unit = minOf(1f, size.width / 400.dp.toPx(), size.height / 225.dp.toPx()) * 1.dp.toPx()
        if (unit <= 0f) return@Canvas
        val width = size.width / unit
        val height = size.height / unit
        val time = if (ValueAnimator.areAnimatorsEnabled()) elapsed else 1_500f
        scale(unit, unit, pivot = Offset.Zero) {
            drawCoverLines(width, height, time, accent)
            drawCoverPluses(width, height, time, accent)

            val outlinedMovement = triangleMovementEasing.transform(coverCycle(time + 1_000f, 4_000))
            val outlinedAlpha = coverAlternate(time + 1_000f, 2_000, coverScalingEasing)
            translate(width * 0.4f - 69.28f - 80f + 400f * outlinedMovement, height - 112f) {
                drawPath(outlinedTriangle, accent.copy(alpha = outlinedAlpha), style = Stroke(2f))
            }
            val filledMovement = triangleMovementEasing.transform(coverCycle(time, 4_000))
            val filledAlpha = coverAlternate(time, 2_000, coverScalingEasing)
            translate(width - 64f - 27.712f - 80f + 160f * filledMovement, height * 0.64f - 32f) {
                drawPath(filledTriangle, accent.copy(alpha = filledAlpha))
            }

            val circleScale = coverAlternate(time, 4_000, coverScalingEasing)
            val circleCenter = Offset(width - 16f, height * 0.15f + 64f)
            scale(circleScale, circleScale, pivot = circleCenter) {
                drawCircle(accent, radius = 64f, center = circleCenter, style = Stroke(16f - 14f * circleScale))
            }

            translate(width - 400f, height - 80f) {
                rotate(coverCycle(time, 16_000) * 360f, pivot = Offset(64f, 64f)) {
                    clipPath(stripeClip) {
                        val stripeScale = 0.7f - 0.4f * coverAlternate(time, 2_000, coverEaseInOut)
                        repeat(13) { index ->
                            drawRect(
                                color = accent,
                                topLeft = Offset(0f, index * 16f + (1f - stripeScale) * 8f),
                                size = Size(128f, 16f * stripeScale),
                            )
                        }
                    }
                }
            }
        }
    }
}

private class CoverLine(val height: Float, val durationMillis: Int, val from: Float, val to: Float)

private val coverLines = listOf(
    CoverLine(32f, 8_000, 1f, 1f),
    CoverLine(16f, 16_000, 3f, 1f),
    CoverLine(8f, 32_000, 5.5f, 2f),
    CoverLine(24f, 12_000, 1.5f, 1.5f),
    CoverLine(64f, 6_000, 1f, 1f),
)

private fun DrawScope.drawCoverLines(width: Float, height: Float, time: Float, accent: Color) {
    rotate(-32f, pivot = Offset(width / 2f, height / 2f)) {
        coverLines.forEachIndexed { index, line ->
            val length = line.height * 15f
            val from = length * line.from
            val to = -width - length * line.to
            val x = width - length + from + (to - from) * coverCycle(time, line.durationMillis)
            val y = when (index) {
                0 -> -32f
                1 -> height * 0.3f
                2 -> height * 0.55f
                3 -> height * 0.58f
                else -> height - 64f
            }
            drawRoundRect(
                color = accent.copy(alpha = 0.3f),
                topLeft = Offset(x, y),
                size = Size(length, line.height),
                cornerRadius = CornerRadius(line.height / 2f),
            )
        }
    }
}

private fun DrawScope.drawCoverPluses(width: Float, height: Float, time: Float, accent: Color) {
    repeat(4) { row ->
        repeat(4) { column ->
            val delay = (row + column) * 250f
            val phase = if (time < delay) 1f else coverCycle(time - delay, 4_000)
            val turn = coverEaseInOut.transform((phase * 2f).coerceAtMost(1f))
            val horizontalScale = cos((turn - 1f) * 2f * PI).toFloat()
            val center = Offset(width - 26f - (3 - column) * 52f, height - 20f - (3 - row) * 38f)
            scale(horizontalScale, 1f, pivot = center) {
                drawLine(accent, center - Offset(8f, 0f), center + Offset(8f, 0f), 1.5f, StrokeCap.Round)
                drawLine(accent, center - Offset(0f, 8f), center + Offset(0f, 8f), 1.5f, StrokeCap.Round)
            }
        }
    }
}

private fun coverCycle(time: Float, durationMillis: Int): Float = (time % durationMillis) / durationMillis

private fun coverAlternate(time: Float, durationMillis: Int, easing: Easing): Float {
    val phase = coverCycle(time, durationMillis * 2) * 2f
    return if (phase <= 1f) easing.transform(phase) else 1f - easing.transform(phase - 1f)
}

@Preview(widthDp = 360, heightDp = 203)
@Composable
private fun PlayerInitialLoadingPreview() {
    KIRAKIRATheme {
        PlayerInitialLoadingOverlay(alpha = { 1f }, showBranding = true)
    }
}
