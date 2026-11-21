package moe.kirakira.ui.theme

import androidx.annotation.StringRes
import moe.kirakira.R

enum class ThemeMode(@StringRes val titleRes: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark)
}
