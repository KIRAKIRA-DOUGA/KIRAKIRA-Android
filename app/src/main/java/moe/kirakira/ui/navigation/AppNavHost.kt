package moe.kirakira.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import moe.kirakira.R
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.auth.SessionState
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.profile.ProfileRepository
import moe.kirakira.data.security.SecurityRepository
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.feature.account.AccountSwitchPage
import moe.kirakira.feature.account.GUEST_ACCOUNT_ID
import moe.kirakira.feature.account.SessionFeedback
import moe.kirakira.feature.auth.AuthPage
import moe.kirakira.feature.auth.AuthRoute
import moe.kirakira.feature.follow.FollowListPage
import moe.kirakira.feature.follow.FollowListViewModel
import moe.kirakira.feature.history.HistoryHostViewModel
import moe.kirakira.feature.history.HistoryPage
import moe.kirakira.feature.history.HistoryViewModel
import moe.kirakira.feature.imageviewer.ImageViewerCloseReason
import moe.kirakira.feature.imageviewer.ImageViewerDiagnostics
import moe.kirakira.feature.imageviewer.ImageViewerPage
import moe.kirakira.feature.main.HomeViewModel
import moe.kirakira.feature.main.MainScreen
import moe.kirakira.feature.player.PlaybackHost
import moe.kirakira.feature.player.PlaybackViewModel
import moe.kirakira.feature.profile.ProfilePage
import moe.kirakira.feature.search.SearchPage
import moe.kirakira.feature.search.SearchTopBar
import moe.kirakira.feature.search.SearchUiState
import moe.kirakira.feature.search.SearchViewModel
import moe.kirakira.feature.settings.AboutScreen
import moe.kirakira.feature.settings.AppearanceScreen
import moe.kirakira.feature.settings.DanmakuSettings
import moe.kirakira.feature.settings.DanmakuSettingsScreen
import moe.kirakira.feature.settings.DanmakuSettingsViewModel
import moe.kirakira.feature.settings.LicensesScreen
import moe.kirakira.feature.settings.PlaybackSettings
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
import moe.kirakira.feature.tag.TagPage
import moe.kirakira.feature.tag.TagViewModel
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
    predictiveBackEnabled: Boolean,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    accountState: SessionState,
    authRepository: AuthRepository?,
    onLogout: () -> Unit,
    onResetLocalAccounts: () -> Unit,
    onRetrySession: () -> Unit,
    onDismissSessionError: () -> Unit,
    onSelectAccount: (String?) -> Unit,
    onRemoveAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    onVideoPageActiveChange: (Boolean) -> Unit = {},
    onImageViewerActiveChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val playbackSettingsModel = if (LocalInspectionMode.current) null else viewModel<PlaybackSettingsViewModel>()
    val playbackSettings = if (LocalInspectionMode.current) PlaybackSettings()
        else playbackSettingsModel?.settings?.collectAsStateWithLifecycle()?.value
    val danmakuSettingsModel = if (LocalInspectionMode.current) null else viewModel<DanmakuSettingsViewModel>()
    val danmakuSettings = if (LocalInspectionMode.current) DanmakuSettings()
        else danmakuSettingsModel?.settings?.collectAsStateWithLifecycle()?.value
    val backStack = rememberNavBackStack(MainRoute)
    val composedViewers = remember { mutableStateMapOf<String, Int>() }
    val imageViewerActive = backStack.lastOrNull() is ImageViewerRoute || composedViewers.isNotEmpty()
    LaunchedEffect(backStack) {
        var previous = emptySet<String>()
        snapshotFlow { backStack.filterIsInstance<ImageViewerRoute>().map { it.instanceId }.toSet() }.collect { current ->
            (current - previous).forEach { ImageViewerDiagnostics.stack(it, present = true) }
            (previous - current).forEach { ImageViewerDiagnostics.stack(it, present = false) }
            previous = current
        }
    }
    var profileBackGuard by remember { mutableStateOf<ProfileEditorViewModel?>(null) }
    var privacyBackGuard by remember { mutableStateOf<PrivacySettingsViewModel?>(null) }
    var securityBackGuard by remember { mutableStateOf<SecuritySettingsViewModel?>(null) }
    // Discard retired demo destinations when restoring navigation after an upgrade.
    LaunchedEffect(Unit) {
        backStack.removeAll {
            it is TestRoute || (it is VideoRoute && it.videoId <= 0) || (it is ProfileRoute && it.uid <= 0) ||
                (it is TagRoute && it.tagId <= 0) || (it is FollowListRoute && it.uid <= 0)
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
    val historyRepository = if (LocalInspectionMode.current) null else authRepository?.let { auth ->
        viewModel { HistoryHostViewModel(apiClient, auth) }.repository
    }
    val playback = if (LocalInspectionMode.current) {
        remember { PlaybackViewModel(context.applicationContext, SavedStateHandle()) }
    } else viewModel { PlaybackViewModel(context.applicationContext, createSavedStateHandle(), historyRepository) }
    val homeViewModel = contentRepository?.let { viewModel { HomeViewModel(it) } }
    val searchViewModel = contentRepository?.let { viewModel { SearchViewModel(it) } }
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
    fun closeImageViewer(route: ImageViewerRoute, reason: ImageViewerCloseReason) {
        val accepted = backStack.size > 1 &&
            (backStack.lastOrNull() as? ImageViewerRoute)?.instanceId == route.instanceId
        ImageViewerDiagnostics.close(route.instanceId, reason, accepted)
        // Removing this exact instance makes repeated/stale callbacks harmless, including on reopen.
        if (accepted) backStack.removeLastOrNull()
    }
    val videoPageActive = backStack.lastOrNull() is VideoRoute
    LaunchedEffect(backStack.lastOrNull()) {
        if (backStack.lastOrNull() is AuthRoute) playback.pause()
    }
    SideEffect {
        onVideoPageActiveChange(videoPageActive)
        onImageViewerActiveChange(imageViewerActive)
        val sessionChanged = playback.syncSession(accountState.revision)
        val video = backStack.lastOrNull() as? VideoRoute
        if (video != null && contentRepository != null && video.videoId > 0) playback.showVideo(video.videoId)
        else playback.leaveVideo(playbackSettings?.inAppMiniPlayer == true)
        if (sessionChanged) playback.cancelAutoplay()
        if (playback.miniPlayer && playbackSettings?.inAppMiniPlayer != true) playback.closeMiniPlayer()
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
    Box(modifier.fillMaxSize()) {
        ActivityNavDisplay(
            backStack = backStack,
            predictiveBackEnabled = predictiveBackEnabled,
            onBackRequested = {
                when (backStack.lastOrNull()) {
                    ProfileEditorRoute -> profileBackGuard?.requestBack() == true
                    PrivacySettingsRoute -> privacyBackGuard?.requestBack() == true
                    SecuritySettingsRoute -> {
                        autofill?.cancel()
                        securityBackGuard?.requestBack() == true
                    }
                    else -> true
                }
            },
            canNavigateBack = {
                when (backStack.lastOrNull()) {
                    ProfileEditorRoute -> profileBackGuard?.canNavigateBack == true
                    PrivacySettingsRoute -> privacyBackGuard?.canNavigateBack == true
                    SecuritySettingsRoute -> securityBackGuard?.canNavigateBack == true
                    else -> true
                }
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            onBack = {
                if (backStack.lastOrNull() is AuthRoute) autofill?.cancel()
                val viewer = backStack.lastOrNull() as? ImageViewerRoute
                if (viewer != null) closeImageViewer(viewer, ImageViewerCloseReason.SYSTEM_BACK)
                else if (backStack.size > 1) backStack.removeLastOrNull()
            },
            onImageBack = { target ->
                if (target is ImageViewerRoute) closeImageViewer(target, ImageViewerCloseReason.SYSTEM_BACK)
            },
            entryProvider = entryProvider {
                entry<TestRoute> {
                    // Retained only until the restored legacy entry is removed above.
                }
                entry<MainRoute> {
                    NavigationPage {
                        MainScreen(
                            onBottomBarHeightChange = { bottomBarHeightPx = it },
                            profile = accountState.activeProfile,
                            onOpenProfile = {
                                openFrom(MainRoute, if (accountState.activeProfile == null) AuthRoute() else SelfProfileRoute)
                            },
                            onOpenHistory = { openFrom(MainRoute, HistoryRoute) },
                            onOpenSettings = {
                                if (backStack.lastOrNull() == MainRoute) backStack.add(SettingsRoute)
                            },
                            onOpenVideo = { id ->
                                if (backStack.lastOrNull() == MainRoute) backStack.add(VideoRoute(id))
                            },
                            videos = homeVideos, onRefreshVideos = { homeViewModel?.refresh() },
                            searchTopBar = {
                                val state = searchViewModel?.state?.collectAsStateWithLifecycle()?.value ?: SearchUiState()
                                SearchTopBar(state, { searchViewModel?.onEvent(it) })
                            },
                            searchContent = { padding ->
                                SearchPage(
                                    model = searchViewModel,
                                    onOpenVideo = { id -> openFrom(MainRoute, VideoRoute(id)) },
                                    contentPadding = padding,
                                )
                            },
                        )
                    }
                }
                entry<VideoRoute> { route ->
                    NavigationPage {
                        if (contentRepository == null || route.videoId <= 0) {
                            Text(stringResource(R.string.content_login_to_interact))
                        } else {
                            VideoPage(
                                playback = playback,
                                onPlayerBounds = playback::updateBounds,
                                danmakuSettings = danmakuSettings,
                                onDanmakuEnabled = { danmakuSettingsModel?.setEnabled(it) },
                                onQualityPreference = { playbackSettingsModel?.setQuality(it) },
                                playbackSettings = playbackSettings,
                                videoId = route.videoId,
                                repository = contentRepository,
                                historyRepository = historyRepository,
                                isActive = backStack.lastOrNull() == route,
                                onOpenProfile = { uid -> openFrom(route, ProfileRoute(uid)) },
                                onOpenTag = { id -> openFrom(route, TagRoute(id)) },
                                onLogin = {
                                    if (backStack.lastOrNull() == route) backStack.add(AuthRoute())
                                },
                                onBack = { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() },
                            )
                        }
                    }
                }
                entry<HistoryRoute> {
                    NavigationPage {
                        if (historyRepository != null) HistoryPage(
                            model = viewModel { HistoryViewModel(historyRepository) },
                            isActive = backStack.lastOrNull() == HistoryRoute,
                            onBack = { if (backStack.lastOrNull() == HistoryRoute) backStack.removeLastOrNull() },
                            onLogin = { openFrom(HistoryRoute, AuthRoute()) },
                            onOpenVideo = { id -> openFrom(HistoryRoute, VideoRoute(id)) },
                        )
                    }
                }
                entry<TagRoute> { route ->
                    NavigationPage {
                        if (contentRepository != null && route.tagId > 0) {
                            TagPage(
                                model = viewModel { TagViewModel(route.tagId, contentRepository) },
                                onBack = { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() },
                                onOpenVideo = { id -> openFrom(route, VideoRoute(id)) },
                            )
                        }
                    }
                }
                entry<ProfileRoute> { route ->
                    NavigationPage {
                        if (contentRepository == null || route.uid <= 0) Text(stringResource(R.string.content_login_to_interact))
                        else ProfilePage(model = viewModel { moe.kirakira.feature.profile.ProfileViewModel(route.uid, contentRepository) },
                            onEditProfile = { openFrom(route, ProfileEditorRoute) },
                            onOpenFollowList = { kind -> openFrom(route, FollowListRoute(route.uid, kind)) },
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
                            onEditProfile = { openFrom(SelfProfileRoute, ProfileEditorRoute) },
                            onOpenFollowList = { kind -> openFrom(SelfProfileRoute, FollowListRoute(account.uid, kind)) },
                            onOpenVideo = { id -> openFrom(SelfProfileRoute, VideoRoute(id)) },
                            onOpenImage = { openFrom(SelfProfileRoute, ImageViewerRoute(it)) }, onLogin = {},
                            onBack = { if (backStack.lastOrNull() == SelfProfileRoute) backStack.removeLastOrNull() })
                    }
                }
                entry<FollowListRoute> { route ->
                    NavigationPage {
                        if (contentRepository != null && route.uid > 0) {
                            FollowListPage(
                                model = viewModel { FollowListViewModel(route.uid, route.kind, contentRepository) },
                                isActive = backStack.lastOrNull() == route,
                                onBack = { if (backStack.lastOrNull() == route) backStack.removeLastOrNull() },
                                onOpenProfile = { uid -> openFrom(route, ProfileRoute(uid)) },
                            )
                        }
                    }
                }
                entry<ImageViewerRoute>(metadata = imageMetadata) { route ->
                    // Keep light system-bar icons until the viewer's exit animation is disposed.
                    DisposableEffect(route.instanceId) {
                        composedViewers[route.instanceId] = composedViewers.getOrDefault(route.instanceId, 0) + 1
                        ImageViewerDiagnostics.composition(route.instanceId, attached = true)
                        onDispose {
                            val remaining = composedViewers.getOrDefault(route.instanceId, 1) - 1
                            if (remaining == 0) composedViewers.remove(route.instanceId)
                            else composedViewers[route.instanceId] = remaining
                            ImageViewerDiagnostics.composition(route.instanceId, attached = false)
                        }
                    }
                    var drawable by remember(route.instanceId) { mutableStateOf(false) }
                    val visibility = rememberImageVisibility()
                    ImageViewerPage(
                        image = route.image,
                        instanceId = route.instanceId,
                        onBack = { closeImageViewer(route, ImageViewerCloseReason.CLOSE_BUTTON) },
                        imageModifier = Modifier.imageSharedBounds(
                            route.image.sharedKey, viewer = true, drawable = drawable,
                        ),
                        controlsModifier = Modifier.imageControlsOverlay(),
                        onDrawableChange = { drawable = it },
                        transitioning = visibility.value != 1f,
                        visibilityProgress = { visibility.value },
                    )
                }
                entry<SettingsRoute> {
                    NavigationPage {
                        SettingsScreen(
                            onNavigateToProfile = { openFrom(SettingsRoute, ProfileEditorRoute) },
                            onNavigateToPrivacy = { openFrom(SettingsRoute, PrivacySettingsRoute) },
                            onNavigateToSecurity = { openFrom(SettingsRoute,
                                if (accountState.activeProfile == null) AuthRoute() else SecuritySettingsRoute) },
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
                entry<ProfileEditorRoute> {
                    NavigationPage {
                        if (authRepository != null) {
                            val model = viewModel {
                                ProfileEditorViewModel(ProfileRepository(apiClient, authRepository), context)
                            }
                            DisposableEffect(model) {
                                profileBackGuard = model
                                onDispose { if (profileBackGuard === model) profileBackGuard = null }
                            }
                            ProfileEditorPage(model,
                                onBack = { if (backStack.lastOrNull() == ProfileEditorRoute) backStack.removeLastOrNull() },
                                onLogin = { openFrom(ProfileEditorRoute, AuthRoute()) })
                        }
                    }
                }
                entry<PrivacySettingsRoute> {
                    NavigationPage {
                        if (settingsRepository != null) {
                            val model = viewModel { PrivacySettingsViewModel(settingsRepository) }
                            DisposableEffect(model) {
                                privacyBackGuard = model
                                onDispose { if (privacyBackGuard === model) privacyBackGuard = null }
                            }
                            PrivacySettingsPage(
                                model = model,
                                onBack = {
                                    if (backStack.lastOrNull() == PrivacySettingsRoute) backStack.removeLastOrNull()
                                },
                                onLogin = { openFrom(PrivacySettingsRoute, AuthRoute()) },
                            )
                        }
                    }
                }
                entry<SecuritySettingsRoute> {
                    NavigationPage {
                        if (authRepository != null) {
                            val model = viewModel { SecuritySettingsViewModel(SecurityRepository(apiClient, authRepository)) }
                            DisposableEffect(model) {
                                securityBackGuard = model
                                onDispose { if (securityBackGuard === model) securityBackGuard = null }
                            }
                            SecuritySettingsPage(
                                model = model,
                                isActive = backStack.lastOrNull() == SecuritySettingsRoute,
                                onBack = { if (backStack.lastOrNull() == SecuritySettingsRoute) backStack.removeLastOrNull() },
                                onLogin = { email ->
                                    openFrom(SecuritySettingsRoute, AuthRoute(email))
                                },
                            )
                        }
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
                            predictiveBackEnabled = predictiveBackEnabled,
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
                            onOutsideAppMiniPlayerChange = { playbackSettingsModel?.setOutsideAppMiniPlayer(it) },
                            onInAppMiniPlayerChange = { playbackSettingsModel?.setInAppMiniPlayer(it) },
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
                            predictiveBackEnabled = predictiveBackEnabled,
                            onPredictiveBackEnabledChange = onPredictiveBackEnabledChange,
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
        val restoreOrigin = backStack.lastOrNull()
        PlaybackHost(
            playback = playback,
            settings = playbackSettings,
            videoPageActive = videoPageActive,
            imageViewerActive = imageViewerActive,
            bottomBarHeightPx = if (backStack.lastOrNull() == MainRoute) bottomBarHeightPx else 0,
            onRestore = {
                if (composedViewers.isEmpty() && backStack.lastOrNull() !is ImageViewerRoute &&
                    backStack.lastOrNull() == restoreOrigin
                ) {
                    playback.videoId?.let { id ->
                        val route = VideoRoute(id)
                        val index = backStack.indexOf(route)
                        if (index >= 0) {
                            while (backStack.lastIndex > index) backStack.removeLastOrNull()
                        } else backStack.add(route)
                    }
                }
            },
        )
    }
}
