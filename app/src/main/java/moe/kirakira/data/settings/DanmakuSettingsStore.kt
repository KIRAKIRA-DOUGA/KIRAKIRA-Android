package moe.kirakira.data.settings

import android.content.Context
import androidx.core.content.edit

internal class DanmakuSettingsStore(context: Context) {
    private val preferences = settingsPreferences(context)

    private val defaults = DanmakuSettings()

    fun read() = DanmakuSettings(
        enabled = preferences.getBoolean("danmaku_enabled", defaults.enabled),
        opacityPercent = preferences.getInt("danmaku_opacity", defaults.opacityPercent),
        fontScalePercent = preferences.getInt("danmaku_font_scale", defaults.fontScalePercent),
        areaPercent = preferences.getInt("danmaku_area", defaults.areaPercent),
        speedTenths = preferences.getInt("danmaku_speed", defaults.speedTenths),
        showRtl = preferences.getBoolean("danmaku_rtl", defaults.showRtl),
        showLtr = preferences.getBoolean("danmaku_ltr", defaults.showLtr),
        showTop = preferences.getBoolean("danmaku_top", defaults.showTop),
        showBottom = preferences.getBoolean("danmaku_bottom", defaults.showBottom),
    ).normalized()

    fun write(settings: DanmakuSettings) {
        preferences.edit {
            putBoolean("danmaku_enabled", settings.enabled)
            putInt("danmaku_opacity", settings.opacityPercent)
            putInt("danmaku_font_scale", settings.fontScalePercent)
            putInt("danmaku_area", settings.areaPercent)
            putInt("danmaku_speed", settings.speedTenths)
            putBoolean("danmaku_rtl", settings.showRtl)
            putBoolean("danmaku_ltr", settings.showLtr)
            putBoolean("danmaku_top", settings.showTop)
            putBoolean("danmaku_bottom", settings.showBottom)
        }
    }
}
