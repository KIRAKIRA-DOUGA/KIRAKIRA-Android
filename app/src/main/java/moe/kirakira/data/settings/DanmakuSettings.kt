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
        opacityPercent = opacityPercent.coerceIn(10, 100) / 5 * 5,
        fontScalePercent = fontScalePercent.coerceIn(50, 200) / 5 * 5,
        areaPercent = areaPercent.coerceIn(25, 100) / 25 * 25,
        speedTenths = speedTenths.coerceIn(5, 20),
    )
}
