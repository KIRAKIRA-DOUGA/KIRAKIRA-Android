package moe.kirakira.ui.navigation

import android.view.animation.AnimationUtils
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal const val ACTIVITY_TRANSITION_MILLIS = 450
internal const val ACTIVITY_OFFSET_DP = 96

internal class NavigationMotion(
    val forward: ContentTransform,
    val backward: ContentTransform,
)

/**
 * Compose adaptation of AOSP's activity_open_* and activity_close_* animations.
 * Distances and timings come from the platform; see third_party/android-motion/README.md.
 * Predictive back is handled by the separate AOSP phase controller in ActivityNavDisplay.
 */
@Composable
internal fun rememberNavigationMotion(): NavigationMotion {
    val context = LocalContext.current
    val distance = with(LocalDensity.current) { ACTIVITY_OFFSET_DP.dp.roundToPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1

    return remember(context, distance, direction) {
        val interpolator = AnimationUtils.loadInterpolator(
            context,
            android.R.interpolator.fast_out_extra_slow_in,
        )
        val offset = distance * direction
        val slideSpec = tween<IntOffset>(
            durationMillis = ACTIVITY_TRANSITION_MILLIS,
            easing = Easing { interpolator.getInterpolation(it) },
        )

        NavigationMotion(
            forward = (
                slideInHorizontally(slideSpec, initialOffsetX = { offset }) +
                    fadeIn(tween(durationMillis = 83, delayMillis = 50, easing = LinearEasing))
                ) togetherWith slideOutHorizontally(slideSpec, targetOffsetX = { -offset }),
            backward = slideInHorizontally(slideSpec, initialOffsetX = { -offset }) togetherWith (
                slideOutHorizontally(slideSpec, targetOffsetX = { offset }) +
                    fadeOut(tween(durationMillis = 83, delayMillis = 35, easing = LinearEasing))
                ),
        )
    }
}

internal val LocalNavigationPageTransform = compositionLocalOf { NavigationPageTransform() }

internal data class NavigationPageTransform(
    val scale: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val alpha: Float = 1f,
    val cornerRadius: Float = 0f,
)

/** Only content is transformed; ActivityNavDisplay draws the scrim in viewport coordinates. */
@Composable
internal fun NavigationPage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transform = LocalNavigationPageTransform.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = transform.scale
                scaleY = transform.scale
                translationX = transform.x
                translationY = transform.y
                alpha = transform.alpha
                shape = RoundedCornerShape(transform.cornerRadius)
                clip = transform.cornerRadius > 0f
            },
    ) {
        content()
    }
}
