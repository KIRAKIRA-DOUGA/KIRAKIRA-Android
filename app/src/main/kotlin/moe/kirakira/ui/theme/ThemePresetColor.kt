package moe.kirakira.ui.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import moe.kirakira.R

enum class ThemePresetColor(
    val color: Color,
    @StringRes val titleRes: Int,
    @StringRes val characterRes: Int,
    @DrawableRes val artworkRes: Int,
) {
    BRAND(KIRAKIRAPink, R.string.theme_color_pink, R.string.theme_character_cocoa, R.drawable.theme_palette_pink),
    BLUE(Color(0xFF4581E1), R.string.theme_color_blue, R.string.theme_character_chino, R.drawable.theme_palette_blue),
    PURPLE(Color(0xFFB044B0), R.string.theme_color_purple, R.string.theme_character_rize, R.drawable.theme_palette_purple),
    GREEN(Color(0xFF46A12F), R.string.theme_color_green, R.string.theme_character_chiya, R.drawable.theme_palette_green),
    YELLOW(Color(0xFFF98D00), R.string.theme_color_yellow, R.string.theme_character_syaro, R.drawable.theme_palette_yellow),
    CYAN(Color(0xFF199BB6), R.string.theme_color_cyan, R.string.theme_character_maya, R.drawable.theme_palette_cyan),
    RED(Color(0xFFDD1818), R.string.theme_color_red, R.string.theme_character_megu, R.drawable.theme_palette_red),
}
