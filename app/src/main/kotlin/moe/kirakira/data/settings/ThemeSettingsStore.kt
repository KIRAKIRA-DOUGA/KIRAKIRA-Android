package moe.kirakira.data.settings

import android.content.Context
import androidx.core.content.edit

internal data class StoredThemeColors(
    val useSystemColors: Boolean = false,
    val seedColorArgb: Int,
    val customColorArgb: Int,
    val useCustomColor: Boolean = false,
)

internal data class StoredThemeSettings(
    val mode: String = "SYSTEM",
    val colors: StoredThemeColors,
    val predictiveBackEnabled: Boolean = false,
    val backgroundDepthEnabled: Boolean = true,
)

internal class ThemeSettingsStore(context: Context) {
    private val preferences by lazy {
        settingsPreferences(context)
    }

    fun read(defaultColorArgb: Int): StoredThemeSettings {
        val defaults = StoredThemeSettings(
            colors = StoredThemeColors(seedColorArgb = defaultColorArgb, customColorArgb = defaultColorArgb),
        )
        val colors = StoredThemeColors(
            useSystemColors = preferences.getBoolean("theme_system_colors", defaults.colors.useSystemColors),
            seedColorArgb = preferences.getInt("theme_seed_color", defaults.colors.seedColorArgb) or 0xFF000000.toInt(),
            customColorArgb = preferences.getInt("theme_custom_color", defaults.colors.customColorArgb) or 0xFF000000.toInt(),
            useCustomColor = preferences.getBoolean("theme_use_custom_color", defaults.colors.useCustomColor),
        )
        return StoredThemeSettings(
            mode = preferences.getString("theme_mode", defaults.mode) ?: defaults.mode,
            colors = colors,
            predictiveBackEnabled = preferences.getBoolean("predictive_back_enabled", defaults.predictiveBackEnabled),
            backgroundDepthEnabled = preferences.getBoolean("background_depth_enabled", defaults.backgroundDepthEnabled),
        )
    }

    fun setMode(mode: String) {
        preferences.edit { putString("theme_mode", mode) }
    }

    fun setColors(colors: StoredThemeColors) {
        preferences.edit {
            putBoolean("theme_system_colors", colors.useSystemColors)
            putInt("theme_seed_color", colors.seedColorArgb)
            putInt("theme_custom_color", colors.customColorArgb)
            putBoolean("theme_use_custom_color", colors.useCustomColor)
        }
    }

    fun setPredictiveBackEnabled(enabled: Boolean) {
        preferences.edit { putBoolean("predictive_back_enabled", enabled) }
    }

    fun setBackgroundDepthEnabled(enabled: Boolean) {
        preferences.edit { putBoolean("background_depth_enabled", enabled) }
    }
}
