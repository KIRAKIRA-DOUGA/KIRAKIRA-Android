package moe.kirakira.ui.theme

import android.util.LruCache
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamicColorScheme

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
    val argb = seedColor.toArgb() or 0xFF000000.toInt()
    return remember(argb, darkTheme) {
        SeedColorSchemes.get(argb, darkTheme)
    }
}

private data class SeedColorSchemeKey(val argb: Int, val darkTheme: Boolean)

private object SeedColorSchemes {
    private val lightNeutral by lazy { neutralColorScheme(darkTheme = false) }
    private val darkNeutral by lazy { neutralColorScheme(darkTheme = true) }
    private val schemes = object : LruCache<SeedColorSchemeKey, ColorScheme>(32) {
        override fun create(key: SeedColorSchemeKey): ColorScheme = classicAccentColorScheme(
            seed = Color(key.argb),
            darkTheme = key.darkTheme,
            neutral = if (key.darkTheme) darkNeutral else lightNeutral,
        )
    }

    fun get(argb: Int, darkTheme: Boolean): ColorScheme = checkNotNull(schemes[SeedColorSchemeKey(argb, darkTheme)])

    private fun neutralColorScheme(darkTheme: Boolean): ColorScheme = dynamicColorScheme(
        seedColor = Color.Gray,
        isDark = darkTheme,
        style = PaletteStyle.Monochrome,
        specVersion = ColorSpec.SpecVersion.SPEC_2021,
    )
}
