package moe.kirakira.ui.components

import android.os.Build
import android.view.RoundedCorner
import android.view.View

private const val DEFAULT_WINDOW_CORNER_RADIUS_DP = 28f

/** Returns the smallest positive screen corner radius in pixels, falling back to 28dp. */
internal fun View.windowCornerRadius(): Float {
    val fallbackRadius = DEFAULT_WINDOW_CORNER_RADIUS_DP * resources.displayMetrics.density
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallbackRadius
    val insets = rootWindowInsets ?: return fallbackRadius
    return listOf(
        RoundedCorner.POSITION_TOP_LEFT,
        RoundedCorner.POSITION_TOP_RIGHT,
        RoundedCorner.POSITION_BOTTOM_LEFT,
        RoundedCorner.POSITION_BOTTOM_RIGHT,
    ).mapNotNull { insets.getRoundedCorner(it)?.radius?.takeIf { radius -> radius > 0 } }
        .minOrNull()?.toFloat() ?: fallbackRadius
}
