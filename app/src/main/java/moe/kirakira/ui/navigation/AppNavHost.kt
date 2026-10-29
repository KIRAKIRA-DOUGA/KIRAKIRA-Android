package moe.kirakira.ui.navigation

import android.annotation.SuppressLint
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import kotlinx.coroutines.launch
import moe.kirakira.AppDependenciesViewModel
import moe.kirakira.MainActivity
import moe.kirakira.data.auth.SessionState
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.feature.account.SessionFeedback
import moe.kirakira.feature.auth.AuthRoute
import moe.kirakira.feature.imageviewer.ImageExportViewModel
import moe.kirakira.feature.imageviewer.ImageViewerCloseReason
import moe.kirakira.feature.imageviewer.ImageViewerDiagnostics
import moe.kirakira.feature.main.HomeViewModel
import moe.kirakira.feature.player.PlaybackHost
import moe.kirakira.feature.player.PlaybackViewModel
import moe.kirakira.feature.player.PlaybackViewportState
import moe.kirakira.feature.search.SearchViewModel
import moe.kirakira.feature.settings.DanmakuSettingsViewModel
import moe.kirakira.feature.settings.PlaybackSettingsViewModel
import moe.kirakira.feature.video.KaomojiViewModel
import moe.kirakira.feature.video.RetainedVideoPage
import moe.kirakira.feature.video.RetainedVideoPageViewModel
import moe.kirakira.feature.video.VideoPage
import moe.kirakira.ui.components.rememberEmphasizedEasing
import moe.kirakira.ui.components.video.LocalVideoZoomState
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
    dependencies: AppDependenciesViewModel?,
    onLogout: () -> Unit,
    onResetLocalAccounts: () -> Unit,
    onRetrySession: () -> Unit,
    onDismissSessionError: () -> Unit,
    onDismissSessionNotice: () -> Unit,
    onSelectAccount: (String?) -> Unit,
    onRemoveAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
    onVideoPageActiveChange: (Boolean) -> Unit = {},
    onImageViewerActiveChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val snackbar = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    val feedbackScope = rememberCoroutineScope()
    val showFeedback: (Int) -> Unit = { message ->
        feedbackScope.launch { snackbar.showSnackbar(resources.getString(message)) }
    }
    val imageExports = dependencies?.imageExports?.let {
        viewModel { ImageExportViewModel(applicationContext, it) }
    }
    LaunchedEffect(imageExports, applicationContext) {
        imageExports?.feedback?.collect { message ->
            snackbar.showSnackbar(resources.getString(message))
        }
    }
    val playbackSettingsModel = if (LocalInspectionMode.current) null else viewModel<PlaybackSettingsViewModel>()
    val playbackSettings = if (LocalInspectionMode.current) PlaybackSettings()
        else playbackSettingsModel?.settings?.collectAsStateWithLifecycle()?.value
    val currentPlaybackSettings by rememberUpdatedState(playbackSettings)
    val danmakuSettingsModel = if (LocalInspectionMode.current) null else viewModel<DanmakuSettingsViewModel>()
    val danmakuSettings = if (LocalInspectionMode.current) DanmakuSettings()
        else danmakuSettingsModel?.settings?.collectAsStateWithLifecycle()?.value
    val backStack = rememberNavBackStack(MainRoute)
    val navigation = remember(backStack) { AppNavigationState(backStack) }
    val imageViewerActive = backStack.lastOrNull() is ImageViewerRoute || navigation.composedViewers.isNotEmpty()
    LaunchedEffect(backStack) {
        var previous = emptySet<String>()
        snapshotFlow { backStack.filterIsInstance<ImageViewerRoute>().map { it.instanceId }.toSet() }.collect { current ->
            (current - previous).forEach { ImageViewerDiagnostics.stack(it, present = true) }
            (previous - current).forEach { ImageViewerDiagnostics.stack(it, present = false) }
            previous = current
        }
    }
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
    val contentRepository = dependencies?.content
    val authRepository = dependencies?.auth
    val historyRepository = dependencies?.history
    // Only the IDE preview uses an empty handle; runtime creation uses the saved-state owner below.
    @SuppressLint("VisibleForTests")
    val playback = if (LocalInspectionMode.current) {
        remember { PlaybackViewModel(context.applicationContext, SavedStateHandle()) }
    } else viewModel { PlaybackViewModel(context.applicationContext, createSavedStateHandle(), historyRepository) }
    val kaomojiModel = dependencies?.let { viewModel { KaomojiViewModel(it.recentKaomoji) } }
    val homeViewModel = contentRepository?.let { viewModel { HomeViewModel(it) } }
    val searchViewModel = dependencies?.let { viewModel { SearchViewModel(it.content, it.searchHistory) } }
    val videoPageActive = backStack.lastOrNull() is VideoRoute
    val viewportState = remember { PlaybackViewportState() }
    val retainedVideoModels = viewModel { RetainedVideoPageViewModel() }
    fun popRoute(route: NavKey) {
        if (route is VideoRoute && backStack.lastOrNull() == route) {
            val keepMiniPlayer = currentPlaybackSettings?.inAppMiniPlayer == true && playback.showPauseIcon &&
                playback.player != null && !playback.failed
            if (!keepMiniPlayer) {
                viewportState.exitingVideoId = route.videoId
                return
            }
            // Publish the mini-player destination before removing its navigation entry so the
            // retained page never disappears between the route change and the next SideEffect.
            playback.leaveVideo(true)
        }
        navigation.popFrom(route)
    }
    LaunchedEffect(backStack.lastOrNull()) {
        if (backStack.lastOrNull() is AuthRoute) playback.pause()
    }
    SideEffect {
        viewportState.cards.ownerProvider = { backStack.lastOrNull() }
        onVideoPageActiveChange(videoPageActive)
        onImageViewerActiveChange(imageViewerActive)
        val sessionChanged = playback.syncSession(accountState.revision)
        val video = backStack.lastOrNull() as? VideoRoute
        if (video != null && contentRepository != null && video.videoId > 0) {
            if (viewportState.exitingVideoId != video.videoId) viewportState.exitingVideoId = null
            playback.showVideo(video.videoId)
        } else if (viewportState.exitingVideoId == null) {
            playback.leaveVideo(playbackSettings?.inAppMiniPlayer == true)
        }
        if (sessionChanged) viewportState.exitingVideoId = null
        if (viewportState.backProgress == null) {
            viewportState.backToMiniPlayer = playbackSettings?.inAppMiniPlayer == true &&
                playback.showPauseIcon && playback.player != null && !playback.failed
        }
        if (sessionChanged) playback.cancelAutoplay()
        if (playback.miniPlayer && playbackSettings?.inAppMiniPlayer != true) playback.closeMiniPlayer()
        if (sessionChanged || (backStack.none { it is VideoRoute } && !playback.miniPlayer &&
            viewportState.exitingVideoId == null)) {
            viewportState.cards.clear()
        }
    }

    SessionFeedback(
        state = accountState,
        onDismissError = onDismissSessionError,
        onDismissNotice = onDismissSessionNotice,
        onRetry = onRetrySession,
        onResetLocalAccounts = onResetLocalAccounts,
        onLogin = { email ->
            val source = backStack.lastOrNull()
            if (source != null && source !is AuthRoute) navigation.openFrom(source, AuthRoute(email))
        },
    )

    // Keep the host opaque under translated pages and the predictive back preview.
    CompositionLocalProvider(LocalVideoZoomState provides viewportState.cards) {
        Box(
            modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
                .onGloballyPositioned { viewportState.windowOrigin = it.positionInWindow() },
        ) {
            ActivityNavDisplay(
                backStack = backStack,
                predictiveBackEnabled = predictiveBackEnabled,
                videoZoomState = viewportState,
                onBackRequested = navigation::requestBack,
                canNavigateBack = navigation::canNavigateBack,
                onBack = {
                    if (backStack.lastOrNull() is AuthRoute) autofill?.cancel()
                    val viewer = backStack.lastOrNull() as? ImageViewerRoute
                    if (viewer != null) navigation.closeImageViewer(viewer, ImageViewerCloseReason.SYSTEM_BACK)
                    else backStack.lastOrNull()?.let(::popRoute)
                },
                onImageBack = { target ->
                    if (target is ImageViewerRoute) navigation.closeImageViewer(target, ImageViewerCloseReason.SYSTEM_BACK)
                },
                entryProvider = entryProvider {
                    registerMainEntries(this, navigation, accountState, homeViewModel, searchViewModel)
                    registerContentEntries(this, navigation, dependencies, accountState)
                    registerImageViewerEntries(this, navigation, imageMetadata, imageExports)
                    registerSettingsEntries(
                        this, navigation, dependencies, accountState, onLogout,
                        themeMode, onThemeModeChange, themeColors, onThemeColorsChange,
                        predictiveBackEnabled, onPredictiveBackEnabledChange,
                        playbackSettingsModel, playbackSettings, danmakuSettingsModel, danmakuSettings,
                    )
                    registerAccountEntries(
                        this, navigation, authRepository, accountState, onSelectAccount, onRemoveAccount,
                        predictiveBackEnabled,
                        onCredentialSaveFailure = showFeedback,
                    )
                },
            )
            val restoreOrigin = backStack.lastOrNull()
            val retainedVideoId = (restoreOrigin as? VideoRoute)?.videoId
                ?: viewportState.exitingVideoId
                ?: playback.videoId?.takeIf { playback.miniPlayer }
                ?: backStack.filterIsInstance<VideoRoute>().lastOrNull()?.videoId
            val retainedIds = backStack.filterIsInstance<VideoRoute>().map { it.videoId }.toSet() +
                listOfNotNull(retainedVideoId)
            SideEffect { retainedVideoModels.retain(accountState.revision, retainedIds) }
            if (retainedVideoId != null && retainedVideoId > 0 && contentRepository != null && kaomojiModel != null) {
                val route = VideoRoute(retainedVideoId)
                RetainedVideoPage(
                    models = retainedVideoModels,
                    videoId = retainedVideoId,
                    retainedIds = retainedIds,
                    sessionRevision = accountState.revision,
                    isActive = videoPageActive,
                ) {
                    PlaybackHost(
                        playback = playback,
                        videoId = retainedVideoId,
                        viewportState = viewportState,
                        settings = playbackSettings,
                        videoPageActive = videoPageActive,
                        imageViewerActive = imageViewerActive,
                        videoPageCovered = !imageViewerActive && (
                            (!videoPageActive && backStack.any { it is VideoRoute }) ||
                                viewportState.coveringPageKeys.isNotEmpty()
                            ),
                        modifier = Modifier.zIndex(
                            if (playback.miniPlayer && !videoPageActive && !viewportState.navigatingPage) 2f else 0f,
                        ),
                        bottomBarHeightPx = if (restoreOrigin == MainRoute) navigation.bottomBarHeightPx else 0,
                        onRestore = {
                            val currentRoute = backStack.lastOrNull()
                            if (navigation.composedViewers.isEmpty() && currentRoute !is ImageViewerRoute &&
                                currentRoute != route) {
                                currentRoute?.let { navigation.openFrom(it, route) }
                            }
                        },
                        onCardExitFinished = {
                            if (viewportState.exitingVideoId == retainedVideoId) {
                                // Keep the source Scene mounted until the visual return is complete.
                                if (backStack.lastOrNull() == route) navigation.popFrom(route)
                                viewportState.exitingVideoId = null
                                if (playback.videoId == retainedVideoId) playback.leaveVideo(false)
                            }
                        },
                    ) { pageModifier, interactive, surfaceVisible ->
                        VideoPage(
                            playback = playback,
                            viewportState = viewportState,
                            kaomojiModel = kaomojiModel,
                            onPlayerBounds = playback::updateBounds,
                            danmakuSettings = danmakuSettings,
                            onDanmakuEnabled = { danmakuSettingsModel?.setEnabled(it) },
                            onOutsideAppMiniPlayerChange = { playbackSettingsModel?.setOutsideAppMiniPlayer(it) },
                            onInAppMiniPlayerChange = { playbackSettingsModel?.setInAppMiniPlayer(it) },
                            onAutoplayChange = { playbackSettingsModel?.setAutoplay(it) },
                            onDanmakuSettingsChange = { danmakuSettingsModel?.update(it) },
                            onQualityPreference = { playbackSettingsModel?.setQuality(it) },
                            playbackSettings = playbackSettings,
                            videoId = retainedVideoId,
                            repository = contentRepository,
                            historyRepository = historyRepository,
                            settingsRepository = dependencies.settings,
                            isActive = interactive,
                            surfaceVisible = surfaceVisible,
                            initialImage = viewportState.cards.origin?.takeIf { it.videoId == retainedVideoId }?.image,
                            onOpenProfile = { uid -> navigation.openFrom(route, ProfileRoute(uid)) },
                            onOpenTag = { id -> navigation.openFrom(route, TagRoute(id)) },
                            onLogin = { navigation.openFrom(route, AuthRoute()) },
                            onBack = { popRoute(route) },
                            modifier = pageModifier,
                        )
                    }
                }
            }
            val density = LocalDensity.current
            val safeBottom = WindowInsets.safeDrawing.getBottom(density)
            val keyboardBottom = WindowInsets.ime.getBottom(density)
            val bottomBarPadding = if (backStack.lastOrNull() == MainRoute && keyboardBottom == 0) {
                with(density) { (navigation.bottomBarHeightPx - safeBottom).coerceAtLeast(0).toDp() }
            } else 0.dp
            if ((LocalActivity.current as? MainActivity)?.pictureInPicture != true) {
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .zIndex(3f)
                        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                        .padding(bottom = bottomBarPadding),
                )
            }
        }
    }
}
