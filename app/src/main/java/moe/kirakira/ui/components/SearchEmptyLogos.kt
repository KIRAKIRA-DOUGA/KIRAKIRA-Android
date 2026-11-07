package moe.kirakira.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

/** The existing history clock paired with the same flat paper magnifier as video search. */
@Composable
internal fun SearchHistoryEmptyLogo(modifier: Modifier = Modifier) {
    Box(modifier.size(128.dp)) {
        HistorySearchLogo(Modifier.size(102.dp))
        SearchPaperMagnifier()
    }
}

/** A paper video frame under a magnifying glass; the open lens carries no failure mark. */
@Composable
internal fun SearchVideosEmptyLogo(modifier: Modifier = Modifier) {
    val frame = remember { videoFilmPath() }
    Box(modifier.size(128.dp)) {
        SearchPaperLayer(frame, Color(0xFFF33437), Color(0xFFB72C30)) {
            drawRect(Color(0xFFD53538), Offset(8f, 20f), Size(104f, 39f))
        }
        SearchPaperMagnifier()
    }
}

/** Opaque, uniform paper lens: depth comes only from the cut edge and cast shadow. */
@Composable
private fun SearchPaperMagnifier() {
    val resources = LocalResources.current
    val silhouette = remember(resources) {
        val data = readVectorPathData(resources, R.drawable.ic_icon_search).first()
        val search = PathParser().parsePathString(data).toPath()
        val lensPaper = Path().apply { addOval(Rect(4.5f, 4.5f, 14.5f, 14.5f)) }
        val paper = Path.combine(PathOperation.Union, search, lensPaper).apply {
            transform(Matrix().apply { scale(3.6f, 3.6f) })
        }
        Path().apply { addPath(paper, Offset(48f, 43f)) }
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
    Box(
        Modifier.size(128.dp).graphicsLayer {
            // Search once across the subject, then settle at the original resting position.
            val sweep = sin(PI * progress.value).toFloat()
            translationX = (-24).dp.toPx() * sweep
            translationY = (-8).dp.toPx() * sweep
        },
    ) {
        SearchPaperLayer(
            path = silhouette,
            face = Color(0xFF616161),
            edge = Color(0xFF424242),
            elevation = 2f,
            edgeDepth = 1f,
        ) {
            drawCircle(Color(0xFFEEEEEE), 16.2f, Offset(82.2f, 77.2f))
        }
    }
}

/** Each piece owns one continuous silhouette, a shaded cut edge, and physical elevation. */
@Composable
private fun SearchPaperLayer(
    path: Path,
    face: Color,
    edge: Color,
    modifier: Modifier = Modifier,
    elevation: Float = 4f,
    edgeDepth: Float = 2f,
    decoration: DrawScope.() -> Unit = {},
) {
    val shape = remember(path, edgeDepth) {
        GenericShape { size, _ ->
            addPath(path, Offset(0f, edgeDepth))
            addPath(path)
            transform(Matrix().apply { scale(size.width / 128f, size.height / 128f) })
        }
    }
    Canvas(modifier.size(128.dp).graphicsLayer {
        this.shape = shape
        shadowElevation = elevation.dp.toPx()
    }) {
        scale(size.width / 128f, size.height / 128f, pivot = Offset.Zero) {
            translate(top = edgeDepth) { drawPath(path, edge) }
            drawPath(path, face)
            clipPath(path) { decoration() }
        }
    }
}

/** Reference film silhouette: 4:3 red sheet with four rounded perforations on each side. */
private fun videoFilmPath(): Path {
    val sheet = Path().apply {
        addRoundRect(RoundRect(Rect(8f, 20f, 112f, 98f), CornerRadius(6f)))
    }
    val holes = Path().apply {
        repeat(4) { row ->
            val top = 29f + row * 17.3f
            addRoundRect(RoundRect(Rect(16.7f, top, 25.4f, top + 8.7f), CornerRadius(1.3f)))
            addRoundRect(RoundRect(Rect(94.6f, top, 103.3f, top + 8.7f), CornerRadius(1.3f)))
        }
    }
    return Path.combine(PathOperation.Difference, sheet, holes)
}

@Preview(name = "Search history paper", showBackground = true)
@Composable
private fun SearchHistoryEmptyLogoPreview() {
    KIRAKIRATheme { SearchHistoryEmptyLogo() }
}

@Preview(name = "Video search paper", showBackground = true)
@Composable
private fun SearchVideosEmptyLogoPreview() {
    KIRAKIRATheme { SearchVideosEmptyLogo() }
}
