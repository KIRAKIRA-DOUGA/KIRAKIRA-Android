package moe.kirakira.ui.components.illustration

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.shadow.ShadowContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import moe.kirakira.ui.theme.KIRAKIRATheme

internal data class HistorySearchLogoColors(
    val face: Brush,
    val frame: Color,
    val hands: Color,
    val secondHand: Color,
)

/** A classic paper clock with a counterclockwise history arrow and one rewind on entry. */
@Composable
internal fun HistorySearchLogo(
    modifier: Modifier = Modifier,
    colors: HistorySearchLogoColors = historySearchLogoColors(),
    isActive: Boolean = true,
) {
    val progress = rememberIllustrationEntranceProgress(isActive)
    HistorySearchLogoArtwork(progress = { progress.value }, modifier = modifier, colors = colors)
}

@Composable
internal fun historySearchLogoColors(): HistorySearchLogoColors {
    val frameColor = MaterialTheme.colorScheme.primary
    val faceBrush = remember(frameColor) {
        Brush.linearGradient(
            colors = listOf(
                lerp(frameColor, Color.Black, 0.22f),
                lerp(frameColor, Color.Black, 0.26f),
            ),
            start = Offset(112f, 112f),
            end = Offset(400f, 400f),
        )
    }
    return HistorySearchLogoColors(
        face = faceBrush,
        frame = frameColor,
        hands = Color.White,
        secondHand = lerp(frameColor, Color.White, 0.5f),
    )
}

@Composable
private fun HistorySearchLogoArtwork(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    colors: HistorySearchLogoColors = historySearchLogoColors(),
) {
    val dialClip = remember { Path().apply { addOval(Rect(64f, 64f, 448f, 448f)) } }
    val faceShape = remember {
        GenericShape { size, _ ->
            addOval(Rect(54f, 54f, 458f, 458f))
            transform(Matrix().apply { scale(size.width / 512f, size.height / 512f) })
        }
    }
    val shadow = remember { Path() }
    Box(modifier.size(128.dp), contentAlignment = Alignment.Center) {
        // Every plane uses the same viewport and center; elevation never shifts its geometry.
        Canvas(
            Modifier.size(128.dp).graphicsLayer {
                shape = faceShape
                shadowElevation = 2.dp.toPx()
            },
        ) {
            scale(size.width / 512f, size.height / 512f, pivot = Offset.Zero) {
                drawCircle(colors.face, radius = 202f, center = clockCenter)
            }
        }
        Canvas(Modifier.size(128.dp)) {
            scale(size.width / 512f, size.height / 512f, pivot = Offset.Zero) {
                val remainingTurn = 1f - progress()
                val hour = rotateClockHand(Offset(160f, 200f), 7.5f * remainingTurn)
                val minute = rotateClockHand(Offset(396f, 176f), 90f * remainingTurn)
                val second = rotateClockHand(Offset(256f, 428f), 360f * remainingTurn)
                shadow.reset()
                shadow.addHandShadow(hour)
                shadow.addHandShadow(minute)
                shadow.addHandShadow(second)
                clipPath(dialClip) {
                    // Hand positions change, but the light always projects toward the lower right.
                    drawPath(shadow, Color.Black.copy(alpha = 0.2f))
                    drawLine(colors.hands, clockCenter, hour, 14f, StrokeCap.Butt)
                    drawLine(colors.hands, clockCenter, minute, 14f, StrokeCap.Butt)
                    drawLine(colors.secondHand, clockCenter, second, 8f, StrokeCap.Butt)
                    drawCircle(colors.secondHand, radius = 16f, center = clockCenter)
                }
            }
        }
        Spacer(
            Modifier
                .size(128.dp)
                .drawWithCache {
                    val arrow = Path().apply {
                        setHistoryArrow(progress())
                        transform(Matrix().apply { scale(size.width / 512f, size.height / 512f) })
                    }
                    // Use the same path for the face and its shadow, including outside the viewport.
                    val bounds = arrow.getBounds()
                    val shape = GenericShape { _, _ -> addPath(arrow, -bounds.topLeft) }
                    val painter = ShadowContext().createDropShadowPainter(shape, historyArrowShadow)
                    onDrawBehind {
                        translate(bounds.left, bounds.top) {
                            with(painter) { draw(bounds.size) }
                        }
                        drawPath(arrow, colors.frame)
                    }
                },
        )
    }
}

private val clockCenter = Offset(256f, 256f)
private val historyArrowShadow = Shadow(
    radius = 4.dp,
    color = Color.Black.copy(alpha = 0.2f),
    offset = DpOffset(0.dp, 2.dp),
)

/** The fixed-size head turns with the leading edge of the 13dp curved band. */
private fun Path.setHistoryArrow(fraction: Float) {
    reset()
    val bodySweep = -312f * fraction
    val baseDegrees = 180f + bodySweep
    val endAngle = baseDegrees - 3f
    val angle = endAngle * PI.toFloat() / 180f
    val radial = Offset(cos(angle), sin(angle))
    val tangent = Offset(radial.y, -radial.x)
    val baseAngle = baseDegrees * PI.toFloat() / 180f
    val baseRadial = Offset(cos(baseAngle), sin(baseAngle))
    val tip = clockCenter + radial * 216f + tangent * 112f
    val outerHead = clockCenter + baseRadial * 292f
    val innerHead = clockCenter + baseRadial * 144f
    val innerEnd = clockCenter + baseRadial * 192f
    moveTo(12f, 256f)
    arcTo(Rect(12f, 12f, 500f, 500f), 180f, bodySweep, false)
    lineTo(outerHead.x, outerHead.y)
    lineTo(tip.x, tip.y)
    lineTo(innerHead.x, innerHead.y)
    lineTo(innerEnd.x, innerEnd.y)
    arcTo(Rect(64f, 64f, 448f, 448f), baseDegrees, -bodySweep, false)
    close()
}

private fun rotateClockHand(end: Offset, degrees: Float): Offset {
    val angle = degrees * PI.toFloat() / 180f
    val delta = end - clockCenter
    val cosine = cos(angle)
    val sine = sin(angle)
    return clockCenter + Offset(
        delta.x * cosine - delta.y * sine,
        delta.x * sine + delta.y * cosine,
    )
}

/** Keep all projected quadrilaterals wound alike so their overlap is one flat shadow. */
private fun Path.addHandShadow(end: Offset) {
    val projection = Offset(512f, 512f)
    val delta = end - clockCenter
    val projectedCenter = clockCenter + projection
    val projectedEnd = end + projection
    moveTo(clockCenter.x, clockCenter.y)
    if (delta.x >= delta.y) {
        lineTo(end.x, end.y)
        lineTo(projectedEnd.x, projectedEnd.y)
        lineTo(projectedCenter.x, projectedCenter.y)
    } else {
        lineTo(projectedCenter.x, projectedCenter.y)
        lineTo(projectedEnd.x, projectedEnd.y)
        lineTo(end.x, end.y)
    }
    close()
}

@Preview(name = "History paper clock", widthDp = 240, heightDp = 240)
@Composable
private fun HistorySearchLogoPreview() {
    KIRAKIRATheme {
        Surface {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                HistorySearchLogoArtwork(progress = { 1f })
            }
        }
    }
}

@Preview(name = "History paper clock dark", widthDp = 240, heightDp = 240, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistorySearchLogoDarkPreview() {
    HistorySearchLogoPreview()
}

internal class HistorySearchMotionPreviewProvider : PreviewParameterProvider<Float> {
    override val values = sequenceOf(0f, 0.05f, 0.1f, 0.2f, 0.4f, 0.7f, 1f)
}

@Preview(name = "History arrow entry phases", widthDp = 176, heightDp = 176)
@Composable
private fun HistorySearchMotionPreview(
    @PreviewParameter(HistorySearchMotionPreviewProvider::class) elapsed: Float,
) {
    KIRAKIRATheme {
        Surface {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                HistorySearchLogoArtwork(progress = { illustrationEntranceSpec.easing.transform(elapsed) })
            }
        }
    }
}
