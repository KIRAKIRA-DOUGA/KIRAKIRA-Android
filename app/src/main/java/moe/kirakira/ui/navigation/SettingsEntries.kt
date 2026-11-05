package moe.kirakira.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import moe.kirakira.feature.componenttest.ComponentSamplesScreen
import moe.kirakira.feature.componenttest.ComponentTestScreen
import moe.kirakira.feature.settings.AboutScreen
import moe.kirakira.feature.settings.AppearanceScreen
import moe.kirakira.feature.settings.DanmakuSettingsScreen
import moe.kirakira.feature.settings.DanmakuSettingsViewModel
import moe.kirakira.feature.settings.LicensesScreen
import moe.kirakira.feature.settings.PlaybackSettingsScreen
import moe.kirakira.feature.settings.PlaybackSettingsViewModel
import moe.kirakira.feature.settings.SettingsScreen
import moe.kirakira.feature.settings.SettingsSearchPage
import moe.kirakira.feature.settings.SettingsSearchTargetProvider
import moe.kirakira.feature.settings.isVisible
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
import moe.kirakira.feature.settings.settingsSearchCatalog
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
    val renderSettingsDestination: @Composable (NavKey, NavKey) -> Unit = { currentRoute, destination ->
        when (destination) {
            ProfileEditorRoute -> {
                NavigationPage {
                    if (dependencies != null) {
                        val context = LocalContext.current
                        val model = viewModel {
                            ProfileEditorViewModel(dependencies.createProfileRepository(), context)
                        }
                        RegisterNavigationBackGuard(
                            navigation = navigation,
                            route = currentRoute,
                            canNavigateBack = { model.canNavigateBack },
                            requestBack = model::requestBack,
                        )
                        ProfileEditorPage(
                            model = model,
                            onBack = { navigation.popFrom(currentRoute) },
                            onLogin = { navigation.openFrom(currentRoute, AuthRoute()) },
                        )
                    }
                }
            }
            PrivacySettingsRoute -> {
                NavigationPage {
                    if (settingsRepository != null) {
                        val model = viewModel { PrivacySettingsViewModel(settingsRepository) }
                        RegisterNavigationBackGuard(
                            navigation = navigation,
                            route = currentRoute,
                            canNavigateBack = { model.canNavigateBack },
                            requestBack = model::requestBack,
                        )
                        PrivacySettingsPage(
                            model = model,
                            onBack = { navigation.popFrom(currentRoute) },
                            onLogin = { navigation.openFrom(currentRoute, AuthRoute()) },
                        )
                    }
                }
            }
            SecuritySettingsRoute -> {
                NavigationPage {
                    if (dependencies != null) {
                        val model = viewModel { SecuritySettingsViewModel(dependencies.createSecurityRepository()) }
                        val autofill = LocalAutofillManager.current
                        RegisterNavigationBackGuard(
                            navigation = navigation,
                            route = currentRoute,
                            canNavigateBack = { model.canNavigateBack },
                            requestBack = {
                                autofill?.cancel()
                                model.requestBack()
                            },
                        )
                        SecuritySettingsPage(
                            model = model,
                            isActive = navigation.backStack.lastOrNull() == currentRoute,
                            predictiveBackEnabled = predictiveBackEnabled,
                            onBack = { navigation.popFrom(currentRoute) },
                            onLogin = { email ->
                                navigation.openFrom(currentRoute, AuthRoute(email))
                            },
                        )
                    }
                }
            }
            BlockingOverviewRoute -> {
                NavigationPage {
                    if (settingsRepository != null) BlockingOverviewPage(
                        model = viewModel { BlockingOverviewViewModel(settingsRepository) },
                        onBack = { navigation.popFrom(currentRoute) },
                        onLogin = { navigation.openFrom(currentRoute, AuthRoute()) },
                        onCategory = { navigation.openFrom(currentRoute, RuleManagementRoute(it)) },
                    )
                }
            }
            InvitationsRoute -> {
                NavigationPage {
                    if (settingsRepository != null) InvitationsPage(
                        model = viewModel { InvitationsViewModel(settingsRepository) },
                        onBack = { navigation.popFrom(currentRoute) },
                        onLogin = { navigation.openFrom(currentRoute, AuthRoute()) },
                    )
                }
            }
            AboutRoute -> {
                NavigationPage {
                    AboutScreen(
                        onNavigateToLicenses = { navigation.openFrom(currentRoute, LicensesRoute) },
                        onNavigateToComponentTest = { navigation.openFrom(currentRoute, ComponentTestRoute) },
                        onBack = { navigation.popFrom(currentRoute) },
                    )
                }
            }
            DanmakuSettingsRoute -> {
                NavigationPage {
                    DanmakuSettingsScreen(
                        settings = danmakuSettings,
                        onChange = { danmakuSettingsModel?.update(it) },
                        onBack = { navigation.popFrom(currentRoute) },
                    )
                }
            }
            PlaybackSettingsRoute -> {
                NavigationPage {
                    PlaybackSettingsScreen(
                        settings = playbackSettings,
                        onOutsideAppMiniPlayerChange = { playbackSettingsModel?.setOutsideAppMiniPlayer(it) },
                        onInAppMiniPlayerChange = { playbackSettingsModel?.setInAppMiniPlayer(it) },
                        onAutoplayChange = { playbackSettingsModel?.setAutoplay(it) },
                        onBack = { navigation.popFrom(currentRoute) },
                    )
                }
            }
            AppearanceRoute -> {
                NavigationPage {
                    AppearanceScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        themeColors = themeColors,
                        onThemeColorsChange = onThemeColorsChange,
                        predictiveBackEnabled = predictiveBackEnabled,
                        onPredictiveBackEnabledChange = onPredictiveBackEnabledChange,
                        onBack = { navigation.popFrom(currentRoute) },
                    )
                }
            }
            else -> Unit
        }
    }
    with(scope) {
        entry<SettingsSearchTargetRoute> { route ->
            val target = settingsSearchCatalog.firstOrNull { it.id == route.entryId }
            val visible = target?.isVisible(
                accountState.activeProfile != null || accountState.isLoading,
            ) == true
            LaunchedEffect(visible) {
                if (!visible) navigation.popFrom(route)
            }
            if (target != null && visible) SettingsSearchTargetProvider(
                entry = target,
                ready = when (target.page) {
                    SettingsSearchPage.PLAYBACK -> playbackSettings != null
                    SettingsSearchPage.DANMAKU -> danmakuSettings != null
                    else -> true
                },
            ) {
                val destination = when (target.page) {
                    SettingsSearchPage.PROFILE -> ProfileEditorRoute
                    SettingsSearchPage.PRIVACY -> PrivacySettingsRoute
                    SettingsSearchPage.SECURITY -> SecuritySettingsRoute
                    SettingsSearchPage.BLOCKING -> BlockingOverviewRoute
                    SettingsSearchPage.INVITATIONS -> InvitationsRoute
                    SettingsSearchPage.ABOUT -> AboutRoute
                    SettingsSearchPage.DANMAKU -> DanmakuSettingsRoute
                    SettingsSearchPage.PLAYBACK -> PlaybackSettingsRoute
                    SettingsSearchPage.APPEARANCE -> AppearanceRoute
                    SettingsSearchPage.ACCOUNT, SettingsSearchPage.HOME -> null
                }
                if (destination != null) renderSettingsDestination(route, destination)
            }
        }
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
                    isActive = navigation.backStack.lastOrNull() == SettingsRoute,
                    onSearchDestination = { id ->
                        val entry = settingsSearchCatalog.firstOrNull { it.id == id }
                        if (entry != null && entry.isVisible(accountState.activeProfile != null)) {
                            navigation.openFrom(
                                SettingsRoute,
                                if (entry.page == SettingsSearchPage.ACCOUNT) AccountSwitchRoute else SettingsSearchTargetRoute(id),
                            )
                        }
                    },
                    onBack = { navigation.popFrom(SettingsRoute) },
                    onNavigateToAbout = { navigation.openFrom(SettingsRoute, AboutRoute) },
                    onNavigateToAppearance = { navigation.openFrom(SettingsRoute, AppearanceRoute) },
                    onNavigateToAccount = { navigation.openFrom(SettingsRoute, AccountSwitchRoute) },
                )
            }
        }
        entry<ProfileEditorRoute> { route -> renderSettingsDestination(route, route) }
        entry<PrivacySettingsRoute> { route -> renderSettingsDestination(route, route) }
        entry<SecuritySettingsRoute> { route -> renderSettingsDestination(route, route) }
        entry<BlockingOverviewRoute> { route -> renderSettingsDestination(route, route) }
        entry<RuleManagementRoute> { route ->
            NavigationPage {
                if (settingsRepository != null && contentRepository != null) RuleManagementPage(
                    model = viewModel { RuleManagementViewModel(route.category, settingsRepository, contentRepository) },
                    onBack = { navigation.popFrom(route) },
                    onLogin = { navigation.openFrom(route, AuthRoute()) },
                )
            }
        }
        entry<InvitationsRoute> { route -> renderSettingsDestination(route, route) }
        entry<AboutRoute> { route -> renderSettingsDestination(route, route) }
        entry<ComponentTestRoute> {
            NavigationPage {
                ComponentTestScreen(
                    onBack = { navigation.popFrom(ComponentTestRoute) },
                    onOpenCategory = { navigation.openFrom(ComponentTestRoute, ComponentSamplesRoute(it)) },
                )
            }
        }
        entry<ComponentSamplesRoute> { route ->
            NavigationPage {
                ComponentSamplesScreen(
                    category = route.category,
                    onBack = { navigation.popFrom(route) },
                )
            }
        }
        entry<DanmakuSettingsRoute> { route -> renderSettingsDestination(route, route) }
        entry<PlaybackSettingsRoute> { route -> renderSettingsDestination(route, route) }
        entry<AppearanceRoute> { route -> renderSettingsDestination(route, route) }
        entry<LicensesRoute> {
            NavigationPage {
                LicensesScreen(
                    onBack = { navigation.popFrom(LicensesRoute) },
                )
            }
        }
    }
}
