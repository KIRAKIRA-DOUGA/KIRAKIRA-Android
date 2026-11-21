package moe.kirakira.ui.components

import android.os.Build
import android.view.RoundedCorner
import android.view.View

private const val DEFAULT_WINDOW_CORNER_RADIUS_DP = 28f

internal data class WindowCornerRadii(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f,
)

/** Returns each physical corner radius in pixels where the window meets the display corners. */
internal fun View.windowCornerRadii(): WindowCornerRadii {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return WindowCornerRadii()
    val insets = rootWindowInsets ?: return WindowCornerRadii()
    return WindowCornerRadii(
        topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f,
        topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius?.toFloat() ?: 0f,
        bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius?.toFloat() ?: 0f,
        bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.toFloat() ?: 0f,
    )
}

/** Returns the smallest positive screen corner radius in pixels, falling back to 28dp. */
internal fun View.windowCornerRadius(): Float {
    val fallbackRadius = DEFAULT_WINDOW_CORNER_RADIUS_DP * resources.displayMetrics.density
    val corners = windowCornerRadii()
    return listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
        .filter { it > 0f }.minOrNull() ?: fallbackRadius
}
