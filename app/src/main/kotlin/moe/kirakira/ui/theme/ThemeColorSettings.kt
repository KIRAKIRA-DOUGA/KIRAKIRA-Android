package moe.kirakira.ui.theme

import androidx.compose.ui.graphics.toArgb
import moe.kirakira.core.color.formatRgbColor

data class ThemeColorSettings(
    val useSystemColors: Boolean = false,
    val seedColorArgb: Int = KIRAKIRAPink.toArgb(),
    val customColorArgb: Int = KIRAKIRAPink.toArgb(),
    val useCustomColor: Boolean = false,
) {
    fun selectSystemAccentColor(): ThemeColorSettings = copy(useSystemColors = true)

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

fun formatThemeColor(argb: Int): String = formatRgbColor(argb)
