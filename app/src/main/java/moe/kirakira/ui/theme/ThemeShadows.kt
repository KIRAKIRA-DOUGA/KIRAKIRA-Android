package moe.kirakira.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val BarShadowElevation = 4.dp

// Share alpha and elevation so the native outline describes the background's actual opacity.
internal fun Modifier.barSurfaceLayer(
    shape: Shape = RectangleShape,
    alpha: Float = 1f,
    shadowElevation: Dp = BarShadowElevation,
): Modifier = graphicsLayer {
    this.shape = shape
    this.alpha = alpha
    this.shadowElevation = shadowElevation.toPx()
    clip = true
}
