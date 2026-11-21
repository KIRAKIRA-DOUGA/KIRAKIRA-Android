package moe.kirakira.ui.navigation

import android.os.Build
import android.view.animation.AnimationUtils
import androidx.annotation.InterpolatorRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

internal fun activityCloseFadeSpec(): TweenSpec<Float> =
    tween(durationMillis = 83, delayMillis = 35, easing = LinearEasing)

@InterpolatorRes
internal fun activityInterpolatorResource(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        android.R.interpolator.fast_out_extra_slow_in
    } else {
        android.R.interpolator.fast_out_slow_in
    }

internal class NavigationMotion(
    horizontalOffset: Int,
    private val distance: Int,
    easing: Easing,
) {
    private val slideSpec = tween<IntOffset>(durationMillis = ACTIVITY_TRANSITION_MILLIS, easing = easing)
    private val openFade = fadeIn(tween(durationMillis = 83, delayMillis = 50, easing = LinearEasing))
    private val closeFade = fadeOut(activityCloseFadeSpec())

    val pageOffset = horizontalOffset.toFloat()
    val pageSlideSpec = tween<Float>(durationMillis = ACTIVITY_TRANSITION_MILLIS, easing = easing)
    val forward = (slideInHorizontally(slideSpec) { horizontalOffset } + openFade) togetherWith
        slideOutHorizontally(slideSpec) { -horizontalOffset }
    val backward = slideInHorizontally(slideSpec) { -horizontalOffset } togetherWith
        (slideOutHorizontally(slideSpec) { horizontalOffset } + closeFade)

    fun vertical(enterFromBottom: Boolean, isPop: Boolean): ContentTransform {
        val offset = if (enterFromBottom) distance else -distance
        val enter = slideInVertically(slideSpec) { offset }
        val exit = slideOutVertically(slideSpec) { -offset }
        return if (isPop) enter togetherWith (exit + closeFade) else (enter + openFade) togetherWith exit
    }
}

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
            activityInterpolatorResource(),
        )
        NavigationMotion(
            horizontalOffset = distance * direction,
            distance = distance,
            easing = Easing { interpolator.getInterpolation(it) },
        )
    }
}

internal val LocalNavigationPageTransform = compositionLocalOf {
    { NavigationPageTransform() }
}

internal val LocalNavigationPageStartGutter = compositionLocalOf { 0.dp }

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
    val readTransform = LocalNavigationPageTransform.current
    val startGutter = LocalNavigationPageStartGutter.current
    val bodyInset = if (startGutter > 0.dp) {
        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)).padding(start = startGutter)
    } else Modifier
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val transform = readTransform()
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = transform.scale
                scaleY = transform.scale
                translationX = transform.x
                translationY = transform.y
                alpha = transform.alpha
                shape = RoundedCornerShape(transform.cornerRadius)
                clip = transform.cornerRadius > 0f
            }
            .then(bodyInset),
    ) {
        // Only the outer page owns this gutter; nested content must not apply it again.
        CompositionLocalProvider(LocalNavigationPageStartGutter provides 0.dp, content = content)
    }
}
