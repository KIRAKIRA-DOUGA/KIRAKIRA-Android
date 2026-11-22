package moe.kirakira.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import moe.kirakira.AppDependenciesViewModel
import moe.kirakira.data.auth.SessionState
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.feature.auth.AuthRoute
import moe.kirakira.feature.gallery.ComponentDetailScreen
import moe.kirakira.feature.gallery.ComponentGalleryScreen
import moe.kirakira.feature.settings.LanguageSettingsScreen
import moe.kirakira.feature.settings.SettingsScreen
import moe.kirakira.feature.settings.about.AboutScreen
import moe.kirakira.feature.settings.about.LicensesScreen
import moe.kirakira.feature.settings.appearance.AppearanceScreen
import moe.kirakira.feature.settings.directoryIndex
import moe.kirakira.feature.settings.management.BlockingOverviewPage
import moe.kirakira.feature.settings.management.BlockingOverviewViewModel
import moe.kirakira.feature.settings.management.InvitationsPage
import moe.kirakira.feature.settings.management.InvitationsViewModel
import moe.kirakira.feature.settings.management.RuleManagementPage
import moe.kirakira.feature.settings.management.RuleManagementViewModel
import moe.kirakira.feature.settings.playback.DanmakuSettingsScreen
import moe.kirakira.feature.settings.playback.DanmakuSettingsViewModel
import moe.kirakira.feature.settings.playback.PlaybackSettingsScreen
import moe.kirakira.feature.settings.playback.PlaybackSettingsViewModel
import moe.kirakira.feature.settings.privacy.PrivacySettingsPage
import moe.kirakira.feature.settings.privacy.PrivacySettingsViewModel
import moe.kirakira.feature.settings.profile.ProfileEditorPage
import moe.kirakira.feature.settings.profile.ProfileEditorViewModel
import moe.kirakira.feature.settings.search.SettingsSearchPage
import moe.kirakira.feature.settings.search.SettingsSearchTargetProvider
import moe.kirakira.feature.settings.search.isVisible
import moe.kirakira.feature.settings.search.settingsSearchCatalog
import moe.kirakira.feature.settings.security.SecuritySettingsPage
import moe.kirakira.feature.settings.security.SecuritySettingsViewModel
import moe.kirakira.feature.settings.security.SecurityStep
import moe.kirakira.ui.components.form.TextFieldEdits
import moe.kirakira.ui.navigation.LocalDestinationActive
import moe.kirakira.ui.navigation.LocalSettingsPane
import moe.kirakira.ui.navigation.NavigationPage
import moe.kirakira.ui.navigation.SettingsCategoryIndexKey
import moe.kirakira.ui.navigation.SettingsPane
import moe.kirakira.ui.navigation.SettingsPaneKey
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemeMode

internal fun settingsCategoryPane(route: NavKey) = metadata {
    put(SettingsPaneKey, SettingsPane.CATEGORY)
    route.settingsCategory?.directoryIndex?.let { put(SettingsCategoryIndexKey, it) }
}

private val settingsSubpagePane = metadata { put(SettingsPaneKey, SettingsPane.SUBPAGE) }

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
    backgroundDepthEnabled: Boolean,
    onBackgroundDepthEnabledChange: (Boolean) -> Unit,
    playbackSettingsModel: PlaybackSettingsViewModel?,
    playbackSettings: PlaybackSettings?,
    danmakuSettingsModel: DanmakuSettingsViewModel?,
    danmakuSettings: DanmakuSettings?,
) {
    val contentRepository = dependencies?.content
    val settingsRepository = dependencies?.settings
    val renderPage: @Composable (NavKey, NavKey) -> Unit = { currentRoute, destination ->
        when (destination) {
            ProfileEditorRoute -> {
                NavigationPage {
                    if (dependencies != null) {
                        val context = LocalContext.current
                        val model = viewModel {
                            ProfileEditorViewModel(dependencies.createProfileRepository(), context)
                        }
                        val state by model.state.collectAsStateWithLifecycle()
                        val edits = remember(model) { TextFieldEdits() }
                        val requestBack = {
                            edits.flush()
                            model.requestBack()
                        }
                        RegisterFullPageSetting(navigation, currentRoute, fullPage = state.cropSource != null)
                        RegisterNavigationBackGuard(
                            navigation = navigation,
                            route = currentRoute,
                            canNavigateBack = { model.canNavigateBack && !edits.hasPendingChanges },
                            requestBack = requestBack,
                            requestSettingsLeave = { model.state.value.cropSource == null && requestBack() },
                            awaitingDiscardConfirmation = { model.state.value.confirmDiscard },
                        )
                        ProfileEditorPage(
                            model = model,
                            edits = edits,
                            onBack = { navigation.popFrom(currentRoute) },
                            onLogin = { navigation.openFrom(currentRoute, AuthRoute()) },
                            onDiscardConfirmed = {
                                navigation.completeSettingsLeave(currentRoute)
                                if (navigation.backStack.lastOrNull() == currentRoute) model.discardDraft()
                            },
                            onDiscardCancelled = { navigation.cancelSettingsLeave(currentRoute) },
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
                        val state by model.state.collectAsStateWithLifecycle()
                        RegisterFullPageSetting(navigation, currentRoute, fullPage = state.step != SecurityStep.OVERVIEW)
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
                    if (settingsRepository != null) {
                        val model = viewModel { InvitationsViewModel(settingsRepository) }
                        val canLeave = { !model.busy.value }
                        RegisterNavigationBackGuard(navigation, currentRoute, canLeave, canLeave)
                        InvitationsPage(
                            model = model,
                            onBack = { if (canLeave()) navigation.popFrom(currentRoute) },
                            onLogin = { navigation.openFrom(currentRoute, AuthRoute()) },
                        )
                    }
                }
            }
            AboutRoute -> {
                NavigationPage {
                    AboutScreen(
                        onNavigateToLicenses = { navigation.openFrom(currentRoute, LicensesRoute) },
                        onNavigateToComponentGallery = { navigation.openFrom(currentRoute, ComponentGalleryRoute) },
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
            LanguageSettingsRoute -> {
                NavigationPage {
                    LanguageSettingsScreen(onBack = { navigation.popFrom(currentRoute) })
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
                        backgroundDepthEnabled = backgroundDepthEnabled,
                        onBackgroundDepthEnabledChange = onBackgroundDepthEnabledChange,
                        onBack = { navigation.popFrom(currentRoute) },
                    )
                }
            }
            else -> Unit
        }
    }
    val renderSettingsDestination: @Composable (NavKey, NavKey) -> Unit = { route, destination ->
        var anchorId by rememberSaveable { mutableStateOf((route as? SettingsSearchTargetRoute)?.entryId) }
        var selection by rememberSaveable { mutableIntStateOf(0) }
        val request = navigation.settingsAnchorRequest?.takeIf { it.route == route }
        LaunchedEffect(request) {
            if (request != null) {
                anchorId = request.entryId
                selection++
                navigation.consumeSettingsAnchor(request)
            }
        }
        val target = settingsSearchCatalog.firstOrNull { it.id == anchorId }
        SettingsSearchTargetProvider(
            entry = target?.takeIf { it.isVisible(accountState.activeProfile != null || accountState.isLoading) },
            ready = when (destination) {
                PlaybackSettingsRoute -> playbackSettings != null
                DanmakuSettingsRoute -> danmakuSettings != null
                else -> true
            },
            selection = selection,
        ) {
            renderPage(route, destination)
        }
    }
    with(scope) {
        entry<SettingsSearchTargetRoute>(metadata = ::settingsCategoryPane) { route ->
            val target = settingsSearchCatalog.firstOrNull { it.id == route.entryId }
            val visible = target?.isVisible(
                accountState.activeProfile != null || accountState.isLoading,
            ) == true
            LaunchedEffect(visible) {
                if (!visible) navigation.popFrom(route)
            }
            if (target != null && visible) renderSettingsDestination(route, target.page.settingsRoute)
        }
        entry<SettingsRoute>(metadata = metadata { put(SettingsPaneKey, SettingsPane.DIRECTORY) }) {
            NavigationPage {
                val twoPane = LocalSettingsPane.current == SettingsPane.DIRECTORY
                val top = navigation.backStack.last()
                val category = if (twoPane) {
                    navigation.backStack.getOrNull(navigation.backStack.indexOf(SettingsRoute) + 1)?.settingsCategory
                } else null
                fun select(destination: NavKey, isSearch: Boolean = false) {
                    if (twoPane) navigation.selectSetting(top, destination, isSearch)
                    else navigation.openFrom(SettingsRoute, destination)
                }
                SettingsScreen(
                    onSelectCategory = { select(it.settingsRoute) },
                    twoPane = twoPane,
                    selectedCategory = category,
                    signedIn = accountState.activeProfile != null,
                    accountBusy = accountState.isBusy || accountState.isLoading,
                    onLogout = {
                        if (twoPane) navigation.leaveSettings(top, afterPathChange = onLogout) else onLogout()
                    },
                    isActive = LocalDestinationActive.current,
                    onSearchDestination = { entry ->
                        if (entry.isVisible(accountState.activeProfile != null)) {
                            val destination = if (entry.page == SettingsSearchPage.ACCOUNT) AccountSwitchRoute
                                else SettingsSearchTargetRoute(entry.id)
                            select(destination, isSearch = true)
                        }
                    },
                    onBack = {
                        if (twoPane) navigation.leaveSettings(top) { navigation.popFrom(SettingsRoute) }
                        else navigation.popFrom(SettingsRoute)
                    },
                )
            }
        }
        entry<ProfileEditorRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<PrivacySettingsRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<SecuritySettingsRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<BlockingOverviewRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<RuleManagementRoute>(metadata = settingsSubpagePane) { route ->
            NavigationPage {
                if (settingsRepository != null && contentRepository != null) {
                    val model = viewModel { RuleManagementViewModel(route.category, settingsRepository, contentRepository) }
                    val canLeave = { !model.busy.value }
                    RegisterNavigationBackGuard(navigation, route, canLeave, canLeave)
                    RuleManagementPage(
                        model = model,
                        onBack = { if (canLeave()) navigation.popFrom(route) },
                        onLogin = { navigation.openFrom(route, AuthRoute()) },
                    )
                }
            }
        }
        entry<InvitationsRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<AboutRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<ComponentGalleryRoute> {
            NavigationPage {
                ComponentGalleryScreen(
                    onBack = { navigation.popFrom(ComponentGalleryRoute) },
                    onOpenComponent = { navigation.openFrom(ComponentGalleryRoute, ComponentDetailRoute(it)) },
                )
            }
        }
        entry<ComponentSamplesRoute> {
            // AppNavHost drops this restored legacy destination.
        }
        entry<ComponentDetailRoute> { route ->
            NavigationPage {
                ComponentDetailScreen(
                    component = route.component,
                    onBack = { navigation.popFrom(route) },
                )
            }
        }
        entry<DanmakuSettingsRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<PlaybackSettingsRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<AppearanceRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<LanguageSettingsRoute>(metadata = ::settingsCategoryPane) { route -> renderSettingsDestination(route, route) }
        entry<LicensesRoute>(metadata = settingsSubpagePane) {
            NavigationPage {
                LicensesScreen(
                    onBack = { navigation.popFrom(LicensesRoute) },
                )
            }
        }
    }
}
