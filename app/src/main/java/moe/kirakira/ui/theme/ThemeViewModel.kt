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

    private val _shadowsEnabled = MutableStateFlow(false)
    val shadowsEnabled: StateFlow<Boolean> = _shadowsEnabled.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            val (mode, colors, shadows) = withContext(Dispatchers.IO) {
                Triple(
                    loadThemeMode(),
                    loadThemeColors(),
                    sharedPreferences.getBoolean("theme_shadows_enabled", false),
                )
            }
            _themeMode.value = mode
            _themeColors.value = colors
            _shadowsEnabled.value = shadows
            _isReady.value = true
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        sharedPreferences.edit { putString("theme_mode", mode.name) }
        _themeMode.value = mode
    }

    fun setShadowsEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean("theme_shadows_enabled", enabled) }
        _shadowsEnabled.value = enabled
    }

    fun setThemeColors(colors: ThemeColorSettings) {
        val opaqueColors = colors.copy(
            seedColorArgb = colors.seedColorArgb or 0xFF000000.toInt(),
            customColorArgb = colors.customColorArgb or 0xFF000000.toInt(),
        )
        sharedPreferences.edit {
            putBoolean("theme_system_colors", opaqueColors.useSystemColors)
            putInt("theme_seed_color", opaqueColors.seedColorArgb)
            putString("theme_color_algorithm", opaqueColors.algorithm.name)
            putInt("theme_custom_color", opaqueColors.customColorArgb)
            putBoolean("theme_use_custom_color", opaqueColors.useCustomColor)
        }
        _themeColors.value = opaqueColors
    }

    private fun loadThemeColors(): ThemeColorSettings {
        val seedColorArgb = sharedPreferences.getInt(
            "theme_seed_color",
            ThemeColorSettings().seedColorArgb,
        ) or 0xFF000000.toInt()
        return ThemeColorSettings(
            useSystemColors = sharedPreferences.getBoolean("theme_system_colors", false),
            seedColorArgb = seedColorArgb,
            algorithm = ThemeColorAlgorithm.fromStoredName(
                sharedPreferences.getString("theme_color_algorithm", null),
            ),
            // Older versions stored only the active seed; preserve it when migrating.
            customColorArgb = sharedPreferences.getInt("theme_custom_color", seedColorArgb) or 0xFF000000.toInt(),
            useCustomColor = sharedPreferences.getBoolean(
                "theme_use_custom_color",
                ThemePresetColor.entries.none { it.color.toArgb() == seedColorArgb },
            ),
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
