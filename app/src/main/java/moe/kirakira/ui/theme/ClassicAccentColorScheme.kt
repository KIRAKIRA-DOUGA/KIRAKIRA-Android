package moe.kirakira.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.utils.ColorUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 在主题层生成全部颜色角色，组件直接继承原色及其配套前景。 */
internal fun classicAccentColorScheme(seed: Color, darkTheme: Boolean, neutral: ColorScheme): ColorScheme {
    val primary = Color(seed.toArgb() or 0xFF000000.toInt())
    val container = accentTone(primary, if (darkTheme) 30.0 else 90.0)
    val fixed = accentTone(primary, 90.0)
    val fixedDim = accentTone(primary, 80.0)
    val surface = if (darkTheme) neutral.surfaceContainerLow else Color.White
    val pageBackground = if (darkTheme) neutral.surfaceContainer else Color(0xFFF5F5F5)
    return neutral.copy(
        primary = primary,
        onPrimary = preferredWhiteOnAccent(primary, minimumContrast = 2.5),
        primaryContainer = container,
        onPrimaryContainer = readableAccent(
            accentTone(primary, if (darkTheme) 90.0 else 10.0), container,
        ),
        secondaryContainer = container,
        onSecondaryContainer = readableAccent(
            accentTone(primary, if (darkTheme) 90.0 else 10.0), container,
        ),
        inversePrimary = readableAccent(primary, neutral.inverseSurface),
        primaryFixed = fixed,
        primaryFixedDim = fixedDim,
        onPrimaryFixed = accentTone(primary, 10.0),
        onPrimaryFixedVariant = accentTone(primary, 30.0),
        background = if (darkTheme) pageBackground else Color.White,
        surfaceContainer = pageBackground,
        surface = surface,
        // surfaceColorAtElevation replaces the tint's alpha, so Transparent would turn into black.
        // Matching the surface also keeps direct calls neutral when tonal elevation is disabled.
        surfaceTint = surface,
    )
}

private fun readableAccent(seed: Color, background: Color, minimumContrast: Double = 4.5): Color {
    if (contrastRatio(seed, background) >= minimumContrast) return seed
    val hct = Hct.fromInt(seed.toArgb())
    // Search from the original tone outwards, checking the final sRGB color after gamut mapping.
    // A one-tone step keeps the chosen variant close while bounding work for the color picker.
    return (0..100).asSequence()
        .sortedBy { abs(it - hct.tone) }
        .map { accentTone(seed, it.toDouble()) }
        .firstOrNull { contrastRatio(it, background) >= minimumContrast }
        ?: contrastingBlackOrWhite(background)
}

private fun accentTone(seed: Color, tone: Double): Color {
    // HCT can assign a tiny chroma to sRGB grays; preserve truly achromatic selections explicitly.
    if (seed.red == seed.green && seed.green == seed.blue) {
        return Color(ColorUtils.argbFromLstar(tone))
    }
    val hct = Hct.fromInt(seed.toArgb())
    return Color(Hct.from(hct.hue, hct.chroma, tone).toInt())
}

private fun contrastingBlackOrWhite(background: Color): Color =
    if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) Color.Black else Color.White

/** Prefer white for on-accent content once it meets the theme's chosen contrast target. */
private fun preferredWhiteOnAccent(background: Color, minimumContrast: Double): Color =
    if (contrastRatio(Color.White, background) >= minimumContrast) Color.White else Color.Black

private fun contrastRatio(first: Color, second: Color): Double {
    val firstLuminance = first.luminance().toDouble()
    val secondLuminance = second.luminance().toDouble()
    return (max(firstLuminance, secondLuminance) + 0.05) / (min(firstLuminance, secondLuminance) + 0.05)
}
