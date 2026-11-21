package moe.kirakira.ui.navigation

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import dev.chrisbanes.haze.rememberHazeState
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
import moe.kirakira.feature.main.rememberMainTabState
import moe.kirakira.feature.player.MiniPlayerExitViewport
import moe.kirakira.feature.player.PlaybackHost
import moe.kirakira.feature.player.PlaybackViewModel
import moe.kirakira.feature.player.PlaybackViewportState
import moe.kirakira.feature.search.SearchViewModel
import moe.kirakira.feature.settings.DanmakuSettingsViewModel
import moe.kirakira.feature.settings.PlaybackSettingsViewModel
import moe.kirakira.feature.video.KaomojiViewModel
import moe.kirakira.feature.video.RetainedVideoPageViewModel
import moe.kirakira.feature.video.RetainedVideoPages
import moe.kirakira.feature.video.VideoPage
import moe.kirakira.ui.adaptive.LocalAppWindowLayout
import moe.kirakira.ui.adaptive.WindowPane
import moe.kirakira.ui.adaptive.rememberAppWindowLayout
import moe.kirakira.ui.components.LocalMenuHazeState
import moe.kirakira.ui.components.menuHazeSource
import moe.kirakira.ui.components.rememberEmphasizedEasing
import moe.kirakira.ui.components.video.LocalVideoZoomState
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemeMode
import kotlin.math.ceil

@Composable
internal fun AppNavHost(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeColors: ThemeColorSettings,
    onThemeColorsChange: (ThemeColorSettings) -> Unit,
    predictiveBackEnabled: Boolean,
    onPredictiveBackEnabledChange: (Boolean) -> Unit,
    backgroundDepthEnabled: Boolean,
    onBackgroundDepthEnabledChange: (Boolean) -> Unit,
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
    val mainTabs = rememberMainTabState()
    val imageViewerActive = backStack.lastOrNull() is ImageViewerRoute || navigation.composedViewers.isNotEmpty()
    LaunchedEffect(backStack) {
        var previous = emptySet<String>()
        snapshotFlow {
            backStack.filterIsInstance<ImageViewerRoute>().map { it.instanceId }.toSet()
        }.collect { current ->
            (current - previous).forEach { ImageViewerDiagnostics.stack(it, present = true) }
            (previous - current).forEach { ImageViewerDiagnostics.stack(it, present = false) }
            previous = current
        }
    }
    // Discard retired demo destinations when restoring navigation after an upgrade.
    LaunchedEffect(Unit) {
        backStack.removeAll {
            it is TestRoute || it is ComponentSamplesRoute ||
                (it is VideoRoute && it.videoId <= 0) || (it is ProfileRoute && it.uid <= 0) ||
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
    // Include composed viewers so the video stays underneath the entire image exit.
    val videoPageUnderImage = imageViewerActive && (
        videoPageActive || (backStack.lastOrNull() is ImageViewerRoute &&
            backStack.getOrNull(backStack.lastIndex - 1) is VideoRoute)
        )
    val viewportState = remember { PlaybackViewportState() }
    val retainedVideoModels = viewModel { RetainedVideoPageViewModel() }
    val currentRoute = backStack.last()
    var miniPlayerExitViewport by remember(playback) { mutableStateOf<MiniPlayerExitViewport?>(null) }
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
        navigation.retainSettingsInStack()
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
        if (!viewportState.backPreviewActive) {
            viewportState.backToMiniPlayer = playbackSettings?.inAppMiniPlayer == true &&
                playback.showPauseIcon && playback.player != null && !playback.failed
        }
        if (sessionChanged) playback.cancelAutoplay()
        if (playbackSettings?.inAppMiniPlayer != true) {
            if (playback.miniPlayer) playback.closeMiniPlayer()
            playback.finishMiniPlayerExit()
        }
        if (miniPlayerExitViewport?.session !== playback.miniPlayerExit) {
            playback.finishMiniPlayerExit()
            miniPlayerExitViewport = null
        }
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
    val windowLayout = rememberAppWindowLayout(viewportState.windowOrigin)
    val pane = windowLayout.pagePane
    val safePane = windowLayout.safePageBounds
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    val useDesktopRail = !windowLayout.tabletop && with(density) {
        safePane.width >= 600.dp.roundToPx() && safePane.height >= 480.dp.roundToPx()
    }
    val settingsPanes = with(density) { settingsPaneLayout(windowLayout, direction, useDesktopRail) }
    // The expanding player still docks against the chrome of its source page.
    val chromeIndex = if (videoPageActive && backStack.size > 1) backStack.lastIndex - 1 else backStack.lastIndex
    val chromeRoute = backStack[chromeIndex]
    val settingsRootIndex = backStack.indexOf(SettingsRoute)
    val chromeSettingsPanes = settingsPanes?.takeIf {
        settingsRootIndex in 0..chromeIndex && chromeRoute !in navigation.fullPageSettings &&
            backStack.subList(settingsRootIndex + 1, chromeIndex + 1).all { it.isSettingsDetail }
    }
    val activeSettingsPanes = chromeSettingsPanes.takeUnless { videoPageActive }
    val railControlInsets = desktopRailControlInsets()
    val windowHeight = windowLayout.size.height
    val showRailWordmark = useDesktopRail && remember(railControlInsets, windowHeight, pane, density) {
        derivedStateOf {
            // WindowPane already excludes the part of each inset outside the pane.
            val top = maxOf(pane?.top ?: 0, railControlInsets.getTop(density))
            val bottom = minOf(pane?.bottom ?: windowHeight, windowHeight - railControlInsets.getBottom(density))
            with(density) { (bottom - top).toDp() >= DesktopRailWordmarkMinHeight }
        }
    }.value
    val profile = accountState.activeProfile
    val startRail = if (useDesktopRail) ActivityStartRail<NavKey>(
        width = DesktopRailWidth,
        showsFor = { it == MainRoute || it.isDesktopRailChild() },
        enterFromBottom = navigation.railEnterFromBottom,
        content = { route, interactive ->
            val settingsRail = route == SettingsRoute && activeSettingsPanes != null
            val source = if (settingsRail) backStack.last() else route
            AppDesktopRail(
                route = route,
                tab = mainTabs.destination,
                profile = profile,
                interactive = interactive,
                controlInsets = railControlInsets,
                showWordmark = showRailWordmark,
                onSelect = { item ->
                    if (backStack.lastOrNull() == source && !(settingsRail && item.route == SettingsRoute)) {
                        val enterFromBottom = desktopRailEnterFromBottom(route, mainTabs.destination, item)
                        val tab = item.tab
                        val select: () -> Unit = {
                            if (tab != null) {
                                mainTabs.select(
                                    tab,
                                    reselectCurrent = route == MainRoute,
                                    animate = route == MainRoute,
                                )
                                navigation.openFromDesktopRail(route, MainRoute, enterFromBottom)
                            } else if (item == DesktopRailItem.ACCOUNT && profile == null) {
                                navigation.openFrom(route, AuthRoute())
                            } else if (item.route != null) {
                                navigation.openFromDesktopRail(route, item.route, enterFromBottom)
                            }
                        }
                        if (settingsRail) navigation.leaveSettings(
                            source,
                            changePathFromDirectory = select,
                        ) else select()
                    }
                },
            )
        },
    ) else null
    val chromeRailPadding = startRail?.takeIf {
        chromeSettingsPanes?.rail ?: it.showsFor(chromeRoute)
    }?.width ?: 0.dp
    val railPadding = if (videoPageActive) 0.dp else chromeRailPadding
    val chromeBottomBarHeight = if (!useDesktopRail && chromeRoute == MainRoute) navigation.mainBottomBarHeight else 0
    val bottomBarHeight = if (videoPageActive) 0 else chromeBottomBarHeight
    val chromeSize = if (chromeRailPadding > 0.dp) {
        // Playback positions its floating player in window coordinates, even when it docks in another pane.
        val railPane = chromeSettingsPanes?.directory ?: pane
        val railStart = if (direction == LayoutDirection.Ltr) {
            maxOf(railPane?.left ?: 0, safeDrawing.getLeft(density, direction))
        } else {
            maxOf(railPane?.let { windowLayout.size.width - it.right } ?: 0, safeDrawing.getRight(density, direction))
        }
        IntSize(railStart + with(density) { chromeRailPadding.roundToPx() }, 0)
    } else IntSize(0, chromeBottomBarHeight)
    SideEffect {
        viewportState.cards.onOpenVideo = { videoId, open ->
            val previousOrigin = viewportState.cards.origin
            open()
            if (backStack.lastOrNull() == VideoRoute(videoId) && playback.videoId != videoId &&
                !imageViewerActive && !viewportState.navigatingPage
            ) {
                playback.beginMiniPlayerExit()?.let { exit ->
                    miniPlayerExitViewport = MiniPlayerExitViewport(
                        session = exit,
                        origin = previousOrigin?.takeIf { it.videoId == exit.videoId },
                        pageViewport = viewportState.pageViewport?.takeIf { it.videoId == exit.videoId },
                        mainNavigationSize = chromeSize,
                    )
                }
            }
        }
    }
    val menuHazeState = rememberHazeState()
    CompositionLocalProvider(
        LocalMenuHazeState provides menuHazeState,
        LocalVideoZoomState provides viewportState.cards,
        LocalAppWindowLayout provides windowLayout,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .onGloballyPositioned { viewportState.windowOrigin = it.positionInWindow() },
        ) {
            ActivityNavDisplay(
                backStack = backStack,
                predictiveBackEnabled = predictiveBackEnabled,
                videoZoomState = viewportState,
                videoPageUnderImage = videoPageUnderImage,
                startRail = startRail,
                settingsPanes = settingsPanes,
                settingsRoot = SettingsRoute,
                fullPageSettings = navigation.fullPageSettings,
                onBackRequested = navigation::requestBack,
                canNavigateBack = navigation::canNavigateBack,
                onBack = {
                    if (backStack.lastOrNull() is AuthRoute) autofill?.cancel()
                    val viewer = backStack.lastOrNull() as? ImageViewerRoute
                    if (viewer != null) navigation.closeImageViewer(viewer, ImageViewerCloseReason.SYSTEM_BACK)
                    else backStack.lastOrNull()?.let(::popRoute)
                },
                onImageBack = { target ->
                    if (target is ImageViewerRoute) navigation.closeImageViewer(
                        target,
                        ImageViewerCloseReason.SYSTEM_BACK,
                    )
                },
                onImageDismiss = { target ->
                    if (target is ImageViewerRoute) navigation.closeImageViewer(
                        target,
                        ImageViewerCloseReason.DRAG_DOWN,
                    )
                },
                // Keep Haze recording away from the sibling PlaybackHost and its SurfaceView.
                modifier = Modifier
                    .videoZoomBackgroundEffect(viewportState, depthEnabled = backgroundDepthEnabled)
                    .menuHazeSource(menuHazeState),
                entryProvider = entryProvider {
                    registerMainEntries(
                        this, navigation, accountState, homeViewModel, searchViewModel,
                        mainTabs, useDesktopRail = useDesktopRail, showRailWordmark = showRailWordmark,
                    )
                    registerContentEntries(this, navigation, dependencies, accountState)
                    registerImageViewerEntries(this, navigation, imageMetadata, imageExports)
                    registerSettingsEntries(
                        this, navigation, dependencies, accountState, onLogout,
                        themeMode, onThemeModeChange, themeColors, onThemeColorsChange,
                        predictiveBackEnabled, onPredictiveBackEnabledChange,
                        backgroundDepthEnabled, onBackgroundDepthEnabledChange,
                        playbackSettingsModel, playbackSettings, danmakuSettingsModel, danmakuSettings,
                    )
                    registerAccountEntries(
                        this, navigation, authRepository, accountState, onSelectAccount, onRemoveAccount,
                        predictiveBackEnabled,
                        onCredentialSaveFailure = showFeedback,
                    )
                },
            )
            val retainedVideoId = (currentRoute as? VideoRoute)?.videoId
                ?: viewportState.exitingVideoId
                ?: playback.videoId?.takeIf { playback.miniPlayer }
                ?: backStack.filterIsInstance<VideoRoute>().lastOrNull()?.videoId
            val exitingMini = miniPlayerExitViewport?.takeIf { it.session === playback.miniPlayerExit }
            val exitingMiniId = exitingMini?.session?.videoId
            val displayedVideoIds = listOfNotNull(retainedVideoId, exitingMiniId).distinct().filter { it > 0 }
            val retainedIds = backStack.filterIsInstance<VideoRoute>().map { it.videoId }.toSet() + displayedVideoIds
            SideEffect { retainedVideoModels.retain(accountState.revision, retainedIds) }
            if (displayedVideoIds.isNotEmpty() && contentRepository != null && kaomojiModel != null) {
                RetainedVideoPages(
                    models = retainedVideoModels,
                    videoIds = displayedVideoIds,
                    retainedIds = retainedIds,
                    sessionRevision = accountState.revision,
                    activeVideoId = retainedVideoId.takeIf { videoPageActive && !imageViewerActive },
                ) { videoId ->
                    val route = VideoRoute(videoId)
                    val outgoing = exitingMini?.takeIf { videoId != retainedVideoId && videoId == exitingMiniId }
                    PlaybackHost(
                        playback = playback,
                        videoId = videoId,
                        viewportState = viewportState,
                        settings = playbackSettings,
                        videoPageActive = outgoing == null && videoPageActive,
                        imageViewerActive = imageViewerActive,
                        videoPageUnderImage = outgoing == null && videoPageUnderImage,
                        videoPageCovered = outgoing == null && !imageViewerActive && (
                            (!videoPageActive && backStack.any { it is VideoRoute }) ||
                                viewportState.coveringPageKeys.isNotEmpty()
                            ),
                        modifier = Modifier.zIndex(
                            if (outgoing != null || (playback.miniPlayer && !videoPageActive && !videoPageUnderImage &&
                                    !viewportState.navigatingPage)
                            ) 2f else 0f,
                        ),
                        mainNavigationSize = outgoing?.mainNavigationSize ?: chromeSize,
                        miniPlayerExit = outgoing,
                        onRestore = {
                            if (currentRoute !is VideoRoute && currentRoute !is ImageViewerRoute &&
                                navigation.composedViewers.isEmpty() && playback.miniPlayer &&
                                playback.videoId == videoId
                            ) {
                                // Use the page that displayed this mini player so a late restore
                                // cannot open the old video over a newly selected destination.
                                navigation.openFrom(currentRoute, route)
                            }
                        },
                        onCardExitFinished = {
                            if (viewportState.exitingVideoId == videoId) {
                                // Keep the source Scene mounted until the visual return is complete.
                                if (backStack.lastOrNull() == route) navigation.popFrom(route)
                                viewportState.exitingVideoId = null
                                if (playback.videoId == videoId) playback.leaveVideo(false)
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
                            videoId = videoId,
                            miniPlayerExit = outgoing?.session,
                            repository = contentRepository,
                            historyRepository = historyRepository,
                            settingsRepository = dependencies.settings,
                            isActive = interactive,
                            surfaceVisible = surfaceVisible,
                            initialImage = (if (outgoing == null) viewportState.cards.origin else outgoing.origin)
                                ?.takeIf { it.videoId == videoId }?.image,
                            onOpenProfile = { uid -> navigation.openFrom(route, ProfileRoute(uid)) },
                            onOpenTag = { id -> navigation.openFrom(route, TagRoute(id)) },
                            onOpenImage = { navigation.openFrom(route, ImageViewerRoute(it)) },
                            onLogin = { navigation.openFrom(route, AuthRoute()) },
                            onBack = { popRoute(route) },
                            modifier = pageModifier,
                        )
                    }
                }
            }
            val excludedBottom = pane?.let { windowLayout.size.height - it.bottom } ?: 0
            val safeBottom = (safeDrawing.getBottom(density) - excludedBottom).coerceAtLeast(0)
            val keyboardBottom = (WindowInsets.ime.getBottom(density) - excludedBottom).coerceAtLeast(0)
            val bottomBarPadding = if (keyboardBottom == 0) {
                with(density) { (bottomBarHeight - safeBottom).coerceAtLeast(0).toDp() }
            } else 0.dp
            if ((LocalActivity.current as? MainActivity)?.pictureInPicture != true) {
                WindowPane(pane, Modifier.zIndex(3f)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                            .padding(start = railPadding, bottom = bottomBarPadding),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        SnackbarHost(snackbar)
                    }
                }
            }
        }
    }
}

private const val MAX_VIDEO_ZOOM_BLUR_DP = 24
private const val MAX_VIDEO_ZOOM_SCRIM_ALPHA = 0.25f
private const val MAX_VIDEO_ZOOM_BACKGROUND_SCALE = 0.1f

/**
 * Pushes the page behind a card or mini player zoom into the distance. Android 12+ dims it; with
 * [depthEnabled] it also blurs there and, on Android 13+, shrinks it while mirroring its edges
 * into the uncovered margin, like iOS's background extension effect.
 */
@Composable
private fun Modifier.videoZoomBackgroundEffect(viewportState: PlaybackViewportState, depthEnabled: Boolean): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val density = LocalDensity.current.density
    val blurStep by remember(viewportState) {
        derivedStateOf(structuralEqualityPolicy()) {
            val fraction = viewportState.videoZoomBlurFraction?.invoke() ?: 0f
            ceil(MAX_VIDEO_ZOOM_BLUR_DP * fraction).toInt()
        }
    }
    val blurEffects = remember(density) { arrayOfNulls<BlurEffect>(MAX_VIDEO_ZOOM_BLUR_DP + 1) }
    val scrimFilters = remember { arrayOfNulls<ColorFilter>(MAX_VIDEO_ZOOM_BLUR_DP + 1) }
    val shrinkEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) VideoZoomShrinkEffect() else null
    }
    return graphicsLayer {
        val step = blurStep
        if (step == 0) {
            renderEffect = null
            colorFilter = null
            return@graphicsLayer
        }
        val fraction = (viewportState.videoZoomBlurFraction?.invoke() ?: 0f).coerceIn(0f, 1f)
        renderEffect = when {
            !depthEnabled -> null
            // Scale follows the raw progress; the blur radius stays quantized.
            shrinkEffect != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> shrinkEffect.create(
                width = size.width,
                height = size.height,
                scale = 1f - MAX_VIDEO_ZOOM_BACKGROUND_SCALE * fraction,
                blurRadius = step * density,
            )
            else -> blurEffects[step] ?: BlurEffect(step * density, step * density).also { blurEffects[step] = it }
        }
        colorFilter = scrimFilters[step] ?: ColorFilter.tint(
            color = Color.Black.copy(alpha = MAX_VIDEO_ZOOM_SCRIM_ALPHA * step / MAX_VIDEO_ZOOM_BLUR_DP),
            blendMode = BlendMode.SrcAtop,
        ).also { scrimFilters[step] = it }
    }
}

/**
 * Shrinks the layer about its centre and fills the uncovered margin by mirroring the page across its
 * edges, then blurs the result so the reflection reads as a soft continuation rather than a seam.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class VideoZoomShrinkEffect {
    private val shader = RuntimeShader(
        """
        uniform shader content;
        uniform float2 size;
        uniform float scale;

        half4 main(float2 position) {
            float2 center = size * 0.5;
            float2 source = center + (position - center) / scale;
            // Reflect once across each edge; the margin never exceeds the page itself.
            source = abs(source);
            source = size - abs(size - source);
            return content.eval(clamp(source, float2(0.5), size - 0.5));
        }
        """.trimIndent(),
    )

    fun create(width: Float, height: Float, scale: Float, blurRadius: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("scale", scale)
        val shrink = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        val blur = android.graphics.RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        return android.graphics.RenderEffect.createChainEffect(blur, shrink).asComposeRenderEffect()
    }
}
