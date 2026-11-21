package moe.kirakira.core.color

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class OklchColor(val lightness: Double, val chroma: Double, val hue: Double)

// Oklab matrices: https://bottosson.github.io/posts/oklab/ (public domain).
internal fun argbToOklch(argb: Int): OklchColor {
    fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    val red = linear(argb shr 16 and 255)
    val green = linear(argb shr 8 and 255)
    val blue = linear(argb and 255)
    val l = Math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue)
    val m = Math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue)
    val s = Math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue)
    val lightness = (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s).coerceIn(0.0, 1.0)
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    val chroma = hypot(a, b)
    val hue = if (chroma < 0.000004) 0.0 else (Math.toDegrees(atan2(b, a)) + 360) % 360
    return OklchColor(lightness, if (chroma < 0.000004) 0.0 else chroma, hue)
}

/** Reduces chroma at fixed lightness/hue to fit sRGB, then quantizes the actual saved color to 8 bits. */
internal fun oklchToArgb(color: OklchColor): ParsedColorValue {
    val original = color.linearRgb(color.chroma)
    val gamutMapped = !original.isInGamut()
    val rgb = if (gamutMapped) {
        var low = 0.0
        var high = color.chroma
        repeat(24) {
            val middle = (low + high) / 2
            if (color.linearRgb(middle).isInGamut()) low = middle else high = middle
        }
        color.linearRgb(low)
    } else original
    fun channel(value: Double): Int {
        val linear = value.coerceIn(0.0, 1.0)
        val encoded = if (linear <= 0.0031308) 12.92 * linear else 1.055 * linear.pow(1 / 2.4) - 0.055
        return (encoded * 255).roundToInt().coerceIn(0, 255)
    }
    return ParsedColorValue(
        argb = 0xFF000000.toInt() or (channel(rgb[0]) shl 16) or (channel(rgb[1]) shl 8) or channel(rgb[2]),
        gamutMapped = gamutMapped,
    )
}

private fun DoubleArray.isInGamut(): Boolean = all { it in -0.000001..1.000001 }

private fun OklchColor.linearRgb(chroma: Double): DoubleArray {
    val angle = Math.toRadians(hue)
    val a = chroma * cos(angle)
    val b = chroma * sin(angle)
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return doubleArrayOf(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )
}
