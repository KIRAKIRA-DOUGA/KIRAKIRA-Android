package moe.kirakira.ui.theme

import android.app.Application
import android.content.Context
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

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            val (mode, colors) = withContext(Dispatchers.IO) {
                loadThemeMode() to loadThemeColors()
            }
            _themeMode.value = mode
            _themeColors.value = colors
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

    private fun loadThemeColors(): ThemeColorSettings {
        val defaults = ThemeColorSettings()
        return ThemeColorSettings(
            useSystemColors = sharedPreferences.getBoolean("theme_system_colors", false),
            seedColorArgb = sharedPreferences.getInt("theme_seed_color", defaults.seedColorArgb) or 0xFF000000.toInt(),
            customColorArgb = sharedPreferences.getInt("theme_custom_color", defaults.customColorArgb) or
                0xFF000000.toInt(),
            useCustomColor = sharedPreferences.getBoolean("theme_use_custom_color", false),
        )
    }

    private fun loadThemeMode(): ThemeMode {
        val name = sharedPreferences.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }
}
