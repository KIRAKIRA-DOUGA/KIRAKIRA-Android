package moe.kirakira.ui.components

import android.content.res.Configuration
import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

/**
 * A Material Design 1 product icon: a themed paper circle on the 176 / 192 circle keyline, carrying
 * the bottom bar's signal and its raised center in the same light grey paper. Every sheet has a 1dp
 * white top tint and Blue Grey 900 bottom shade, casts a 45° long shadow that fades toward the bottom
 * right, and the whole icon receives the top-left radial finish.
 */
@Composable
internal fun FollowingLogo(modifier: Modifier = Modifier) {
    val scales = remember { followingPaperLayers.map { Animatable(1f) } }
    val inspecting = LocalInspectionMode.current
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    LaunchedEffect(inspecting) {
        if (inspecting || durationScale?.scaleFactor == 0f) return@LaunchedEffect
        coroutineScope {
            followingPaperLayers.forEachIndexed { index, layer ->
                launch { scales[index].animateTo(1f, followingPulseSpec(layer)) }
            }
        }
    }
    FollowingLogoArtwork(layerScale = { scales[it].value }, modifier = modifier)
}

@Composable
private fun FollowingLogoArtwork(layerScale: (Int) -> Float, modifier: Modifier = Modifier) {
    val paperColor = MaterialTheme.colorScheme.primary
    val resources = LocalContext.current.resources
    val signals = remember(resources) {
        readVectorPathData(resources, R.drawable.ic_symbol_wifi_tethering)
            .flatMap(::vectorPathContours)
            .map { data ->
                // The signal's original center (480, 520) maps to the keyline center (480, 480).
                val source = PathParser().parsePathString(data).toPath().apply {
                    transform(Matrix().apply { scale(FOLLOWING_SIGNAL_SCALE, FOLLOWING_SIGNAL_SCALE) })
                }
                FollowingMaterial(
                    Path().apply {
                        addPath(
                            source,
                            Offset(
                                followingCenter.x - 480f * FOLLOWING_SIGNAL_SCALE,
                                followingCenter.y - 520f * FOLLOWING_SIGNAL_SCALE,
                            ),
                        )
                    },
                )
            }
    }
    val shadows = remember(signals) { signals.map { followingLongShadow(it.path) } }
    val paper = remember { FollowingMaterial(Path().apply { addOval(followingKeyline) }) }
    val shadowLayerPaint = remember { Paint() }
    Box(modifier.size(128.dp)) {
        Canvas(Modifier.size(128.dp).followingElevation(paper.shape, 2f)) {
            followingArtworkScale {
                drawPath(paper.path, paperColor)
                clipPath(paper.path) {
                    // Composite opaque projections first so overlaps stay flat, then fade them along 45°.
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(followingKeyline, shadowLayerPaint)
                        shadows.forEachIndexed { index, shadow ->
                            scale(layerScale(index), pivot = followingCenter) {
                                drawPath(shadow, followingShadeColor)
                            }
                        }
                        drawRect(
                            followingLongShadowFade,
                            topLeft = followingKeyline.topLeft,
                            size = followingKeyline.size,
                            blendMode = BlendMode.DstIn,
                        )
                        canvas.restore()
                    }
                }
                drawMaterialEdges(paper)
            }
        }
        signals.forEachIndexed { index, signal ->
            val layer = followingPaperLayers[index]
            Canvas(
                Modifier.size(128.dp).graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    scaleX = layerScale(index)
                    scaleY = scaleX
                }.followingElevation(signal.shape, layer.elevation),
            ) {
                followingArtworkScale {
                    drawPath(signal.path, followingSignalColor)
                    drawMaterialEdges(signal)
                }
            }
        }
        // The finish is a single overlay above every sheet, lit from the top-left corner.
        Canvas(Modifier.size(128.dp)) {
            followingArtworkScale { drawPath(paper.path, followingFinish) }
        }
    }
}

/** One sheet of paper with its 1dp top tint and bottom shade, all in the 960-unit artwork space. */
private class FollowingMaterial(val path: Path) {
    val tint: Path = Path.combine(PathOperation.Difference, path, path.shifted(FOLLOWING_EDGE))
    val shade: Path = Path.combine(PathOperation.Difference, path, path.shifted(-FOLLOWING_EDGE))
    val shape: Shape = GenericShape { size, _ ->
        addPath(path)
        transform(Matrix().apply { scale(size.width / FOLLOWING_VIEWPORT, size.height / FOLLOWING_VIEWPORT) })
    }
}

private fun Path.shifted(y: Float) = Path().also { it.addPath(this, Offset(0f, y)) }

private fun DrawScope.drawMaterialEdges(material: FollowingMaterial) {
    drawPath(material.tint, followingTintColor)
    drawPath(material.shade, followingShadeColor.copy(alpha = 0.2f))
}

private inline fun DrawScope.followingArtworkScale(block: DrawScope.() -> Unit) =
    scale(size.width / FOLLOWING_VIEWPORT, size.height / FOLLOWING_VIEWPORT, pivot = Offset.Zero, block)

private fun Modifier.followingElevation(shape: Shape, elevation: Float) = graphicsLayer {
    this.shape = shape
    shadowElevation = elevation.dp.toPx()
    ambientShadowColor = followingShadeColor
    spotShadowColor = followingShadeColor
}

/** Sweep every contour along the same light direction, including the concave signal arcs. */
private fun followingLongShadow(source: Path): Path {
    val measure = PathMeasure(source.asAndroidPath(), true)
    val position = FloatArray(2)
    val projection = Offset(FOLLOWING_VIEWPORT, FOLLOWING_VIEWPORT)
    return Path().apply {
        addPath(source)
        do {
            val length = measure.length
            val steps = ceil(length / 4f).toInt().coerceAtLeast(1)
            measure.getPosTan(0f, position, null)
            var previous = Offset(position[0], position[1])
            for (step in 1..steps) {
                measure.getPosTan(length * step / steps, position, null)
                val next = Offset(position[0], position[1])
                val delta = next - previous
                moveTo(previous.x, previous.y)
                // Equal winding makes every swept quadrilateral contribute to one solid silhouette.
                if (delta.x >= delta.y) {
                    lineTo(next.x, next.y)
                    lineTo(next.x + projection.x, next.y + projection.y)
                    lineTo(previous.x + projection.x, previous.y + projection.y)
                } else {
                    lineTo(previous.x + projection.x, previous.y + projection.y)
                    lineTo(next.x + projection.x, next.y + projection.y)
                    lineTo(next.x, next.y)
                }
                close()
                previous = next
            }
        } while (measure.nextContour())
    }
}

private class FollowingPaperLayer(
    val elevation: Float,
    val delayMillis: Int,
    val peakScale: Float,
    val reboundScale: Float,
)

private fun followingPulseSpec(layer: FollowingPaperLayer) = keyframes {
    durationMillis = layer.delayMillis + 300
    1f at 0 using FastOutSlowInEasing
    1f at layer.delayMillis using FastOutSlowInEasing
    layer.peakScale at layer.delayMillis + 90 using FastOutSlowInEasing
    layer.reboundScale at layer.delayMillis + 190 using FastOutSlowInEasing
    1f at durationMillis
}

private const val FOLLOWING_VIEWPORT = 960f

// 1dp of the 128dp artwork.
private const val FOLLOWING_EDGE = FOLLOWING_VIEWPORT / 128f
private const val FOLLOWING_SIGNAL_SCALE = 0.86f

// Material product icon circle keyline: 176 of 192 units, centered.
private val followingKeyline = Rect(40f, 40f, 920f, 920f)
private val followingCenter = followingKeyline.center

private val followingSignalColor = Color(0xFFF5F5F5)
private val followingTintColor = Color.White.copy(alpha = 0.2f)
private val followingShadeColor = Color(0xFF263238)
private val followingLongShadowFade = Brush.linearGradient(
    colors = listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent),
    start = Offset(169f, 169f),
    end = Offset(791f, 791f),
)
private val followingFinish = Brush.radialGradient(
    colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent),
    center = followingKeyline.topLeft,
    radius = followingKeyline.width * 1.4142f,
)

// Resource contour order is outer / inner / center; the pulse travels in the opposite direction.
private val followingPaperLayers = listOf(
    FollowingPaperLayer(1f, 140, 1.1f, 0.98f),
    FollowingPaperLayer(1f, 70, 1.14f, 0.97f),
    FollowingPaperLayer(2f, 0, 1.22f, 0.95f),
)

@Preview(name = "Following paper signal", widthDp = 208, heightDp = 208)
@Composable
private fun FollowingLogoPreview() {
    FollowingPreviewFrame { FollowingLogoArtwork(layerScale = { 1f }) }
}

@Preview(name = "Following paper signal dark", widthDp = 208, heightDp = 208, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FollowingLogoDarkPreview() {
    FollowingLogoPreview()
}

internal class FollowingMotionPreviewProvider : PreviewParameterProvider<Int> {
    override val values = sequenceOf(0, 90, 160, 230, 440)
}

@Preview(name = "Following pulse phases", widthDp = 208, heightDp = 208)
@Composable
private fun FollowingLogoMotionPreview(@PreviewParameter(FollowingMotionPreviewProvider::class) elapsedMillis: Int) {
    val animations = remember {
        followingPaperLayers.map { layer ->
            TargetBasedAnimation(followingPulseSpec(layer), Float.VectorConverter, 1f, 1f)
        }
    }
    FollowingPreviewFrame {
        FollowingLogoArtwork(layerScale = { animations[it].getValueFromNanos(elapsedMillis * 1_000_000L) })
    }
}

@Composable
private fun FollowingPreviewFrame(content: @Composable () -> Unit) {
    KIRAKIRATheme {
        Surface {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
        }
    }
}
