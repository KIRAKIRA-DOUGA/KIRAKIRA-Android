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

    private val _appearance = MutableStateFlow(AppearanceSettings())
    val appearance: StateFlow<AppearanceSettings> = _appearance.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = withContext(Dispatchers.IO) {
                store.read(defaultColorArgb = KIRAKIRAPink.toArgb())
            }
            _appearance.value = AppearanceSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.name == settings.mode } ?: ThemeMode.SYSTEM,
                themeColors = settings.colors.let {
                    ThemeColorSettings(it.useSystemColors, it.seedColorArgb, it.customColorArgb, it.useCustomColor)
                },
                predictiveBackEnabled = settings.predictiveBackEnabled,
                backgroundDepthEnabled = settings.backgroundDepthEnabled,
            )
            _isReady.value = true
        }
    }

    /** Persists only the fields that changed; each keeps its existing preference key. */
    fun updateAppearance(settings: AppearanceSettings) {
        val current = _appearance.value
        val next = settings.copy(
            themeColors = settings.themeColors.copy(
                seedColorArgb = settings.themeColors.seedColorArgb or 0xFF000000.toInt(),
                customColorArgb = settings.themeColors.customColorArgb or 0xFF000000.toInt(),
            ),
        )
        if (next.themeMode != current.themeMode) store.setMode(next.themeMode.name)
        if (next.themeColors != current.themeColors) {
            val colors = next.themeColors
            store.setColors(
                StoredThemeColors(colors.useSystemColors, colors.seedColorArgb, colors.customColorArgb, colors.useCustomColor),
            )
        }
        if (next.predictiveBackEnabled != current.predictiveBackEnabled) {
            store.setPredictiveBackEnabled(next.predictiveBackEnabled)
        }
        if (next.backgroundDepthEnabled != current.backgroundDepthEnabled) {
            store.setBackgroundDepthEnabled(next.backgroundDepthEnabled)
        }
        _appearance.value = next
    }
}
