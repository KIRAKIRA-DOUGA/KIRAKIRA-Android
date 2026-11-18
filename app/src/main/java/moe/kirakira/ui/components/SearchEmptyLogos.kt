package moe.kirakira.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

private val searchPaperFrameColor = Color(0xFF616161)
private val searchPaperLensColor = Color(0xFFEEEEEE)

/** A paper clock whose dial forms the lens of a magnifier. */
@Composable
internal fun SearchHistoryEmptyLogo(modifier: Modifier = Modifier, isActive: Boolean = true) {
    val colors = historySearchLogoColors()
    val handle = remember {
        Path().apply {
            addRoundRect(RoundRect(Rect(-7f, -7f, 55f, 7f), CornerRadius(7f)))
            transform(Matrix().apply { rotateZ(45f) })
            translate(Offset(77f, 77f))
        }
    }
    Box(modifier.size(128.dp)) {
        SearchPaperLayer(
            path = handle,
            face = colors.frame,
            elevation = 2f,
        )
        HistorySearchLogo(
            modifier = Modifier.graphicsLayer {
                // Leave room for the handle within the same 128-unit artwork.
                scaleX = 0.72f
                scaleY = 0.72f
                translationX = size.width * (-10f / 128f)
                translationY = size.height * (-10f / 128f)
            },
            colors = colors,
            isActive = isActive,
        )
    }
}

/** A paper video frame under a magnifying glass; the open lens carries no failure mark. */
@Composable
internal fun SearchVideosEmptyLogo(modifier: Modifier = Modifier, isActive: Boolean = true) {
    val filmColor = MaterialTheme.colorScheme.primary
    val upperColor = lerp(filmColor, Color.Black, 0.08f)
    val frame = remember { videoFilmPath() }
    Box(modifier.size(128.dp)) {
        SearchPaperLayer(frame, filmColor) {
            drawRect(upperColor, videoFilmBounds.topLeft, Size(videoFilmBounds.width, videoFilmBounds.height / 2f))
        }
        SweepingSearchPaperMagnifier(isActive)
    }
}

private val videoSearchLensCenter = Offset(82.2f, 77.2f)

@Composable
private fun SweepingSearchPaperMagnifier(isActive: Boolean) {
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
    val progress = rememberIllustrationEntranceProgress(isActive)
    SearchPaperLayer(
        path = silhouette,
        face = searchPaperFrameColor,
        modifier = Modifier.graphicsLayer {
            transformOrigin = TransformOrigin(videoSearchLensCenter.x / 128f, videoSearchLensCenter.y / 128f)
            scaleX = 0.85f
            scaleY = 0.85f
            // Search once across the subject, then settle at the original resting position.
            val sweep = sin(PI * progress.value).toFloat()
            translationX = (-24).dp.toPx() * sweep
            translationY = (-8).dp.toPx() * sweep
        },
        elevation = 2f,
    ) {
        drawCircle(searchPaperLensColor, 16.2f, videoSearchLensCenter)
    }
}

/** Draws one paper silhouette with native elevation. */
@Composable
private fun SearchPaperLayer(
    path: Path,
    face: Color,
    modifier: Modifier = Modifier,
    elevation: Float = 4f,
    decoration: DrawScope.() -> Unit = {},
) {
    val shape = remember(path) {
        GenericShape { size, _ ->
            addPath(path)
            transform(Matrix().apply { scale(size.width / 128f, size.height / 128f) })
        }
    }
    Canvas(modifier.size(128.dp).graphicsLayer {
        this.shape = shape
        shadowElevation = elevation.dp.toPx()
    }) {
        scale(size.width / 128f, size.height / 128f, pivot = Offset.Zero) {
            drawPath(path, face)
            clipPath(path) { decoration() }
        }
    }
}

private val videoFilmBounds = Rect(12f, 25f, 116f, 103f)

/** Reference film silhouette: a 4:3 sheet with four rounded perforations on each side. */
private fun videoFilmPath(): Path {
    val sheet = Path().apply {
        addRoundRect(RoundRect(videoFilmBounds, CornerRadius(6f)))
    }
    val holes = Path().apply {
        val left = videoFilmBounds.left
        val right = videoFilmBounds.right
        repeat(4) { row ->
            val top = videoFilmBounds.top + 9f + row * 17.3f
            addRoundRect(RoundRect(Rect(left + 8.7f, top, left + 17.4f, top + 8.7f), CornerRadius(1.3f)))
            addRoundRect(RoundRect(Rect(right - 17.4f, top, right - 8.7f, top + 8.7f), CornerRadius(1.3f)))
        }
    }
    return Path.combine(PathOperation.Difference, sheet, holes)
}

@Preview(name = "Search history paper", showBackground = false)
@Composable
private fun SearchHistoryEmptyLogoPreview() {
    KIRAKIRATheme { SearchHistoryEmptyLogo() }
}

@Preview(name = "Video search paper", showBackground = false)
@Composable
private fun SearchVideosEmptyLogoPreview() {
    KIRAKIRATheme { SearchVideosEmptyLogo() }
}
