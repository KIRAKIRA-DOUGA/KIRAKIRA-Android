package moe.kirakira.core.color

import java.util.Locale

internal fun formatRgbColor(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

internal fun parseRgbColor(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return hex.toIntOrNull(16)?.or(0xFF000000.toInt())
}
