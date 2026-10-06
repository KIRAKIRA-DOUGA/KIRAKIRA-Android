package moe.kirakira.ui.splash

import android.animation.ValueAnimator
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import moe.kirakira.R

internal data class SplashRevealInfo(
    val foregroundBoundsInWindow: Rect,
    val windowSize: IntSize,
    val backgroundColor: Color,
    val iconColor: Color,
    val darkTheme: Boolean,
)

/** Owns only the launch handoff; no state survives Activity recreation. */
internal class SplashRevealController(
    private val activity: ComponentActivity,
    private var allowAnimation: Boolean,
    private val updateSystemBars: () -> Unit,
) : DefaultLifecycleObserver {
    var revealInfo by mutableStateOf<SplashRevealInfo?>(null)
        private set

    private var provider: SplashScreenViewProvider? = null
    private var handledExit = false

    init {
        activity.lifecycle.addObserver(this)
    }

    fun onSplashExit(splashProvider: SplashScreenViewProvider) {
        if (handledExit || !allowAnimation || !ValueAnimator.areAnimatorsEnabled() ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            handledExit = true
            splashProvider.remove()
            updateSystemBars()
            return
        }
        handledExit = true
        provider = splashProvider

        val icon = splashProvider.iconView
        val root = activity.window.decorView
        if (icon.width <= 0 || icon.height <= 0 || root.width <= 0 || root.height <= 0) {
            finish()
            return
        }

        val position = IntArray(2)
        icon.getLocationInWindow(position)
        var bounds = Rect(0f, 0f, icon.width.toFloat(), icon.height.toFloat())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ also expands plain vector foregrounds before masking them.
            // Match the platform's integer rounding to keep the reveal aligned.
            val viewportScale = 1f / (1f + 2f * AdaptiveIconDrawable.getExtraInsetFraction())
            val centerX = icon.width / 2
            val centerY = icon.height / 2
            val halfWidth = (icon.width / (viewportScale * 2f)).toInt()
            val halfHeight = (icon.height / (viewportScale * 2f)).toInt()
            bounds = Rect(
                (centerX - halfWidth).toFloat(),
                (centerY - halfHeight).toFloat(),
                (centerX + halfWidth).toFloat(),
                (centerY + halfHeight).toFloat(),
            )
        }
        revealInfo = SplashRevealInfo(
            foregroundBoundsInWindow = bounds.translate(position[0].toFloat(), position[1].toFloat()),
            windowSize = IntSize(root.width, root.height),
            backgroundColor = Color(ContextCompat.getColor(activity, R.color.splash_background)),
            iconColor = Color(ContextCompat.getColor(activity, R.color.splash_icon)),
            darkTheme = !activity.resources.getBoolean(R.bool.splash_light_system_bars),
        )
        updateSystemBars()
    }

    fun onOverlayDrawn() {
        if (revealInfo == null) return
        removeSystemSplash()
        // API 31/32's provider.remove() reapplies XML system bar appearance.
        updateSystemBars()
    }

    fun finish() {
        val hadSplash = revealInfo != null || provider != null
        revealInfo = null
        removeSystemSplash()
        if (hadSplash) updateSystemBars()
    }

    private fun removeSystemSplash() {
        val current = provider
        provider = null
        current?.remove()
    }

    override fun onStop(owner: LifecycleOwner) {
        allowAnimation = false
        finish()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        finish()
        owner.lifecycle.removeObserver(this)
    }
}
