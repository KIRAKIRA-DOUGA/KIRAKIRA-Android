package moe.kirakira.core.color

import java.util.Locale

internal enum class ColorValueFormat {
    HEX,
    RGB,
    OKLCH,
}

internal data class ParsedColorValue(val argb: Int, val gamutMapped: Boolean = false)

internal fun formatColorValue(argb: Int, format: ColorValueFormat): String = when (format) {
    ColorValueFormat.HEX -> formatRgbColor(argb)
    ColorValueFormat.RGB -> "rgb(${argb shr 16 and 255} ${argb shr 8 and 255} ${argb and 255})"
    ColorValueFormat.OKLCH -> {
        val color = argbToOklch(argb)
        String.format(Locale.ROOT, "oklch(%.6f%% %.6f %.6f)", color.lightness * 100, color.chroma, color.hue)
    }
}

/** Accepts opaque HEX, numeric RGB (comma or space separated), and numeric/percent OKLCH lightness. */
internal fun parseColorValue(value: String, format: ColorValueFormat): ParsedColorValue? = when (format) {
    ColorValueFormat.HEX -> parseRgbColor(value)?.let { ParsedColorValue(it) }
    ColorValueFormat.RGB -> {
        val parts = colorComponents(value, "rgb")
        val channels = parts?.map { it.toIntOrNull()?.takeIf { channel -> channel in 0..255 } }
        if (channels == null || channels.any { it == null }) null else {
            val (red, green, blue) = channels.filterNotNull()
            ParsedColorValue(0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue)
        }
    }
    ColorValueFormat.OKLCH -> {
        val parts = colorComponents(value, "oklch")
        if (parts == null) null else {
            val lightness = parts[0].removeSuffix("%").toDoubleOrNull()
                ?.div(if (parts[0].endsWith('%')) 100.0 else 1.0)
            val chroma = parts[1].toDoubleOrNull()
            val hue = parts[2].removeSuffix("deg").toDoubleOrNull()
            if (lightness == null || lightness !in 0.0..1.0 ||
                chroma == null || chroma !in 0.0..0.4 || hue == null || !hue.isFinite()
            ) null else {
                oklchToArgb(OklchColor(lightness, chroma, ((hue % 360) + 360) % 360))
            }
        }
    }
}

private fun colorComponents(value: String, function: String): List<String>? {
    var text = value.trim().lowercase(Locale.ROOT)
    if (text.startsWith("$function(")) {
        if (!text.endsWith(')')) return null
        text = text.substring(function.length + 1, text.length - 1).trim()
    }
    val parts = if (',' in text) text.split(',').map(String::trim) else text.split(Regex("\\s+"))
    return parts.takeIf { it.size == 3 && it.none(String::isEmpty) }
}
