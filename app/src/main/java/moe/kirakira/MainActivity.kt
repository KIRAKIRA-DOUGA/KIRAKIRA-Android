package moe.kirakira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
    private var appDarkTheme = false
    private var videoPageActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !themeViewModel.isReady.value }
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
            val shadowsEnabled by themeViewModel.shadowsEnabled.collectAsStateWithLifecycle()
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
                colorAlgorithm = themeColors.algorithm,
                shadowsEnabled = shadowsEnabled,
            ) {
                Box(Modifier.fillMaxSize()) {
                    KIRAKIRAApp(
                        themeMode = themeMode,
                        onThemeModeChange = themeViewModel::setThemeMode,
                        themeColors = themeColors,
                        onThemeColorsChange = themeViewModel::setThemeColors,
                        shadowsEnabled = shadowsEnabled,
                        onShadowsEnabledChange = themeViewModel::setShadowsEnabled,
                        modifier = if (revealInfo != null) Modifier.clearAndSetSemantics { } else Modifier,
                        onVideoPageActiveChange = { active ->
                            // Keep the latest page style while the launch overlay owns the bars.
                            appDarkTheme = darkTheme
                            videoPageActive = active
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
        val splash = splashRevealController.revealInfo
        val darkBars = splash?.darkTheme ?: appDarkTheme
        enableEdgeToEdge(
            statusBarStyle = if (splash == null && videoPageActive) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { darkBars }
            },
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ) { darkBars },
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}
