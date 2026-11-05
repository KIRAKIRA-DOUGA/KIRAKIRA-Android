package moe.kirakira.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

/** Classic Material star cut from yellow paper, with a 2dp edge and raised silhouette. */
@Composable
internal fun FavoritesLogo(modifier: Modifier = Modifier) {
    val inspecting = LocalInspectionMode.current
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val progress = remember(inspecting) {
        Animatable(if (inspecting || durationScale?.scaleFactor == 0f) 1f else 0f)
    }
    LaunchedEffect(inspecting) {
        if (!inspecting) {
            progress.animateTo(1f, tween(1_000, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)))
        }
    }
    val star = remember { PathParser().parsePathString(STAR_PATH).toPath() }
    val paperShape = remember(star) {
        GenericShape { size, _ ->
            addPath(star, Offset(0f, 0.375f))
            addPath(star)
            transform(Matrix().apply { scale(size.width / 24f, size.height / 24f) })
        }
    }
    val fold = remember {
        Path().apply {
            moveTo(12f, 2f)
            lineTo(24f, 2f)
            lineTo(24f, 24f)
            lineTo(12f, 24f)
            lineTo(12f, 12f)
            close()
        }
    }
    Canvas(
        modifier
            .size(128.dp)
            .graphicsLayer {
                val fraction = progress.value
                rotationZ = -72f * (1f - fraction)
                scaleX = 0.72f + 0.28f * fraction
                scaleY = scaleX
                shape = paperShape
                shadowElevation = 4.dp.toPx()
            },
    ) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            translate(top = 0.375f) { drawPath(star, favoritesEdgeColor) }
            drawPath(star, favoritesPaperBrush)
            clipPath(star) { drawPath(fold, Color(0xFFF9A825).copy(alpha = 0.22f)) }
        }
    }
}

private val favoritesEdgeColor = Color(0xFFF9A825)
private val favoritesPaperBrush = Brush.linearGradient(
    colors = listOf(Color(0xFFFFEE58), Color(0xFFFDD835)),
    start = Offset(2f, 2f),
    end = Offset(22f, 22f),
)

// Material Design Icons "star", 24 × 24 viewport.
private const val STAR_PATH = "M12 17.27L18.18 21L16.54 13.97L22 9.24L14.81 8.63L12 2L9.19 8.63L2 9.24L7.46 13.97L5.82 21Z"
