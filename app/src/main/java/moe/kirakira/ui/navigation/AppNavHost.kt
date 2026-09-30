package moe.kirakira.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import moe.kirakira.feature.settings.PlaybackSettings
import moe.kirakira.feature.settings.DanmakuSettings
import moe.kirakira.feature.settings.DanmakuSettingsScreen
import moe.kirakira.feature.settings.DanmakuSettingsViewModel
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.feature.settings.management.BlockingOverviewPage
import moe.kirakira.feature.settings.management.BlockingOverviewViewModel
import moe.kirakira.feature.settings.management.RuleManagementPage
import moe.kirakira.feature.settings.management.RuleManagementViewModel
import moe.kirakira.feature.settings.management.InvitationsPage
import moe.kirakira.feature.settings.management.InvitationsViewModel
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.auth.SessionState
import moe.kirakira.feature.account.AccountSwitchPage
import moe.kirakira.feature.account.GUEST_ACCOUNT_ID
import moe.kirakira.feature.account.SessionFeedback
import moe.kirakira.feature.auth.AuthPage
import moe.kirakira.feature.auth.AuthRoute
import moe.kirakira.feature.imageviewer.ImageViewerPage
import moe.kirakira.feature.main.MainScreen
import moe.kirakira.feature.profile.ProfilePage
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.feature.main.HomeViewModel
import moe.kirakira.feature.settings.AboutScreen
import moe.kirakira.feature.settings.AppearanceScreen
import moe.kirakira.feature.settings.LicensesScreen
import moe.kirakira.feature.settings.PlaybackSettingsScreen
import moe.kirakira.feature.settings.PlaybackSettingsViewModel
import moe.kirakira.feature.settings.SettingsScreen
import moe.kirakira.feature.video.VideoPage
import moe.kirakira.ui.components.rememberEmphasizedEasing
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemeMode

@Composable
internal fun AppNavHost(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeColors: ThemeColorSettings,
    onThemeColorsChange: (ThemeColorSettings) -> Unit,
    accountState: SessionState,
    authRepository: AuthRepository?,
    onLogout: () -> Unit,
    onResetLocalAccounts: () -> Unit,
    onRetrySession: () -> Unit,
    onDismissSessionError: () -> Unit,
    onSelectAccount: (String?) -> Unit,
    onRemoveAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    shadowsEnabled: Boolean = false,
    onShadowsEnabledChange: (Boolean) -> Unit = {},
    onVideoPageActiveChange: (Boolean) -> Unit = {},
    onImageViewerActiveChange: (Boolean) -> Unit = {},
) {
    val playbackSettingsModel = if (LocalInspectionMode.current) null else viewModel<PlaybackSettingsViewModel>()
    val playbackSettings = if (LocalInspectionMode.current) PlaybackSettings()
        else playbackSettingsModel?.settings?.collectAsStateWithLifecycle()?.value
    val danmakuSettingsModel = if (LocalInspectionMode.current) null else viewModel<DanmakuSettingsViewModel>()
    val danmakuSettings = if (LocalInspectionMode.current) DanmakuSettings()
        else danmakuSettingsModel?.settings?.collectAsStateWithLifecycle()?.value
    val backStack = rememberNavBackStack(MainRoute)
    // Discard retired demo destinations when restoring navigation after an upgrade.
    LaunchedEffect(Unit) {
        backStack.removeAll {
            it is TestRoute || (it is VideoRoute && it.videoId <= 0) || (it is ProfileRoute && it.uid <= 0)
        }
    }
    val autofill = LocalAutofillManager.current
    val imageEasing = rememberEmphasizedEasing()
    val imageMetadata = remember(imageEasing) { imageViewerNavigationMetadata(imageEasing) }
    val apiClient = remember { moe.kirakira.core.network.ApiClient(moe.kirakira.BuildConfig.API_BASE_URL) }
    val contentRepository = remember(authRepository, apiClient) {
        authRepository?.let { ContentRepository(apiClient, it) }
    }
    val settingsRepository = remember(authRepository, apiClient) {
        authRepository?.let { AccountSettingsRepository(apiClient, it) }
    }
    val homeViewModel = contentRepository?.let { viewModel { HomeViewModel(it) } }
    val homeVideos = homeViewModel?.videos?.collectAsStateWithLifecycle()?.value
        ?: moe.kirakira.feature.video.ContentState<List<moe.kirakira.data.content.VideoSummary>>()
    fun openFrom(source: NavKey, target: NavKey) {
        if (backStack.lastOrNull() != source) return
        val existingIndex = backStack.indexOf(target)
        if (existingIndex >= 0) {
            while (backStack.lastIndex > existingIndex) backStack.removeLastOrNull()
        } else {
            backStack.add(target)
        }
    }
    val videoPageActive = backStack.lastOrNull() is VideoRoute
    SideEffect {
        onVideoPageActiveChange(videoPageActive)
    }

    SessionFeedback(
        state = accountState,
        onDismissError = onDismissSessionError,
        onRetry = onRetrySession,
        onResetLocalAccounts = onResetLocalAccounts,
        onLogin = { email ->
            if (backStack.lastOrNull() !is AuthRoute) backStack.add(AuthRoute(email))
        },
    )

    // Keep the host opaque under translated pages and the predictive back preview.
    ActivityNavDisplay(
        backStack = backStack,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        onBack = {
            if (backStack.lastOrNull() is AuthRoute) autofill?.cancel()
            if (backStack.size > 1) backStack.removeLastOrNull()
        },
        entryProvider = entryProvider {
            entry<TestRoute> {
                // Retained only until the restored legacy entry is removed above.
            }
            entry<MainRoute> {
                NavigationPage {
                    MainScreen(
                        profile = accountState.activeProfile,
                        onOpenProfile = {
                            openFrom(MainRoute, if (accountState.activeProfile == null) AuthRoute() else SelfProfileRoute)
                        },
                        onOpenSettings = {
                            if (backStack.lastOrNull() == MainRoute) backStack.add(SettingsRoute)
                        },
                        onOpenVideo = { id ->
                            if (backStack.lastOrNull() == MainRoute) backStack.add(VideoRoute(id))
                        },
                        videos = homeVideos, onRefreshVideos = { homeViewModel?.refresh() },
                    )
                }
            }
            entry<VideoRoute> { route ->
                NavigationPage {
                    if (contentRepository == null || route.videoId <= 0) {
                        Text(stringResource(R.string.content_login_to_interact))
                    } else {
                        VideoPage(danmakuSettings = danmakuSettings, onDanmakuEnabled = { danmakuSettingsModel?.setEnabled(it) }, onQualityPreference = { playbackSettingsModel?.setQuality(it) }, playbackSettings = playbackSettings, videoId = route.videoId, repository = contentRepository, isActive = backStack.lastOrNull() == route,
                            onOpenProfile = { uid -> openFrom(route, ProfileRoute(uid)) }, onLogin = {
                                if (backStack.lastOrNull() == route) backStack.add(AuthRoute())
                            }, onBack = { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() })
                    }
                }
            }
            entry<ProfileRoute> { route ->
                NavigationPage {
                    if (contentRepository == null || route.uid <= 0) Text(stringResource(R.string.content_login_to_interact))
                    else ProfilePage(model = viewModel { moe.kirakira.feature.profile.ProfileViewModel(route.uid, contentRepository) },
                        onOpenVideo = { id -> openFrom(route, VideoRoute(id)) },
                        onOpenImage = { openFrom(route, ImageViewerRoute(it)) }, onLogin = { backStack.add(AuthRoute()) },
                        onBack = { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() })
                }
            }
            entry<SelfProfileRoute> {
                NavigationPage {
                    val account = accountState.activeProfile
                    if (account == null || contentRepository == null) LaunchedEffect(Unit) {
                        if (backStack.lastOrNull() == SelfProfileRoute) backStack.removeLastOrNull()
                    } else ProfilePage(model = viewModel(key = "profile-${account.uid}") { moe.kirakira.feature.profile.ProfileViewModel(account.uid, contentRepository) },
                        onOpenVideo = { id -> openFrom(SelfProfileRoute, VideoRoute(id)) },
                        onOpenImage = { openFrom(SelfProfileRoute, ImageViewerRoute(it)) }, onLogin = {},
                        onBack = { if (backStack.lastOrNull() == SelfProfileRoute) backStack.removeLastOrNull() })
                }
            }
            entry<ImageViewerRoute>(metadata = imageMetadata) { route ->
                // Keep light system-bar icons until the viewer's exit animation is disposed.
                DisposableEffect(route) {
                    onImageViewerActiveChange(true)
                    onDispose { onImageViewerActiveChange(false) }
                }
                val visibility = rememberImageVisibility()
                ImageViewerPage(
                    image = route.image,
                    onBack = {
                        if (backStack.lastOrNull() == route) backStack.removeLastOrNull()
                    },
                    imageModifier = Modifier.imageSharedBounds(route.image.sharedKey, viewer = true),
                    backgroundModifier = Modifier.imageBackgroundOverlay(),
                    controlsModifier = Modifier.imageControlsOverlay(),
                    transitioning = visibility.value != 1f,
                    visibilityProgress = { visibility.value },
                )
            }
            entry<SettingsRoute> {
                NavigationPage {
                    SettingsScreen(
                        onNavigateToBlocking = { openFrom(SettingsRoute, BlockingOverviewRoute) },
                        onNavigateToInvitations = { openFrom(SettingsRoute, InvitationsRoute) },
                        onNavigateToDanmaku = { openFrom(SettingsRoute, DanmakuSettingsRoute) },
                        onNavigateToPlayback = {
                            if (backStack.lastOrNull() == SettingsRoute) backStack.add(PlaybackSettingsRoute)
                        },
                        signedIn = accountState.activeProfile != null,
                        accountBusy = accountState.isBusy || accountState.isLoading,
                        onLogout = onLogout,
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
            entry<BlockingOverviewRoute> {
                NavigationPage {
                    if (settingsRepository != null) BlockingOverviewPage(
                        model = viewModel { BlockingOverviewViewModel(settingsRepository) },
                        onBack = { if (backStack.lastOrNull() == BlockingOverviewRoute) backStack.removeLastOrNull() },
                        onLogin = { openFrom(BlockingOverviewRoute, AuthRoute()) },
                        onCategory = { openFrom(BlockingOverviewRoute, RuleManagementRoute(it)) },
                    )
                }
            }
            entry<RuleManagementRoute> { route ->
                NavigationPage {
                    if (settingsRepository != null && contentRepository != null) RuleManagementPage(
                        model = viewModel { RuleManagementViewModel(route.category, settingsRepository, contentRepository) },
                        onBack = { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() },
                        onLogin = { openFrom(route, AuthRoute()) },
                    )
                }
            }
            entry<InvitationsRoute> {
                NavigationPage {
                    if (settingsRepository != null) InvitationsPage(
                        model = viewModel { InvitationsViewModel(settingsRepository) },
                        onBack = { if (backStack.lastOrNull() == InvitationsRoute) backStack.removeLastOrNull() },
                        onLogin = { openFrom(InvitationsRoute, AuthRoute()) },
                    )
                }
            }
            entry<AccountSwitchRoute> {
                NavigationPage {
                    AccountSwitchPage(
                        state = accountState,
                        onSelectAccount = { id -> onSelectAccount(id.takeUnless { it == GUEST_ACCOUNT_ID }) },
                        onReauthenticate = { email ->
                            if (backStack.lastOrNull() == AccountSwitchRoute) backStack.add(AuthRoute(email))
                        },
                        onRemoveAccount = onRemoveAccount,
                        onAddAccount = {
                            if (backStack.lastOrNull() == AccountSwitchRoute) backStack.add(AuthRoute())
                        },
                        onBack = {
                            if (backStack.lastOrNull() == AccountSwitchRoute) backStack.removeLastOrNull()
                        },
                    )
                }
            }
            entry<AuthRoute> { route ->
                NavigationPage {
                    if (authRepository != null) AuthPage(
                        repository = authRepository,
                        initialEmail = route.email,
                        isActive = backStack.lastOrNull() == route,
                        onClose = {
                            if (backStack.lastOrNull() == route) backStack.removeLastOrNull()
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
                        onBack = {
                            if (backStack.lastOrNull() == AboutRoute) backStack.removeLastOrNull()
                        },
                    )
                }
            }
            entry<DanmakuSettingsRoute> {
                NavigationPage {
                    DanmakuSettingsScreen(
                        settings = danmakuSettings,
                        onChange = { danmakuSettingsModel?.update(it) },
                        onBack = { if (backStack.lastOrNull() == DanmakuSettingsRoute) backStack.removeLastOrNull() },
                    )
                }
            }
            entry<PlaybackSettingsRoute> {
                NavigationPage {
                    PlaybackSettingsScreen(
                        settings = playbackSettings,
                        onAutoPictureInPictureChange = { playbackSettingsModel?.setAutoPictureInPicture(it) },
                        onAutoplayChange = { playbackSettingsModel?.setAutoplay(it) },
                        onBack = { if (backStack.lastOrNull() == PlaybackSettingsRoute) backStack.removeLastOrNull() },
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
