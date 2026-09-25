package moe.kirakira.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import moe.kirakira.feature.account.AccountSwitchPage
import moe.kirakira.feature.account.DemoAccountState
import moe.kirakira.feature.main.MainScreen
import moe.kirakira.feature.settings.AboutScreen
import moe.kirakira.feature.settings.AppearanceScreen
import moe.kirakira.feature.settings.LicensesScreen
import moe.kirakira.feature.settings.SettingsScreen
import moe.kirakira.feature.test.TestScreen
import moe.kirakira.feature.video.VideoPage
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemeMode

@Composable
internal fun AppNavHost(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeColors: ThemeColorSettings,
    onThemeColorsChange: (ThemeColorSettings) -> Unit,
    accountState: DemoAccountState,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    shadowsEnabled: Boolean = false,
    onShadowsEnabledChange: (Boolean) -> Unit = {},
    onVideoPageActiveChange: (Boolean) -> Unit = {},
) {
    val backStack = rememberNavBackStack(MainRoute)
    val videoPageActive = backStack.lastOrNull() == VideoRoute
    SideEffect {
        onVideoPageActiveChange(videoPageActive)
    }

    // Keep the host opaque under translated pages and the predictive back preview.
    ActivityNavDisplay(
        backStack = backStack,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        onBack = {
            if (backStack.size > 1) backStack.removeLastOrNull()
        },
        entryProvider = entryProvider {
            entry<TestRoute> {
                NavigationPage {
                    TestScreen()
                }
            }
            entry<MainRoute> {
                NavigationPage {
                    MainScreen(
                        onOpenSettings = {
                            if (backStack.lastOrNull() == MainRoute) backStack.add(SettingsRoute)
                        },
                        onOpenVideo = {
                            if (backStack.lastOrNull() == MainRoute) backStack.add(VideoRoute)
                        },
                    )
                }
            }
            entry<VideoRoute> {
                NavigationPage {
                    VideoPage(
                        onBack = {
                            if (backStack.lastOrNull() == VideoRoute) backStack.removeLastOrNull()
                        },
                    )
                }
            }
            entry<SettingsRoute> {
                NavigationPage {
                    SettingsScreen(
                        onBack = {
                            if (backStack.lastOrNull() == SettingsRoute) backStack.removeLastOrNull()
                        },
                        onNavigateToAbout = {
                            if (backStack.lastOrNull() == SettingsRoute) backStack.add(AboutRoute)
                        },
                        onNavigateToAppearance = {
                            if (backStack.lastOrNull() == SettingsRoute) backStack.add(AppearanceRoute)
                        },
                        onNavigateToAccount = {
                            if (backStack.lastOrNull() == SettingsRoute) backStack.add(AccountSwitchRoute)
                        },
                    )
                }
            }
            entry<AccountSwitchRoute> {
                NavigationPage {
                    AccountSwitchPage(
                        state = accountState,
                        onSelectAccount = onSelectAccount,
                        onRemoveAccount = onRemoveAccount,
                        onBack = {
                            if (backStack.lastOrNull() == AccountSwitchRoute) backStack.removeLastOrNull()
                        },
                    )
                }
            }
            entry<AboutRoute> {
                NavigationPage {
                    AboutScreen(
                        onNavigateToLicenses = {
                            if (backStack.lastOrNull() == AboutRoute) backStack.add(LicensesRoute)
                        },
                        onNavigateToTest = {
                            if (backStack.lastOrNull() == AboutRoute) backStack.add(TestRoute)
                        },
                        onBack = {
                            if (backStack.lastOrNull() == AboutRoute) backStack.removeLastOrNull()
                        },
                    )
                }
            }
            entry<AppearanceRoute> {
                NavigationPage {
                    AppearanceScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        themeColors = themeColors,
                        onThemeColorsChange = onThemeColorsChange,
                        shadowsEnabled = shadowsEnabled,
                        onShadowsEnabledChange = onShadowsEnabledChange,
                        onBack = {
                            if (backStack.lastOrNull() == AppearanceRoute) backStack.removeLastOrNull()
                        },
                    )
                }
            }
            entry<LicensesRoute> {
                NavigationPage {
                    LicensesScreen(
                        onBack = {
                            if (backStack.lastOrNull() == LicensesRoute) backStack.removeLastOrNull()
                        },
                    )
                }
            }
        },
    )
}
