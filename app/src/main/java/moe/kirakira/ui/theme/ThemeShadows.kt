package moe.kirakira.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

private val TopAppBarShadowElevation = 4.dp
private val NavigationBarShadowElevation = 4.dp

internal fun Modifier.topAppBarShadow(): Modifier = shadow(
    elevation = TopAppBarShadowElevation,
    shape = RectangleShape,
    clip = false,
)

internal fun Modifier.bottomEdgeShadow(): Modifier = drawWithContent {
    clipRect(
        left = 0f,
        top = 0f,
        right = size.width,
        bottom = size.height + TopAppBarShadowElevation.toPx() * 8f,
    ) {
        this@drawWithContent.drawContent()
    }
}.shadow(elevation = TopAppBarShadowElevation, shape = RectangleShape, clip = false)

internal fun Modifier.navigationBarShadow(shape: Shape = RectangleShape): Modifier = shadow(
    elevation = NavigationBarShadowElevation,
    shape = shape,
    clip = false,
)
