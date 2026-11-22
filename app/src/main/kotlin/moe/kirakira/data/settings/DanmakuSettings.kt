package moe.kirakira.data.settings

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
        opacityPercent = DanmakuSettingRanges.opacity.normalize(opacityPercent),
        fontScalePercent = DanmakuSettingRanges.fontScale.normalize(fontScalePercent),
        areaPercent = DanmakuSettingRanges.area.normalize(areaPercent),
        speedTenths = DanmakuSettingRanges.speed.normalize(speedTenths),
    )
}
