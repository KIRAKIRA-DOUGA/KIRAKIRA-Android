package moe.kirakira.ui.theme

import androidx.compose.runtime.Immutable

/** App-wide appearance choices, passed from the Activity host to the appearance page as one value. */
@Immutable
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColors: ThemeColorSettings = ThemeColorSettings(),
    val predictiveBackEnabled: Boolean = false,
    val backgroundDepthEnabled: Boolean = true,
)
