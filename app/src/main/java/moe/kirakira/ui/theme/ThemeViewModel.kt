package moe.kirakira.ui.theme

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences by lazy {
        application.getSharedPreferences("kirakira_settings", Context.MODE_PRIVATE)
    }

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _themeColors = MutableStateFlow(ThemeColorSettings())
    val themeColors: StateFlow<ThemeColorSettings> = _themeColors.asStateFlow()

    private val _predictiveBackEnabled = MutableStateFlow(false)
    val predictiveBackEnabled: StateFlow<Boolean> = _predictiveBackEnabled.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            val (mode, colors, predictiveBack) = withContext(Dispatchers.IO) {
                Triple(loadThemeMode(), loadThemeColors(), loadPredictiveBackEnabled())
            }
            _themeMode.value = mode
            _themeColors.value = colors
            _predictiveBackEnabled.value = predictiveBack
            _isReady.value = true
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        sharedPreferences.edit { putString("theme_mode", mode.name) }
        _themeMode.value = mode
    }

    fun setThemeColors(colors: ThemeColorSettings) {
        val opaqueColors = colors.copy(
            seedColorArgb = colors.seedColorArgb or 0xFF000000.toInt(),
            customColorArgb = colors.customColorArgb or 0xFF000000.toInt(),
        )
        sharedPreferences.edit {
            putBoolean("theme_system_colors", opaqueColors.useSystemColors)
            putInt("theme_seed_color", opaqueColors.seedColorArgb)
            putInt("theme_custom_color", opaqueColors.customColorArgb)
            putBoolean("theme_use_custom_color", opaqueColors.useCustomColor)
        }
        _themeColors.value = opaqueColors
    }

    fun setPredictiveBackEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("predictive_back_enabled", enabled) }
        _predictiveBackEnabled.value = enabled
    }

    private fun loadThemeColors(): ThemeColorSettings {
        val defaults = ThemeColorSettings()
        val colors = ThemeColorSettings(
            useSystemColors = sharedPreferences.getBoolean("theme_system_colors", false),
            seedColorArgb = sharedPreferences.getInt("theme_seed_color", defaults.seedColorArgb) or 0xFF000000.toInt(),
            customColorArgb = sharedPreferences.getInt("theme_custom_color", defaults.customColorArgb) or
                0xFF000000.toInt(),
            useCustomColor = sharedPreferences.getBoolean("theme_use_custom_color", false),
        )
        if (colors.useCustomColor) return colors
        val preset = when (colors.seedColorArgb) {
            0xFF537FE7.toInt() -> ThemePresetColor.BLUE
            0xFF9C6ADE.toInt() -> ThemePresetColor.PURPLE
            0xFF008577.toInt() -> ThemePresetColor.GREEN
            0xFFE5A23D.toInt() -> ThemePresetColor.YELLOW
            0xFFD97757.toInt() -> ThemePresetColor.RED
            else -> return colors
        }
        // Migrate only the stored manual preset, including when wallpaper colors are active.
        val migrated = colors.copy(seedColorArgb = preset.color.toArgb())
        sharedPreferences.edit { putInt("theme_seed_color", migrated.seedColorArgb) }
        return migrated
    }

    private fun loadThemeMode(): ThemeMode {
        val name = sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    private fun loadPredictiveBackEnabled(): Boolean =
        sharedPreferences.getBoolean("predictive_back_enabled", false)
}
