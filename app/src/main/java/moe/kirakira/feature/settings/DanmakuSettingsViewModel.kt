package moe.kirakira.feature.settings

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.kirakira.data.content.DanmakuMode

internal data class DanmakuSettings(
    val enabled: Boolean = true,
    val opacityPercent: Int = 100,
    val fontScalePercent: Int = 100,
    val areaPercent: Int = 100,
    val speedTenths: Int = 10,
    val showRtl: Boolean = true,
    val showLtr: Boolean = true,
    val showTop: Boolean = true,
    val showBottom: Boolean = true,
) {
    fun shows(mode: DanmakuMode): Boolean = when (mode) {
        DanmakuMode.RTL -> showRtl
        DanmakuMode.LTR -> showLtr
        DanmakuMode.TOP -> showTop
        DanmakuMode.BOTTOM -> showBottom
    }

    fun normalized() = copy(
        opacityPercent = opacityPercent.coerceIn(10, 100) / 5 * 5,
        fontScalePercent = fontScalePercent.coerceIn(50, 200) / 5 * 5,
        areaPercent = areaPercent.coerceIn(25, 100) / 25 * 25,
        speedTenths = speedTenths.coerceIn(5, 20),
    )
}

/** Device-wide, non-sensitive preferences shared by the player and settings destination. */
internal class DanmakuSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow<DanmakuSettings?>(null)
    val settings = state.asStateFlow()
    private var preferences: android.content.SharedPreferences? = null

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val store = application.getSharedPreferences("kirakira_settings", Context.MODE_PRIVATE)
                val value = DanmakuSettings(
                    enabled = store.getBoolean("danmaku_enabled", true),
                    opacityPercent = store.getInt("danmaku_opacity", 100),
                    fontScalePercent = store.getInt("danmaku_font_scale", 100),
                    areaPercent = store.getInt("danmaku_area", 100),
                    speedTenths = store.getInt("danmaku_speed", 10),
                    showRtl = store.getBoolean("danmaku_rtl", true),
                    showLtr = store.getBoolean("danmaku_ltr", true),
                    showTop = store.getBoolean("danmaku_top", true),
                    showBottom = store.getBoolean("danmaku_bottom", true),
                ).normalized()
                store to value
            }
            preferences = loaded.first
            state.value = loaded.second
        }
    }

    fun setEnabled(enabled: Boolean) {
        state.value?.let { update(it.copy(enabled = enabled)) }
    }

    fun update(value: DanmakuSettings) {
        val store = preferences ?: return
        val next = value.normalized()
        if (next == state.value) return
        store.edit {
            putBoolean("danmaku_enabled", next.enabled)
            putInt("danmaku_opacity", next.opacityPercent)
            putInt("danmaku_font_scale", next.fontScalePercent)
            putInt("danmaku_area", next.areaPercent)
            putInt("danmaku_speed", next.speedTenths)
            putBoolean("danmaku_rtl", next.showRtl)
            putBoolean("danmaku_ltr", next.showLtr)
            putBoolean("danmaku_top", next.showTop)
            putBoolean("danmaku_bottom", next.showBottom)
        }
        state.value = next
    }
}
