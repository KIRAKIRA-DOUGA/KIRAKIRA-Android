package moe.kirakira.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.shadow.ShadowContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Static Material Design 2 height for large colored cards; interactive cards keep their own elevation. */
internal val CardColorShadowElevation = 2.dp

internal fun Modifier.cardColorShadow(
    shape: Shape,
    containerColor: Color,
    elevation: Dp = CardColorShadowElevation,
    opacityScale: Float = 0.5f,
): Modifier = materialColorShadow(shape, containerColor, opacityScale) { elevation.value }

internal fun Modifier.materialColorShadow(
    shape: Shape,
    containerColor: Color,
    opacityScale: Float = 1f,
    opacity: () -> Float = { 1f },
    tint: () -> Color = { materialShadowColor(containerColor) },
    elevation: () -> Float,
): Modifier {
    return drawWithCache {
        // Observe animated shape state before consulting Compose's shadow cache. A stable,
        // mutable Shape is otherwise cached by identity and can keep an old pressed outline.
        val resolvedShape = ResolvedShadowShape(shape.createOutline(size, layoutDirection, this))
        val shadowContext = ShadowContext()
        val level = materialShadowLevel(elevation())
        val upperLevels = (level - 2f).coerceIn(0f, 3f)
        val ambient = shadowContext.createDropShadowPainter(
            resolvedShape,
            Shadow(
                radius = (3f * level.coerceIn(0f, 2f) + 2f * upperLevels).dp,
                spread = (level.coerceIn(0f, 4f) + 2f * (level - 4f).coerceIn(0f, 1f)).dp,
                color = Color.White,
                alpha = 0.25f * opacityScale,
                offset = DpOffset(0.dp, (level.coerceIn(0f, 2f) + 2f * upperLevels).dp),
            ),
        )
        val firstLevel = level.coerceIn(0f, 1f)
        val lastLevel = (level - 4f).coerceIn(0f, 1f)
        val key = shadowContext.createDropShadowPainter(
            resolvedShape,
            Shadow(
                radius = (2f * firstLevel + (level - 2f).coerceIn(0f, 1f) + lastLevel).dp,
                spread = 0.dp,
                color = Color.White,
                alpha = 0.5f * opacityScale,
                offset = DpOffset(0.dp, (firstLevel + (level - 3f).coerceIn(0f, 1f) + 2f * lastLevel).dp),
            ),
        )
        onDrawBehind {
            val shadowColor = tint()
            val shadowOpacity = (opacity() * shadowColor.alpha).coerceIn(0f, 1f)
            if (shadowOpacity == 0f) return@onDrawBehind
            val colorFilter = ColorFilter.tint(shadowColor.copy(alpha = 1f))
            with(ambient) { draw(size, alpha = shadowOpacity, colorFilter = colorFilter) }
            with(key) { draw(size, alpha = shadowOpacity, colorFilter = colorFilter) }
        }
    }
}

/** Neutral shadows use black at 30% of the colored shadow's strength. */
internal fun materialShadowColor(containerColor: Color): Color {
    val maximum = maxOf(containerColor.red, containerColor.green, containerColor.blue)
    val minimum = minOf(containerColor.red, containerColor.green, containerColor.blue)
    return if (maximum - minimum <= 0.02f) {
        Color.Black.copy(alpha = containerColor.alpha * 0.3f)
    } else containerColor
}

// Material Web elevation levels and shadow geometry; interpolate intermediate heights.
// https://github.com/material-components/material-web/blob/main/elevation/internal/_elevation.scss
private fun materialShadowLevel(elevation: Float): Float = when {
    elevation <= 1f -> elevation.coerceAtLeast(0f)
    elevation <= 3f -> 1f + (elevation - 1f) / 2f
    elevation <= 6f -> 2f + (elevation - 3f) / 3f
    elevation <= 8f -> 3f + (elevation - 6f) / 2f
    else -> 4f + ((elevation - 8f) / 4f).coerceAtMost(1f)
}

/** A fixed outline is safe to use as a shadow renderer cache key. */
private class ResolvedShadowShape(private val outline: Outline) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = outline
}
