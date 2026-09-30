package moe.kirakira.data.content

internal enum class DanmakuFontSize(val wireValue: String, val previewSize: Int) {
    SMALL("small", 14), MEDIUM("medium", 20), LARGE("large", 28),
}

internal enum class DanmakuMode(val wireValue: String) {
    RTL("rtl"), TOP("top"), BOTTOM("bottom"), LTR("ltr"),
}

internal data class DanmakuStyle(
    val color: Int = 0xFFFFFF,
    val fontSize: DanmakuFontSize = DanmakuFontSize.MEDIUM,
    val mode: DanmakuMode = DanmakuMode.RTL,
    val enableRainbow: Boolean = false,
) {
    val colorHex: String get() = (color and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
}
