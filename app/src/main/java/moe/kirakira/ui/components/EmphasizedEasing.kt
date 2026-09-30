package moe.kirakira.ui.components

import android.os.Build
import android.view.animation.AnimationUtils
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.PathEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext

/** Material 3 emphasized easing, with the same path on Android 8.1. */
@Composable
internal fun rememberEmphasizedEasing(): Easing {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val interpolator = AnimationUtils.loadInterpolator(
                context,
                android.R.interpolator.fast_out_extra_slow_in,
            )
            Easing { interpolator.getInterpolation(it) }
        } else {
            PathEasing(
                Path().apply {
                    moveTo(0f, 0f)
                    cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
                    cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
                },
            )
        }
    }
}
