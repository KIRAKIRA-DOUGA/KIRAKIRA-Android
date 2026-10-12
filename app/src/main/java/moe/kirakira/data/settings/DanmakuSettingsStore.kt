package moe.kirakira.data.settings

import android.content.Context
import androidx.core.content.edit

internal class DanmakuSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("kirakira_settings", Context.MODE_PRIVATE)

    fun read() = DanmakuSettings(
        enabled = preferences.getBoolean("danmaku_enabled", true),
        opacityPercent = preferences.getInt("danmaku_opacity", 100),
        fontScalePercent = preferences.getInt("danmaku_font_scale", 100),
        areaPercent = preferences.getInt("danmaku_area", 100),
        speedTenths = preferences.getInt("danmaku_speed", 10),
        showRtl = preferences.getBoolean("danmaku_rtl", true),
        showLtr = preferences.getBoolean("danmaku_ltr", true),
        showTop = preferences.getBoolean("danmaku_top", true),
        showBottom = preferences.getBoolean("danmaku_bottom", true),
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
