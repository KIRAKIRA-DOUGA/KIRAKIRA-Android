package io.github.nadeemiqbal.numberflow

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/** Default values and ready-made `format` helpers for [NumberFlow]. */
object NumberFlowDefaults {

    /** The default per-digit slide animation — a 450 ms ease curve. */
    val AnimationSpec: FiniteAnimationSpec<IntOffset> = tween(durationMillis = 450)

    /** Default `(Double) -> String` format — `value.toString()` with trailing ".0" stripped from integers. */
    val PlainDouble: (Double) -> String = { v ->
        val whole = v.toLong()
        if (whole.toDouble() == v) whole.toString() else v.toString()
    }

    /** Default `(Long) -> String` format — `value.toString()`. */
    val PlainLong: (Long) -> String = { it.toString() }

    /**
     * `(Double) -> String` format that adds thousands separators and renders to a fixed number
     * of decimal places, e.g. `1234567.5` → `"1,234,567.50"` with `decimalPlaces = 2`.
     */
    fun thousands(
        grouping: Char = ',',
        decimalSeparator: Char = '.',
        decimalPlaces: Int = 0,
    ): (Double) -> String = { value ->
        formatWithThousands(value, grouping, decimalSeparator, decimalPlaces)
    }

    /**
     * `(Long) -> String` format that adds thousands separators — e.g. `1234567` → `"1,234,567"`.
     */
    fun thousandsLong(grouping: Char = ','): (Long) -> String = { value ->
        val sign = if (value < 0) "-" else ""
        sign + groupThousands(kotlin.math.abs(value).toString(), grouping)
    }
}
