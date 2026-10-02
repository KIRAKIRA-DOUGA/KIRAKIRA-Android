package moe.kirakira.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun Modifier.connectedListItemShadow(index: Int, count: Int): Modifier {
    if (LocalConnectedListGroup.current) return this
    val shape = connectedListItemShapes(index, count).shape
    if (count == 1) return shadow(ConnectedListShadowElevation, shape, clip = false)
    val first = index == 0
    val last = index == count - 1
    val shadowShape = remember(shape, first, last) { ConnectedListShadowShape(shape, first, last) }
    return drawWithContent {
        val overflow = ConnectedListShadowElevation.toPx() * SHADOW_OVERFLOW_MULTIPLIER
        clipRect(
            left = -overflow,
            top = if (first) -overflow else 0f,
            right = size.width + overflow,
            bottom = size.height + if (last) overflow else 0f,
        ) {
            this@drawWithContent.drawContent()
        }
    }.shadow(ConnectedListShadowElevation, shadowShape, clip = false)
}

private const val SHADOW_OVERFLOW_MULTIPLIER = 8f

private class ConnectedListShadowShape(
    private val shape: Shape,
    private val first: Boolean,
    private val last: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val overflow = with(density) { ConnectedListShadowElevation.toPx() * SHADOW_OVERFLOW_MULTIPLIER }
        val top = if (first) 0f else overflow
        val bottom = if (last) 0f else overflow
        val outline = shape.createOutline(Size(size.width, size.height + top + bottom), layoutDirection, density)
        return Outline.Generic(Path().apply {
            addOutline(outline)
            translate(Offset(0f, -top))
        })
    }
}
