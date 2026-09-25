package moe.kirakira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeMode
import moe.kirakira.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {
    private val themeViewModel by lazy {
        ViewModelProvider(this)[ThemeViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { !themeViewModel.isReady.value }
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val isReady by themeViewModel.isReady.collectAsStateWithLifecycle()
            if (!isReady) return@setContent
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val themeColors by themeViewModel.themeColors.collectAsStateWithLifecycle()
            val shadowsEnabled by themeViewModel.shadowsEnabled.collectAsStateWithLifecycle()
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
                KIRAKIRAApp(
                    themeMode = themeMode,
                    onThemeModeChange = themeViewModel::setThemeMode,
                    themeColors = themeColors,
                    onThemeColorsChange = themeViewModel::setThemeColors,
                    shadowsEnabled = shadowsEnabled,
                    onShadowsEnabledChange = themeViewModel::setShadowsEnabled,
                    onVideoPageActiveChange = { videoPageActive ->
                        // Navigation owns the override so page transitions cannot race theme effects.
                        enableEdgeToEdge(
                            statusBarStyle = if (videoPageActive) {
                                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                            } else {
                                SystemBarStyle.auto(
                                    android.graphics.Color.TRANSPARENT,
                                    android.graphics.Color.TRANSPARENT,
                                ) { darkTheme }
                            },
                            navigationBarStyle = SystemBarStyle.auto(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                            ) { darkTheme },
                        )
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            window.isNavigationBarContrastEnforced = false
                        }
                    },
                )
            }
        }
    }
}
