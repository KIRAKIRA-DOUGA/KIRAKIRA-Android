package moe.kirakira.ui.theme

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.kirakira.data.settings.StoredThemeColors
import moe.kirakira.data.settings.ThemeSettingsStore

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ThemeSettingsStore(application)

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
            val settings = withContext(Dispatchers.IO) {
                store.read(
                    defaultColorArgb = KIRAKIRAPink.toArgb(),
                    presetMigrations = mapOf(
                        0xFF537FE7.toInt() to ThemePresetColor.BLUE.color.toArgb(),
                        0xFF9C6ADE.toInt() to ThemePresetColor.PURPLE.color.toArgb(),
                        0xFF008577.toInt() to ThemePresetColor.GREEN.color.toArgb(),
                        0xFFE5A23D.toInt() to ThemePresetColor.YELLOW.color.toArgb(),
                        0xFFD97757.toInt() to ThemePresetColor.RED.color.toArgb(),
                    ),
                )
            }
            _themeMode.value = ThemeMode.entries.firstOrNull { it.name == settings.mode } ?: ThemeMode.SYSTEM
            _themeColors.value = settings.colors.let {
                ThemeColorSettings(it.useSystemColors, it.seedColorArgb, it.customColorArgb, it.useCustomColor)
            }
            _predictiveBackEnabled.value = settings.predictiveBackEnabled
            _isReady.value = true
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        store.setMode(mode.name)
        _themeMode.value = mode
    }

    fun setThemeColors(colors: ThemeColorSettings) {
        val opaqueColors = colors.copy(
            seedColorArgb = colors.seedColorArgb or 0xFF000000.toInt(),
            customColorArgb = colors.customColorArgb or 0xFF000000.toInt(),
        )
        store.setColors(
            StoredThemeColors(
                opaqueColors.useSystemColors, opaqueColors.seedColorArgb,
                opaqueColors.customColorArgb, opaqueColors.useCustomColor,
            ),
        )
        _themeColors.value = opaqueColors
    }

    fun setPredictiveBackEnabled(enabled: Boolean) {
        store.setPredictiveBackEnabled(enabled)
        _predictiveBackEnabled.value = enabled
    }
}
