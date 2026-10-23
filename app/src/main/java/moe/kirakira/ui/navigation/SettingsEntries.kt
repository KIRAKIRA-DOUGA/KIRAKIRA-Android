package moe.kirakira.ui.navigation

import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import moe.kirakira.AppDependenciesViewModel
import moe.kirakira.data.auth.SessionState
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.feature.auth.AuthRoute
import moe.kirakira.feature.componenttest.ComponentTestScreen
import moe.kirakira.feature.settings.AboutScreen
import moe.kirakira.feature.settings.AppearanceScreen
import moe.kirakira.feature.settings.DanmakuSettingsScreen
import moe.kirakira.feature.settings.DanmakuSettingsViewModel
import moe.kirakira.feature.settings.LicensesScreen
import moe.kirakira.feature.settings.PlaybackSettingsScreen
import moe.kirakira.feature.settings.PlaybackSettingsViewModel
import moe.kirakira.feature.settings.SettingsScreen
import moe.kirakira.feature.settings.management.BlockingOverviewPage
import moe.kirakira.feature.settings.management.BlockingOverviewViewModel
import moe.kirakira.feature.settings.management.InvitationsPage
import moe.kirakira.feature.settings.management.InvitationsViewModel
import moe.kirakira.feature.settings.management.RuleManagementPage
import moe.kirakira.feature.settings.management.RuleManagementViewModel
import moe.kirakira.feature.settings.privacy.PrivacySettingsPage
import moe.kirakira.feature.settings.privacy.PrivacySettingsViewModel
import moe.kirakira.feature.settings.profile.ProfileEditorPage
import moe.kirakira.feature.settings.profile.ProfileEditorViewModel
import moe.kirakira.feature.settings.security.SecuritySettingsPage
import moe.kirakira.feature.settings.security.SecuritySettingsViewModel
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemeMode

internal fun registerSettingsEntries(
    scope: EntryProviderScope<NavKey>,
    navigation: AppNavigationState,
    dependencies: AppDependenciesViewModel?,
    accountState: SessionState,
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeColors: ThemeColorSettings,
    onThemeColorsChange: (ThemeColorSettings) -> Unit,
    predictiveBackEnabled: Boolean,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    playbackSettingsModel: PlaybackSettingsViewModel?,
    playbackSettings: PlaybackSettings?,
    danmakuSettingsModel: DanmakuSettingsViewModel?,
    danmakuSettings: DanmakuSettings?,
) {
    val contentRepository = dependencies?.content
    val settingsRepository = dependencies?.settings
    with(scope) {
        entry<SettingsRoute> {
            NavigationPage {
                SettingsScreen(
                    onNavigateToProfile = { navigation.openFrom(SettingsRoute, ProfileEditorRoute) },
                    onNavigateToPrivacy = { navigation.openFrom(SettingsRoute, PrivacySettingsRoute) },
                    onNavigateToSecurity = { navigation.openFrom(SettingsRoute,
                        if (accountState.activeProfile == null) AuthRoute() else SecuritySettingsRoute) },
                    onNavigateToBlocking = { navigation.openFrom(SettingsRoute, BlockingOverviewRoute) },
                    onNavigateToInvitations = { navigation.openFrom(SettingsRoute, InvitationsRoute) },
                    onNavigateToDanmaku = { navigation.openFrom(SettingsRoute, DanmakuSettingsRoute) },
                    onNavigateToPlayback = { navigation.openFrom(SettingsRoute, PlaybackSettingsRoute) },
                    signedIn = accountState.activeProfile != null,
                    accountBusy = accountState.isBusy || accountState.isLoading,
                    onLogout = onLogout,
                    onBack = { navigation.popFrom(SettingsRoute) },
                    onNavigateToAbout = { navigation.openFrom(SettingsRoute, AboutRoute) },
                    onNavigateToAppearance = { navigation.openFrom(SettingsRoute, AppearanceRoute) },
                    onNavigateToAccount = { navigation.openFrom(SettingsRoute, AccountSwitchRoute) },
                )
            }
        }
        entry<ProfileEditorRoute> {
            NavigationPage {
                if (dependencies != null) {
                    val context = LocalContext.current
                    val model = viewModel {
                        ProfileEditorViewModel(dependencies.createProfileRepository(), context)
                    }
                    RegisterNavigationBackGuard(
                        navigation = navigation,
                        route = ProfileEditorRoute,
                        canNavigateBack = { model.canNavigateBack },
                        requestBack = model::requestBack,
                    )
                    ProfileEditorPage(model,
                        onBack = { navigation.popFrom(ProfileEditorRoute) },
                        onLogin = { navigation.openFrom(ProfileEditorRoute, AuthRoute()) })
                }
            }
        }
        entry<PrivacySettingsRoute> {
            NavigationPage {
                if (settingsRepository != null) {
                    val model = viewModel { PrivacySettingsViewModel(settingsRepository) }
                    RegisterNavigationBackGuard(
                        navigation = navigation,
                        route = PrivacySettingsRoute,
                        canNavigateBack = { model.canNavigateBack },
                        requestBack = model::requestBack,
                    )
                    PrivacySettingsPage(
                        model = model,
                        onBack = { navigation.popFrom(PrivacySettingsRoute) },
                        onLogin = { navigation.openFrom(PrivacySettingsRoute, AuthRoute()) },
                    )
                }
            }
        }
        entry<SecuritySettingsRoute> {
            NavigationPage {
                if (dependencies != null) {
                    val model = viewModel { SecuritySettingsViewModel(dependencies.createSecurityRepository()) }
                    val autofill = LocalAutofillManager.current
                    RegisterNavigationBackGuard(
                        navigation = navigation,
                        route = SecuritySettingsRoute,
                        canNavigateBack = { model.canNavigateBack },
                        requestBack = {
                            autofill?.cancel()
                            model.requestBack()
                        },
                    )
                    SecuritySettingsPage(
                        model = model,
                        isActive = navigation.backStack.lastOrNull() == SecuritySettingsRoute,
                        predictiveBackEnabled = predictiveBackEnabled,
                        onBack = { navigation.popFrom(SecuritySettingsRoute) },
                        onLogin = { email ->
                            navigation.openFrom(SecuritySettingsRoute, AuthRoute(email))
                        },
                    )
                }
            }
        }
        entry<BlockingOverviewRoute> {
            NavigationPage {
                if (settingsRepository != null) BlockingOverviewPage(
                    model = viewModel { BlockingOverviewViewModel(settingsRepository) },
                    onBack = { navigation.popFrom(BlockingOverviewRoute) },
                    onLogin = { navigation.openFrom(BlockingOverviewRoute, AuthRoute()) },
                    onCategory = { navigation.openFrom(BlockingOverviewRoute, RuleManagementRoute(it)) },
                )
            }
        }
        entry<RuleManagementRoute> { route ->
            NavigationPage {
                if (settingsRepository != null && contentRepository != null) RuleManagementPage(
                    model = viewModel { RuleManagementViewModel(route.category, settingsRepository, contentRepository) },
                    onBack = { navigation.popFrom(route) },
                    onLogin = { navigation.openFrom(route, AuthRoute()) },
                )
            }
        }
        entry<InvitationsRoute> {
            NavigationPage {
                if (settingsRepository != null) InvitationsPage(
                    model = viewModel { InvitationsViewModel(settingsRepository) },
                    onBack = { navigation.popFrom(InvitationsRoute) },
                    onLogin = { navigation.openFrom(InvitationsRoute, AuthRoute()) },
                )
            }
        }
        entry<AboutRoute> {
            NavigationPage {
                AboutScreen(
                    onNavigateToLicenses = { navigation.openFrom(AboutRoute, LicensesRoute) },
                    onNavigateToComponentTest = { navigation.openFrom(AboutRoute, ComponentTestRoute) },
                    onBack = { navigation.popFrom(AboutRoute) },
                )
            }
        }
        entry<ComponentTestRoute> {
            NavigationPage {
                ComponentTestScreen(onBack = { navigation.popFrom(ComponentTestRoute) })
            }
        }
        entry<DanmakuSettingsRoute> {
            NavigationPage {
                DanmakuSettingsScreen(
                    settings = danmakuSettings,
                    onChange = { danmakuSettingsModel?.update(it) },
                    onBack = { navigation.popFrom(DanmakuSettingsRoute) },
                )
            }
        }
        entry<PlaybackSettingsRoute> {
            NavigationPage {
                PlaybackSettingsScreen(
                    settings = playbackSettings,
                    onOutsideAppMiniPlayerChange = { playbackSettingsModel?.setOutsideAppMiniPlayer(it) },
                    onInAppMiniPlayerChange = { playbackSettingsModel?.setInAppMiniPlayer(it) },
                    onAutoplayChange = { playbackSettingsModel?.setAutoplay(it) },
                    onBack = { navigation.popFrom(PlaybackSettingsRoute) },
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
                    predictiveBackEnabled = predictiveBackEnabled,
                    onPredictiveBackEnabledChange = onPredictiveBackEnabledChange,
                    onBack = { navigation.popFrom(AppearanceRoute) },
                )
            }
        }
        entry<LicensesRoute> {
            NavigationPage {
                LicensesScreen(
                    onBack = { navigation.popFrom(LicensesRoute) },
                )
            }
        }
    }
}
