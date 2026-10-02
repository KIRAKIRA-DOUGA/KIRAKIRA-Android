package moe.kirakira.data.content

internal fun secondsToMilliseconds(seconds: Double?): Long? = seconds?.takeIf {
    it.isFinite() && it >= 0 && it < Long.MAX_VALUE / 1000.0
}?.let { (it * 1000).toLong() }
