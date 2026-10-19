package io.github.nadeemiqbal.numberflow

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats [value] with thousands separators and optional fixed decimal places.
 *
 * Pure helper extracted from the composable so it can be unit-tested directly. Locale-aware
 * formatting (currency symbols, region-specific separators) is out of scope; callers wanting
 * locale-aware output should provide their own `format` lambda.
 */
internal fun formatWithThousands(
    value: Double,
    grouping: Char = ',',
    decimalSeparator: Char = '.',
    decimalPlaces: Int = 0,
): String {
    require(decimalPlaces in 0..12) {
        "decimalPlaces must be in 0..12, was $decimalPlaces"
    }
    val isNegative = value < 0
    val absolute = abs(value)
    val scale = pow10(decimalPlaces)
    val scaled = (absolute * scale).roundToLong()
    val wholePart = scaled / scale
    val fractionalPart = scaled - wholePart * scale

    val wholeStr = groupThousands(wholePart.toString(), grouping)
    val sign = if (isNegative && scaled != 0L) "-" else ""
    return if (decimalPlaces == 0) {
        "$sign$wholeStr"
    } else {
        val fracStr = fractionalPart.toString().padStart(decimalPlaces, '0')
        "$sign$wholeStr$decimalSeparator$fracStr"
    }
}

/**
 * Inserts [grouping] every three digits from the right of [digits] (which must consist of digits
 * only — no sign, no decimal). Pure string operation, no allocations beyond the result.
 */
internal fun groupThousands(digits: String, grouping: Char = ','): String {
    if (digits.length <= 3) return digits
    val sb = StringBuilder(digits.length + (digits.length - 1) / 3)
    val firstChunk = digits.length % 3
    var index = 0
    if (firstChunk > 0) {
        sb.append(digits, 0, firstChunk)
        index = firstChunk
    }
    while (index < digits.length) {
        if (sb.isNotEmpty()) sb.append(grouping)
        sb.append(digits, index, index + 3)
        index += 3
    }
    return sb.toString()
}

private fun pow10(n: Int): Long {
    var result = 1L
    repeat(n) { result *= 10L }
    return result
}
