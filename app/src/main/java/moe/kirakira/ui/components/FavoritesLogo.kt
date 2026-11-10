package moe.kirakira.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import moe.kirakira.R

/** Cerasus star cut from themed paper, with a 2dp edge and raised silhouette. */
@Composable
internal fun FavoritesLogo(modifier: Modifier = Modifier) {
    val paperColor = MaterialTheme.colorScheme.primary
    val edgeColor = lerp(paperColor, Color.Black, 0.11f)
    val paperBrush = remember(paperColor) {
        Brush.linearGradient(
            colors = listOf(lerp(paperColor, Color.White, 0.2f), paperColor),
            start = Offset(2f, 2f),
            end = Offset(22f, 22f),
        )
    }
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
    val resources = LocalResources.current
    val star = remember(resources) {
        val data = readVectorPathData(resources, R.drawable.ic_icon_star).first()
        PathParser().parsePathString(data).toPath()
    }
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
            translate(top = 0.375f) { drawPath(star, edgeColor) }
            drawPath(star, paperBrush)
            clipPath(star) { drawPath(fold, edgeColor.copy(alpha = 0.22f)) }
        }
    }
}
