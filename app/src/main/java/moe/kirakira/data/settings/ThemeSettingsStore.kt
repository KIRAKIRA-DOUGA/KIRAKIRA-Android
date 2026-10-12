package moe.kirakira.data.settings

import android.content.Context
import androidx.core.content.edit

internal data class StoredThemeColors(
    val useSystemColors: Boolean,
    val seedColorArgb: Int,
    val customColorArgb: Int,
    val useCustomColor: Boolean,
)

internal data class StoredThemeSettings(
    val mode: String,
    val colors: StoredThemeColors,
    val predictiveBackEnabled: Boolean,
)

internal class ThemeSettingsStore(context: Context) {
    private val preferences by lazy {
        context.applicationContext.getSharedPreferences("kirakira_settings", Context.MODE_PRIVATE)
    }

    fun read(defaultColorArgb: Int, presetMigrations: Map<Int, Int>): StoredThemeSettings {
        val colors = StoredThemeColors(
            useSystemColors = preferences.getBoolean("theme_system_colors", false),
            seedColorArgb = preferences.getInt("theme_seed_color", defaultColorArgb) or 0xFF000000.toInt(),
            customColorArgb = preferences.getInt("theme_custom_color", defaultColorArgb) or 0xFF000000.toInt(),
            useCustomColor = preferences.getBoolean("theme_use_custom_color", false),
        )
        // Migrate only the stored manual preset, including when wallpaper colors are active.
        val migratedSeed = if (colors.useCustomColor) null else presetMigrations[colors.seedColorArgb]
        val migratedColors = if (migratedSeed == null) colors else {
            preferences.edit { putInt("theme_seed_color", migratedSeed) }
            colors.copy(seedColorArgb = migratedSeed)
        }
        return StoredThemeSettings(
            mode = preferences.getString("theme_mode", "SYSTEM") ?: "SYSTEM",
            colors = migratedColors,
            predictiveBackEnabled = preferences.getBoolean("predictive_back_enabled", false),
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
}
