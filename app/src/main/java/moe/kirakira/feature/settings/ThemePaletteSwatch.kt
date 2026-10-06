package moe.kirakira.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.theme.rememberSeedColorScheme

@Composable
internal fun ThemePaletteSwatch(
    seedColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    val scheme = rememberSeedColorScheme(seedColor = seedColor, darkTheme = false)
    Canvas(modifier = modifier.size(32.dp).clip(shape)) {
        drawRect(color = scheme.primary, size = Size(size.width, size.height / 2))
        drawRect(
            color = scheme.surface,
            topLeft = Offset(0f, size.height / 2),
            size = Size(size.width / 2, size.height / 2),
        )
        drawRect(
            color = scheme.surfaceContainer,
            topLeft = Offset(size.width / 2, size.height / 2),
            size = Size(size.width / 2, size.height / 2),
        )
    }
}
