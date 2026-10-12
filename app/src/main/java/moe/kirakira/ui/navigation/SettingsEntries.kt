package moe.kirakira.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import moe.kirakira.AppDependenciesViewModel
import moe.kirakira.data.auth.SessionState
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.feature.auth.AuthRoute
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
                    onNavigateToPlayback = {
                        if (navigation.backStack.lastOrNull() == SettingsRoute) navigation.backStack.add(PlaybackSettingsRoute)
                    },
                    signedIn = accountState.activeProfile != null,
                    accountBusy = accountState.isBusy || accountState.isLoading,
                    onLogout = onLogout,
                    onBack = {
                        if (navigation.backStack.lastOrNull() == SettingsRoute) navigation.backStack.removeLastOrNull()
                    },
                    onNavigateToAbout = {
                        if (navigation.backStack.lastOrNull() == SettingsRoute) navigation.backStack.add(AboutRoute)
                    },
                    onNavigateToAppearance = {
                        if (navigation.backStack.lastOrNull() == SettingsRoute) navigation.backStack.add(AppearanceRoute)
                    },
                    onNavigateToAccount = {
                        if (navigation.backStack.lastOrNull() == SettingsRoute) navigation.backStack.add(AccountSwitchRoute)
                    },
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
                    DisposableEffect(model) {
                        navigation.profileBackGuard = model
                        onDispose { if (navigation.profileBackGuard === model) navigation.profileBackGuard = null }
                    }
                    ProfileEditorPage(model,
                        onBack = { if (navigation.backStack.lastOrNull() == ProfileEditorRoute) navigation.backStack.removeLastOrNull() },
                        onLogin = { navigation.openFrom(ProfileEditorRoute, AuthRoute()) })
                }
            }
        }
        entry<PrivacySettingsRoute> {
            NavigationPage {
                if (settingsRepository != null) {
                    val model = viewModel { PrivacySettingsViewModel(settingsRepository) }
                    DisposableEffect(model) {
                        navigation.privacyBackGuard = model
                        onDispose { if (navigation.privacyBackGuard === model) navigation.privacyBackGuard = null }
                    }
                    PrivacySettingsPage(
                        model = model,
                        onBack = {
                            if (navigation.backStack.lastOrNull() == PrivacySettingsRoute) navigation.backStack.removeLastOrNull()
                        },
                        onLogin = { navigation.openFrom(PrivacySettingsRoute, AuthRoute()) },
                    )
                }
            }
        }
        entry<SecuritySettingsRoute> {
            NavigationPage {
                if (dependencies != null) {
                    val model = viewModel { SecuritySettingsViewModel(dependencies.createSecurityRepository()) }
                    DisposableEffect(model) {
                        navigation.securityBackGuard = model
                        onDispose { if (navigation.securityBackGuard === model) navigation.securityBackGuard = null }
                    }
                    SecuritySettingsPage(
                        model = model,
                        isActive = navigation.backStack.lastOrNull() == SecuritySettingsRoute,
                        predictiveBackEnabled = predictiveBackEnabled,
                        onBack = { if (navigation.backStack.lastOrNull() == SecuritySettingsRoute) navigation.backStack.removeLastOrNull() },
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
                    onBack = { if (navigation.backStack.lastOrNull() == BlockingOverviewRoute) navigation.backStack.removeLastOrNull() },
                    onLogin = { navigation.openFrom(BlockingOverviewRoute, AuthRoute()) },
                    onCategory = { navigation.openFrom(BlockingOverviewRoute, RuleManagementRoute(it)) },
                )
            }
        }
        entry<RuleManagementRoute> { route ->
            NavigationPage {
                if (settingsRepository != null && contentRepository != null) RuleManagementPage(
                    model = viewModel { RuleManagementViewModel(route.category, settingsRepository, contentRepository) },
                    onBack = { if (navigation.backStack.lastOrNull() == route) navigation.backStack.removeLastOrNull() },
                    onLogin = { navigation.openFrom(route, AuthRoute()) },
                )
            }
        }
        entry<InvitationsRoute> {
            NavigationPage {
                if (settingsRepository != null) InvitationsPage(
                    model = viewModel { InvitationsViewModel(settingsRepository) },
                    onBack = { if (navigation.backStack.lastOrNull() == InvitationsRoute) navigation.backStack.removeLastOrNull() },
                    onLogin = { navigation.openFrom(InvitationsRoute, AuthRoute()) },
                )
            }
        }
        entry<AboutRoute> {
            NavigationPage {
                AboutScreen(
                    onNavigateToLicenses = {
                        if (navigation.backStack.lastOrNull() == AboutRoute) navigation.backStack.add(LicensesRoute)
                    },
                    onBack = {
                        if (navigation.backStack.lastOrNull() == AboutRoute) navigation.backStack.removeLastOrNull()
                    },
                )
            }
        }
        entry<DanmakuSettingsRoute> {
            NavigationPage {
                DanmakuSettingsScreen(
                    settings = danmakuSettings,
                    onChange = { danmakuSettingsModel?.update(it) },
                    onBack = { if (navigation.backStack.lastOrNull() == DanmakuSettingsRoute) navigation.backStack.removeLastOrNull() },
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
                    onBack = { if (navigation.backStack.lastOrNull() == PlaybackSettingsRoute) navigation.backStack.removeLastOrNull() },
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
                    onBack = {
                        if (navigation.backStack.lastOrNull() == AppearanceRoute) navigation.backStack.removeLastOrNull()
                    },
                )
            }
        }
        entry<LicensesRoute> {
            NavigationPage {
                LicensesScreen(
                    onBack = {
                        if (navigation.backStack.lastOrNull() == LicensesRoute) navigation.backStack.removeLastOrNull()
                    },
                )
            }
        }
    }
}
