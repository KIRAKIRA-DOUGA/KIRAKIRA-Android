package moe.kirakira.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import moe.kirakira.R

enum class ThemePresetColor(val color: Color, @StringRes val titleRes: Int) {
    BRAND(KIRAKIRAPink, R.string.theme_color_pink),
    PURPLE(Color(0xFF9C6ADE), R.string.theme_color_purple),
    BLUE(Color(0xFF537FE7), R.string.theme_color_blue),
    GREEN(Color(0xFF008577), R.string.theme_color_green),
    AMBER(Color(0xFFE5A23D), R.string.theme_color_amber),
    CORAL(Color(0xFFD97757), R.string.theme_color_coral),
}
