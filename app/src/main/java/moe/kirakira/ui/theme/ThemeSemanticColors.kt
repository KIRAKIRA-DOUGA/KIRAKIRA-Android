package moe.kirakira.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ThemeSemanticColors(
    val success: Color,
    val onSuccess: Color,
)

internal fun themeSemanticColors(darkTheme: Boolean): ThemeSemanticColors = ThemeSemanticColors(
    success = if (darkTheme) Color(0xFF00594F) else Color(0xFF008577),
    onSuccess = Color.White,
)

internal val LocalThemeSemanticColors = staticCompositionLocalOf { themeSemanticColors(darkTheme = false) }

val MaterialTheme.semanticColors: ThemeSemanticColors
    @Composable get() = LocalThemeSemanticColors.current
