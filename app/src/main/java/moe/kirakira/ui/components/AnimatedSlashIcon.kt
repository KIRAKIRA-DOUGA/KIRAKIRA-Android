package moe.kirakira.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import org.xmlpull.v1.XmlPullParser

internal enum class SlashIconType(
    @param:DrawableRes val visibleRes: Int,
    @param:DrawableRes val slashedRes: Int,
    val slashData: String,
    val viewport: Float,
    val revealStart: Float,
    val revealEnd: Float,
    val slashAnchor: Offset,
) {
    VISIBILITY(
        R.drawable.ic_symbol_visibility,
        R.drawable.ic_symbol_visibility_off,
        "M83,196Q72,184 72.5,167.5Q73,151 84,140Q95,129 112,129Q129,129 140,140" +
            "L820,820Q831,831 831.5,847.5Q832,864 820,876Q809,887 792,887Q775,887 764,876" +
            "L624,738L522,634L306,418L222,336Z",
        960f,
        5f,
        43f,
        Offset(2.8f, 4.2f),
    ),
    DANMAKU(
        R.drawable.ic_custom_danmaku,
        R.drawable.ic_custom_danmaku_off,
        "M6.11885 4.05461C5.72833 3.66409 5.09516 3.66409 4.70464 4.05461" +
            "C4.31411 4.44513 4.31411 5.0783 4.70463 5.46882L19.271 20.0352" +
            "C19.6615 20.4258 20.2947 20.4258 20.6852 20.0352" +
            "C21.0757 19.6447 21.0757 19.0115 20.6852 18.621L6.11885 4.05461Z",
        24f,
        8f,
        41f,
        Offset(5.41174f, 4.76171f),
    ),
}

/** Hoist above conditional controls to keep their reappearance from restarting the animation. */
@Composable
internal fun rememberSlashIconProgress(
    slashed: Boolean,
    ready: Boolean = true,
): Animatable<Float, AnimationVector1D> {
    // Resolving asynchronous settings establishes the initial state without a startup transition.
    val progress = remember(ready) { Animatable(if (slashed) 1f else 0f) }
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(slashed, ready, spatialSpec) {
        if (ready) {
            progress.animateTo(if (slashed) 1f else 0f, spatialSpec)
        }
    }
    return progress
}

@Composable
internal fun AnimatedSlashIcon(
    type: SlashIconType,
    slashed: Boolean,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val progress = rememberSlashIconProgress(slashed)
    AnimatedSlashIcon(type, { progress.value }, description, modifier)
}

@Composable
internal fun AnimatedSlashIcon(
    type: SlashIconType,
    progress: () -> Float,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val geometry = remember(resources, type) {
        fun readPath(@DrawableRes resource: Int): Path {
            val result = Path()
            resources.getXml(resource).use { parser ->
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {
                        val data = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData")
                        result.addPath(PathParser().parsePathString(data).toPath())
                    }
                    parser.next()
                }
            }
            result.transform(Matrix().apply { scale(24f / type.viewport, 24f / type.viewport) })
            return result
        }
        val visible = readPath(type.visibleRes)
        val slashed = readPath(type.slashedRes)
        val slash = PathParser().parsePathString(type.slashData).toPath().apply {
            transform(Matrix().apply { scale(24f / type.viewport, 24f / type.viewport) })
        }
        val body = Path.combine(PathOperation.Difference, slashed, slash)
        SlashGeometry(
            visible,
            slashed,
            slash,
            Path.combine(PathOperation.Intersect, visible, body),
            Path.combine(PathOperation.Difference, visible, body),
            Path.combine(PathOperation.Difference, body, visible),
        )
    }
    val revealClip = remember { Path() }
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp).semantics {
        if (description != null) contentDescription = description
    }) {
        val spatialProgress = progress().coerceAtLeast(0f)
        val fraction = spatialProgress.coerceAtMost(1f)
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            when (spatialProgress) {
                0f -> drawPath(geometry.visible, color)
                1f -> drawPath(geometry.slashed, color)
                else -> {
                    drawPath(geometry.commonBody, color)
                    drawPath(geometry.visibleOnly, color, alpha = 1f - fraction)
                    drawPath(geometry.slashedOnly, color, alpha = fraction)
                    // x + y advances along the slash, revealing its original rounded contour.
                    val boundary = type.revealStart + (type.revealEnd - type.revealStart) * fraction
                    revealClip.rewind()
                    revealClip.moveTo(-48f, -48f)
                    revealClip.lineTo(boundary + 48f, -48f)
                    revealClip.lineTo(-48f, boundary + 48f)
                    revealClip.close()
                    if (spatialProgress > 1f) {
                        // Stretch only along the slash, keeping its start and visual weight fixed.
                        withTransform({
                            rotate(45f, type.slashAnchor)
                            scale(spatialProgress, 1f, type.slashAnchor)
                            rotate(-45f, type.slashAnchor)
                        }) {
                            drawPath(geometry.slash, color)
                        }
                    } else {
                        clipPath(revealClip) { drawPath(geometry.slash, color) }
                    }
                }
            }
        }
    }
}

private class SlashGeometry(
    val visible: Path,
    val slashed: Path,
    val slash: Path,
    val commonBody: Path,
    val visibleOnly: Path,
    val slashedOnly: Path,
)
