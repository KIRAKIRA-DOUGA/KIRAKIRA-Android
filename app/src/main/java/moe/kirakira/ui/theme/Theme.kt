package moe.kirakira.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTonalElevationEnabled
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun KIRAKIRATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    seedColor: Color = KIRAKIRAPink,
    colorAlgorithm: ThemeColorAlgorithm = ThemeColorAlgorithm.TONAL_SPOT,
    shadowsEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val usesSystemColors = dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val classicAccent = !usesSystemColors && colorAlgorithm == ThemeColorAlgorithm.CLASSIC_ACCENT
    val colorScheme = when {
        usesSystemColors -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> rememberSeedColorScheme(
            seedColor = seedColor,
            darkTheme = darkTheme,
            algorithm = colorAlgorithm,
        )
    }

    CompositionLocalProvider(
        LocalClassicAccent provides classicAccent,
        LocalShadowsEnabled provides shadowsEnabled,
        LocalTonalElevationEnabled provides (!classicAccent && LocalTonalElevationEnabled.current),
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
    algorithm: ThemeColorAlgorithm = ThemeColorAlgorithm.TONAL_SPOT,
): ColorScheme {
    if (algorithm == ThemeColorAlgorithm.CLASSIC_ACCENT) {
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
    return rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        style = requireNotNull(algorithm.paletteStyle),
        specVersion = algorithm.specVersion,
    )
}
