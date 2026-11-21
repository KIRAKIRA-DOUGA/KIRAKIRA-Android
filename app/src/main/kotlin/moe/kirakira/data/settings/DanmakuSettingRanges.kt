package moe.kirakira.data.settings

internal data class SteppedSettingRange(val range: IntRange, val step: Int) {
    init {
        require(step > 0 && !range.isEmpty() && (range.last - range.first) % step == 0)
    }

    fun normalize(value: Int): Int = range.first + (value.coerceIn(range) - range.first) / step * step
}

internal object DanmakuSettingRanges {
    val opacity = SteppedSettingRange(10..100, 5)
    val fontScale = SteppedSettingRange(50..200, 5)
    val area = SteppedSettingRange(25..100, 25)
    val speed = SteppedSettingRange(5..20, 1)
}
