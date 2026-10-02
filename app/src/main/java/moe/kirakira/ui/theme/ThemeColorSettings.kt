package moe.kirakira.ui.theme

import androidx.compose.ui.graphics.toArgb
import java.util.Locale

data class ThemeColorSettings(
    val useSystemColors: Boolean = false,
    val seedColorArgb: Int = KIRAKIRAPink.toArgb(),
    val customColorArgb: Int = KIRAKIRAPink.toArgb(),
    val useCustomColor: Boolean = false,
) {
    fun selectWallpaperColor(): ThemeColorSettings = copy(useSystemColors = true)

    fun selectPreset(preset: ThemePresetColor): ThemeColorSettings = copy(
        useSystemColors = false,
        seedColorArgb = preset.color.toArgb(),
        useCustomColor = false,
    )

    fun selectCustomColor(argb: Int): ThemeColorSettings = copy(
        useSystemColors = false,
        seedColorArgb = argb,
        customColorArgb = argb,
        useCustomColor = true,
    )
}

fun formatThemeColor(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

fun parseThemeColor(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return hex.toIntOrNull(16)?.or(0xFF000000.toInt())
}
