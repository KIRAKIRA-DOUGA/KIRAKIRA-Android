package moe.kirakira.core.color

import java.util.Locale

/** Six uppercase RGB digits without a prefix; alpha is ignored. */
internal fun formatRgbHex(rgb: Int): String = String.format(Locale.ROOT, "%06X", rgb and 0xFFFFFF)

/** Strict wire format: exactly six hex digits, with no prefix or whitespace. */
internal fun parseRgbHex(value: String): Int? {
    if (value.length != 6 || value.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return value.toIntOrNull(16)
}

internal fun formatRgbColor(argb: Int): String = "#${formatRgbHex(argb)}"

internal fun parseRgbColor(value: String): Int? =
    parseRgbHex(value.trim().removePrefix("#"))?.or(0xFF000000.toInt())
