package moe.kirakira.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

/** Uses classic accent colors; [dynamicColor] selects the wallpaper accent on Android 12+. */
@Composable
fun KIRAKIRATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    seedColor: Color = KIRAKIRAPink,
    content: @Composable () -> Unit,
) {
    val accentColor = if (dynamicColor) wallpaperAccentColor() ?: seedColor else seedColor
    val colorScheme = rememberSeedColorScheme(seedColor = accentColor, darkTheme = darkTheme)
    val semanticColors = remember(darkTheme) { themeSemanticColors(darkTheme) }

    CompositionLocalProvider(
        LocalTonalElevationEnabled provides false,
        LocalThemeSemanticColors provides semanticColors,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

@Composable
internal fun rememberSeedColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
): ColorScheme {
    val neutral = rememberDynamicColorScheme(
        seedColor = Color.Gray,
        isDark = darkTheme,
        style = PaletteStyle.Monochrome,
        specVersion = ColorSpec.SpecVersion.SPEC_2021,
    )
    return remember(seedColor, darkTheme, neutral) {
        classicAccentColorScheme(seedColor, darkTheme, neutral)
    }
}
