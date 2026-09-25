package moe.kirakira.ui.theme

import androidx.annotation.StringRes
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import moe.kirakira.R

enum class ThemeColorAlgorithm(
    val paletteStyle: PaletteStyle?,
    @StringRes val titleRes: Int,
) {
    TONAL_SPOT(
        PaletteStyle.TonalSpot,
        R.string.theme_algorithm_tonal_spot,
    ),
    CLASSIC_ACCENT(
        null,
        R.string.theme_algorithm_classic_accent,
    ),
    MONOCHROME(
        PaletteStyle.Monochrome,
        R.string.theme_algorithm_monochrome,
    ),
    NEUTRAL(
        PaletteStyle.Neutral,
        R.string.theme_algorithm_neutral,
    ),
    VIBRANT(
        PaletteStyle.Vibrant,
        R.string.theme_algorithm_vibrant,
    ),
    EXPRESSIVE(
        PaletteStyle.Expressive,
        R.string.theme_algorithm_expressive,
    ),
    FIDELITY(
        PaletteStyle.Fidelity,
        R.string.theme_algorithm_fidelity,
    ),
    CONTENT(
        PaletteStyle.Content,
        R.string.theme_algorithm_content,
    ),
    RAINBOW(
        PaletteStyle.Rainbow,
        R.string.theme_algorithm_rainbow,
    ),
    FRUIT_SALAD(
        PaletteStyle.FruitSalad,
        R.string.theme_algorithm_fruit_salad,
    );

    // MCU defines the 2025 specification for these four variants; the others use 2021.
    internal val specVersion: ColorSpec.SpecVersion
        get() = when (this) {
            TONAL_SPOT, NEUTRAL, VIBRANT, EXPRESSIVE -> ColorSpec.SpecVersion.SPEC_2025
            else -> ColorSpec.SpecVersion.SPEC_2021
        }

    companion object {
        fun fromStoredName(name: String?): ThemeColorAlgorithm =
            entries.firstOrNull { it.name == name } ?: TONAL_SPOT
    }
}
