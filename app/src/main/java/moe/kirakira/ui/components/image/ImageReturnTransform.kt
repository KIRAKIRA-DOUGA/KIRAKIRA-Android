package moe.kirakira.ui.components.image

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace

/** Visually unwind zoom during a return gesture without mutating Telephoto's saved zoom state. */
@OptIn(ExperimentalTelephotoApi::class)
internal fun Modifier.imageReturnTransform(
    state: ZoomableImageState,
    visibility: () -> Float,
): Modifier = graphicsLayer {
    val progress = visibility().coerceIn(0f, 1f)
    with(state.zoomableState.coordinateSystem) {
        val current = contentBounds(clipToViewport = false).rectIn(CoordinateSpace.Viewport)
        val initial = unscaledContentBounds(clipToViewport = false).rectIn(CoordinateSpace.Viewport)
        if (current.width > 0f && current.height > 0f && initial.width > 0f && initial.height > 0f) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = lerp(initial.width / current.width, 1f, progress)
            scaleY = lerp(initial.height / current.height, 1f, progress)
            translationX = lerp(initial.left, current.left, progress) - current.left * scaleX
            translationY = lerp(initial.top, current.top, progress) - current.top * scaleY
        }
    }
}
