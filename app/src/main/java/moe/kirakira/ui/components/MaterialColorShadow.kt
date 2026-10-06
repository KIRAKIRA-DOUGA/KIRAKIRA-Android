package moe.kirakira.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal fun Modifier.materialColorShadow(
    shape: Shape,
    containerColor: Color,
    opacityScale: Float = 1f,
    elevation: () -> Float,
): Modifier {
    val maximum = maxOf(containerColor.red, containerColor.green, containerColor.blue)
    val minimum = minOf(containerColor.red, containerColor.green, containerColor.blue)
    val neutral = maximum - minimum <= 0.02f
    val shadowColor = if (neutral) Color.Black else containerColor.copy(alpha = 1f)
    return dropShadow(shape) {
        val level = materialShadowLevel(elevation())
        val upperLevels = (level - 2f).coerceIn(0f, 3f)
        radius = (3f * level.coerceIn(0f, 2f) + 2f * upperLevels).dp.toPx()
        spread = (level.coerceIn(0f, 4f) + 2f * (level - 4f).coerceIn(0f, 1f)).dp.toPx()
        color = shadowColor
        alpha = (if (neutral) 0.15f else 0.25f) * containerColor.alpha * opacityScale
        offset = Offset(0f, (level.coerceIn(0f, 2f) + 2f * upperLevels).dp.toPx())
    }.dropShadow(shape) {
        val level = materialShadowLevel(elevation())
        val firstLevel = level.coerceIn(0f, 1f)
        val lastLevel = (level - 4f).coerceIn(0f, 1f)
        radius = (2f * firstLevel + (level - 2f).coerceIn(0f, 1f) + lastLevel).dp.toPx()
        spread = 0f
        color = shadowColor
        alpha = (if (neutral) 0.3f else 0.5f) * containerColor.alpha * opacityScale
        offset = Offset(0f, (firstLevel + (level - 3f).coerceIn(0f, 1f) + 2f * lastLevel).dp.toPx())
    }
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
