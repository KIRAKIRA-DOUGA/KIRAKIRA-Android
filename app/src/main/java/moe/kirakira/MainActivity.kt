package moe.kirakira

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.kirakira.ui.splash.SplashReveal
import moe.kirakira.ui.splash.SplashRevealController
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeMode
import moe.kirakira.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {
    private val themeViewModel by lazy {
        ViewModelProvider(this)[ThemeViewModel::class.java]
    }
    private lateinit var splashRevealController: SplashRevealController
    private var appContentReady = false
    private var appDarkTheme = false
    private var videoFullscreen = false
    private var videoPageActive = false
    private var imageViewerActive = false

    // AndroidX retains the first styles for configuration changes; their detectors must read current state.
    private val statusBarStyle = SystemBarStyle.auto(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
    ) {
        splashRevealController.revealInfo?.darkTheme ?: (appDarkTheme || videoPageActive || imageViewerActive)
    }
    private val navigationBarStyle = SystemBarStyle.auto(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
    ) {
        splashRevealController.revealInfo?.darkTheme ?: (appDarkTheme || imageViewerActive || videoFullscreen)
    }
    private val restoreSystemBarsAfterConfigurationChange = Runnable { applySystemBars() }

    var pictureInPicture by mutableStateOf(false)
        private set
    private var canEnterPictureInPicture = false
    private var autoEnterPictureInPicture = false
    private var orientationBeforeFullscreen: Int? = null
    private var pipParams = PictureInPictureParams.Builder().build()

    fun updatePictureInPicture(eligible: Boolean, bounds: Rect?, aspectRatio: Float = 16f / 9f, autoEnter: Boolean = true) {
        canEnterPictureInPicture = eligible && packageManager.hasSystemFeature("android.software.picture_in_picture")
        autoEnterPictureInPicture = canEnterPictureInPicture && autoEnter
        val ratio = aspectRatio.takeIf { it.isFinite() }?.coerceIn(1f / 2.39f, 2.39f) ?: 16f / 9f
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational((ratio * 10000).toInt(), 10000))
        if (bounds != null && !bounds.isEmpty) builder.setSourceRectHint(bounds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnterPictureInPicture)
        pipParams = builder.build()
        if (packageManager.hasSystemFeature("android.software.picture_in_picture")) setPictureInPictureParams(pipParams)
    }

    fun enterVideoPictureInPicture(): Boolean = canEnterPictureInPicture &&
        runCatching { enterPictureInPictureMode(pipParams) }.getOrDefault(false)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && autoEnterPictureInPicture) enterVideoPictureInPicture()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPicture = isInPictureInPictureMode
    }

    fun setVideoFullscreen(fullscreen: Boolean) {
        videoFullscreen = fullscreen
        if (fullscreen) {
            if (orientationBeforeFullscreen == null) orientationBeforeFullscreen = requestedOrientation
            if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            orientationBeforeFullscreen?.let { requestedOrientation = it }
            orientationBeforeFullscreen = null
        }
        applySystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
        // AndroidX reapplies edge-to-edge from a child View after the Activity configuration callback.
        window.decorView.apply {
            removeCallbacks(restoreSystemBarsAfterConfigurationChange)
            post(restoreSystemBarsAfterConfigurationChange)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::splashRevealController.isInitialized) applySystemBars()
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(restoreSystemBarsAfterConfigurationChange)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Theme loading can finish before Compose applies and lays out the first page.
        // Keep the system splash through that gap, including when animations are disabled.
        splashScreen.setKeepOnScreenCondition { !appContentReady }
        appDarkTheme = !resources.getBoolean(R.bool.splash_light_system_bars)
        splashRevealController = SplashRevealController(
            activity = this,
            allowAnimation = savedInstanceState == null,
            updateSystemBars = ::applySystemBars,
        )
        splashScreen.setOnExitAnimationListener(splashRevealController::onSplashExit)
        applySystemBars()
        setContent {
            val isReady by themeViewModel.isReady.collectAsStateWithLifecycle()
            if (!isReady) return@setContent
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val themeColors by themeViewModel.themeColors.collectAsStateWithLifecycle()
            val predictiveBackEnabled by themeViewModel.predictiveBackEnabled.collectAsStateWithLifecycle()
            val revealInfo = splashRevealController.revealInfo
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            KIRAKIRATheme(
                darkTheme = darkTheme,
                dynamicColor = themeColors.useSystemColors,
                seedColor = Color(themeColors.seedColorArgb),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { appContentReady = true },
                ) {
                    KIRAKIRAApp(
                        themeMode = themeMode,
                        onThemeModeChange = themeViewModel::setThemeMode,
                        themeColors = themeColors,
                        onThemeColorsChange = themeViewModel::setThemeColors,
                        predictiveBackEnabled = predictiveBackEnabled,
                        onPredictiveBackEnabledChange = themeViewModel::setPredictiveBackEnabled,
                        modifier = if (revealInfo != null) Modifier.clearAndSetSemantics { } else Modifier,
                        onVideoPageActiveChange = { active ->
                            // Keep the latest page style while the launch overlay owns the bars.
                            appDarkTheme = darkTheme
                            videoPageActive = active
                            applySystemBars()
                        },
                        onImageViewerActiveChange = { active ->
                            appDarkTheme = darkTheme
                            imageViewerActive = active
                            applySystemBars()
                        },
                    )
                    if (revealInfo != null) {
                        SplashReveal(
                            info = revealInfo,
                            onOverlayDrawn = splashRevealController::onOverlayDrawn,
                            onFinished = splashRevealController::finish,
                        )
                    }
                }
            }
        }
    }

    private fun applySystemBars() {
        if (!::splashRevealController.isInitialized) return
        enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle,
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = if (videoFullscreen) {
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
            if (videoFullscreen) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}
