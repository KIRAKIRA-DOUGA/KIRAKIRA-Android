package moe.kirakira.navigation

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
import androidx.compose.runtime.mutableStateListOf
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
import moe.kirakira.feature.search.SearchViewModel
import moe.kirakira.feature.settings.playback.DanmakuSettingsViewModel
import moe.kirakira.feature.settings.playback.PlaybackSettingsViewModel
import moe.kirakira.feature.video.KaomojiViewModel
import moe.kirakira.feature.video.RetainedVideoPageViewModel
import moe.kirakira.feature.video.RetainedVideoPages
import moe.kirakira.feature.video.VideoPage
import moe.kirakira.ui.adaptive.LocalAppWindowLayout
import moe.kirakira.ui.adaptive.WindowPane
import moe.kirakira.ui.adaptive.rememberAppWindowLayout
import moe.kirakira.ui.components.motion.rememberEmphasizedEasing
import moe.kirakira.ui.components.overlay.LocalMenuHazeState
import moe.kirakira.ui.components.overlay.menuHazeSource
import moe.kirakira.ui.components.video.LocalVideoZoomState
import moe.kirakira.ui.components.video.completeVideoZoomTap
import moe.kirakira.ui.navigation.ActivityNavDisplay
import moe.kirakira.ui.navigation.ActivityStartRail
import moe.kirakira.ui.navigation.PlaybackViewportState
import moe.kirakira.ui.navigation.VideoCardReturn
import moe.kirakira.ui.navigation.imageViewerNavigationMetadata
import moe.kirakira.ui.theme.AppearanceSettings
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun AppNavHost(
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    accountState: SessionState,
    dependencies: AppDependenciesViewModel?,
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
    // One per retiring session in the playback model, so quick switches let every exit finish.
    val exitViewports = remember(playback) { mutableStateListOf<MiniPlayerExitViewport>() }
    fun canKeepMiniPlayer(): Boolean = currentPlaybackSettings?.inAppMiniPlayer == true &&
        playback.showPauseIcon && playback.player != null && !playback.failed

    fun popRoute(route: NavKey) {
        if (route is VideoRoute && backStack.lastOrNull() == route && playback.videoId == route.videoId) {
            if (viewportState.exitingVideoId == route.videoId) return
            val keepMiniPlayer = viewportState.backPreviewFor(route)?.toMiniPlayer != false && canKeepMiniPlayer()
            if (!keepMiniPlayer) {
                // The retained page carries the return on its own, so the source page takes input
                // right away and another card can open while this one lands. A revealed video page
                // would take over the session, so that return still pops when it finishes.
                val source = backStack.getOrNull(backStack.lastIndex - 1)
                viewportState.cardReturn = VideoCardReturn(route.videoId, source)
                if (source !is VideoRoute) navigation.popFrom(route)
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
        val newlyOpened = navigation.consumeVideoOpening(video)
        if (video != null && contentRepository != null) {
            if (viewportState.exitingVideoId != video.videoId) viewportState.cardReturn = null
            playback.showVideo(video.videoId, newlyOpened)
        } else if (viewportState.exitingVideoId == null) {
            playback.leaveVideo(playbackSettings?.inAppMiniPlayer == true)
        }
        if (sessionChanged) viewportState.cardReturn = null
        viewportState.canBackToMiniPlayer = ::canKeepMiniPlayer
        if (sessionChanged) playback.cancelAutoplay()
        if (playbackSettings?.inAppMiniPlayer != true) {
            if (playback.miniPlayer) playback.closeMiniPlayer()
            playback.miniPlayerExits.filterNot { it.toCard }.forEach(playback::finishMiniPlayerExit)
        }
        val exits = playback.miniPlayerExits
        exitViewports.removeAll { viewport -> exits.none { it === viewport.session } }
        exits.filter { exit -> exitViewports.none { it.session === exit } }.forEach(playback::finishMiniPlayerExit)
        if (sessionChanged || (backStack.none { it is VideoRoute } && !playback.miniPlayer &&
                viewportState.exitingVideoId == null)) {
            viewportState.cards.clear()
        }
    }

    SessionFeedback(
        state = accountState,
        onDismissError = onDismissSessionError,
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
        viewportState.cards.onOpenVideo = openVideo@{ videoId, origin, open ->
            val previousOrigin = viewportState.cards.origin
            val cardReturn = viewportState.cardReturn?.takeIf { it.videoId == playback.videoId }
            val returning = cardReturn?.videoId
            open()
            if (backStack.lastOrNull() != VideoRoute(videoId)) return@openVideo
            val matchingOrigin = if (returning == videoId) previousOrigin else {
                exitViewports.firstOrNull { it.session.toCard && it.session.videoId == videoId }?.origin
            }
            viewportState.cards.select(matchingOrigin?.takeIf { it.key == origin?.key } ?: origin)
            if (returning == videoId) {
                // Reopening the card being returned to reverses the same flight from where it is.
                viewportState.cardReturn = null
            } else if (returning != null) {
                playback.beginCardExit()?.let { exit ->
                    exitViewports += MiniPlayerExitViewport(
                        session = exit,
                        origin = previousOrigin?.takeIf { it.videoId == exit.videoId },
                        pageViewport = viewportState.pageViewport?.takeIf { it.videoId == exit.videoId },
                        mainNavigationSize = chromeSize,
                        cardReturn = cardReturn,
                    )
                }
                viewportState.cardReturn = null
            } else {
                if (playback.videoId != videoId && !imageViewerActive && !viewportState.navigatingPage) {
                    playback.beginMiniPlayerExit()?.let { exit ->
                        exitViewports += MiniPlayerExitViewport(
                            session = exit,
                            origin = previousOrigin?.takeIf { it.videoId == exit.videoId },
                            pageViewport = viewportState.pageViewport?.takeIf { it.videoId == exit.videoId },
                            mainNavigationSize = chromeSize,
                        )
                    }
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
                .completeVideoZoomTap(viewportState.cards) {
                    val source = viewportState.cardReturn?.sourceRoute
                    source != null && source !is VideoRoute && backStack.lastOrNull() == source
                }
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .onGloballyPositioned { viewportState.windowOrigin = it.positionInWindow() },
        ) {
            ActivityNavDisplay(
                backStack = backStack,
                predictiveBackEnabled = appearance.predictiveBackEnabled,
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
                    .videoZoomBackgroundEffect(viewportState, depthEnabled = appearance.backgroundDepthEnabled)
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
                        appearance, onAppearanceChange,
                        playbackSettingsModel, playbackSettings, danmakuSettingsModel, danmakuSettings,
                    )
                    registerAccountEntries(
                        this, navigation, authRepository, accountState, onSelectAccount, onRemoveAccount,
                        appearance.predictiveBackEnabled,
                        onCredentialSaveFailure = showFeedback,
                    )
                },
            )
            val foregroundVideoId = (currentRoute as? VideoRoute)?.videoId
                ?: viewportState.exitingVideoId
                ?: playback.videoId?.takeIf { playback.miniPlayer }
            // A covered route can share an ID with a retiring session. Keep the exit mounted until
            // it releases its player, even if that route becomes the only video left in the stack.
            val liveExits = playback.miniPlayerExits.toSet()
            val outgoingById = exitViewports
                .filter { it.session.videoId != foregroundVideoId && it.session in liveExits }
                .associateBy { it.session.videoId }
            val stackVideos = backStack.filterIsInstance<VideoRoute>()
            val retainedVideoId = foregroundVideoId
                ?: stackVideos.lastOrNull()?.videoId?.takeUnless(outgoingById::containsKey)
            val displayedVideoIds = (listOfNotNull(retainedVideoId) + outgoingById.keys).filter { it > 0 }
            val retainedIds = stackVideos.map { it.videoId }.toSet() + displayedVideoIds
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
                    val outgoing = outgoingById[videoId]
                    val cardReturn = viewportState.cardReturn?.takeIf { it.videoId == videoId }
                    PlaybackHost(
                        playback = playback,
                        videoId = videoId,
                        viewportState = viewportState,
                        backPreview = if (outgoing == null && videoPageActive) viewportState.backPreviewFor(route) else null,
                        sourceOwner = chromeRoute,
                        settings = playbackSettings,
                        videoPageActive = outgoing == null && videoPageActive,
                        imageViewerActive = imageViewerActive,
                        videoPageUnderImage = outgoing == null && videoPageUnderImage,
                        videoPageCovered = outgoing == null && !imageViewerActive &&
                            viewportState.exitingVideoId != videoId && (
                            (!videoPageActive && stackVideos.isNotEmpty()) ||
                                viewportState.coveringPageKeys.isNotEmpty()
                            ),
                        // A card return has left the stack, so it draws above the revealed page, even one
                        // that covers another video; once detached it stays under the video expanding over it.
                        modifier = Modifier
                            .zIndex(
                                when {
                                    outgoing?.session?.toCard == true -> 0.25f
                                    outgoing != null || (playback.miniPlayer && !videoPageActive &&
                                        !videoPageUnderImage && !viewportState.navigatingPage) -> 2f
                                    viewportState.exitingVideoId == videoId -> 1.5f
                                    else -> 0.5f
                                },
                            )
                            .then(
                                // Layers only while a detached return is pushed back with the page.
                                if (outgoing?.session?.toCard == true) {
                                    Modifier.videoZoomBackgroundEffect(
                                        viewportState,
                                        depthEnabled = appearance.backgroundDepthEnabled,
                                        excludingVideoId = videoId,
                                        mirrorEdges = false,
                                    )
                                } else Modifier,
                            ),
                        mainNavigationSize = outgoing?.mainNavigationSize ?: chromeSize,
                        miniPlayerExit = outgoing,
                        hostScale = if (outgoing?.session?.toCard == true) {
                            { viewportState.backgroundScale(appearance.backgroundDepthEnabled, excludingVideoId = videoId) }
                        } else NoHostScale,
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
                            if (cardReturn != null && viewportState.cardReturn === cardReturn) {
                                // Keep the source Scene mounted until the visual return is complete.
                                if (backStack.lastOrNull() == route) navigation.popFrom(route)
                                viewportState.cardReturn = null
                                if (playback.videoId == videoId) playback.leaveVideo(false)
                            } else if (cardReturn != null) {
                                // The flight may land after detachment but before its host recomposes.
                                exitViewports.firstOrNull { it.cardReturn === cardReturn }
                                    ?.session?.let(playback::finishMiniPlayerExit)
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

private val NoHostScale = { 1f }

private const val MAX_VIDEO_ZOOM_BLUR_DP = 24
private const val VIDEO_ZOOM_BLUR_STEPS_PER_PX = 2
private const val MAX_VIDEO_ZOOM_SCRIM_ALPHA = 0.25f

/** The scrim's alpha in 8-bit levels, the finest step the tint can show. */
private const val MAX_VIDEO_ZOOM_SCRIM_STEPS = (MAX_VIDEO_ZOOM_SCRIM_ALPHA * 255).toInt()
private const val MAX_VIDEO_ZOOM_BACKGROUND_SCALE = 0.1f

/** Scale steps per unit: under half a pixel of edge movement per step on a phone-width window. */
private const val VIDEO_ZOOM_SCALE_STEPS = 4096

/**
 * Pushes the page behind a card or mini player zoom into the distance. Android 12+ dims it; with
 * [depthEnabled] it also blurs there and, on Android 13+, shrinks it while mirroring its edges
 * into the uncovered margin, like iOS's background extension effect.
 *
 * The shrink is a layer transform, so hit testing and coordinates follow what is drawn: the page
 * takes taps while a card return flies, and the flight lands where the card is shown. Blur, dim and
 * the mirrored margin only change the drawing, on an outer layer around the scaled one.
 *
 * A layer that floats above the page but belongs to the background, such as a card return the next
 * video now expands over, passes its [excludingVideoId] and no [mirrorEdges]: it takes the same push
 * except its own, about the same centre so it stays on its shrunken card.
 */
@Composable
private fun Modifier.videoZoomBackgroundEffect(
    viewportState: PlaybackViewportState,
    depthEnabled: Boolean,
    excludingVideoId: Int? = null,
    mirrorEdges: Boolean = true,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val density = LocalDensity.current.density
    val maxBlurSteps = ceil(MAX_VIDEO_ZOOM_BLUR_DP * density * VIDEO_ZOOM_BLUR_STEPS_PER_PX).toInt()
    val blurEffects = remember(maxBlurSteps) { arrayOfNulls<BlurEffect>(maxBlurSteps + 1) }
    val scrimFilters = remember { arrayOfNulls<ColorFilter>(MAX_VIDEO_ZOOM_SCRIM_STEPS + 1) }
    val edgeMirror = remember(mirrorEdges) {
        if (mirrorEdges && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) VideoZoomEdgeMirror() else null
    }
    fun strength() = viewportState.backgroundStrength(excludingVideoId).coerceAtMost(1f)

    fun shrink() = viewportState.backgroundScale(depthEnabled, excludingVideoId)

    // Quantized steps, so the effect layer only updates when a visible step changes. Sub-pixel radius
    // steps, rounded rather than ceiled: the last visible step is half a pixel, so the tail of the
    // restore fades out instead of dropping a whole dp of blur in one frame.
    val blurStep by remember(viewportState, excludingVideoId, maxBlurSteps) {
        derivedStateOf(structuralEqualityPolicy()) { (maxBlurSteps * strength()).roundToInt() }
    }
    val scrimStep by remember(viewportState, excludingVideoId) {
        derivedStateOf(structuralEqualityPolicy()) { (MAX_VIDEO_ZOOM_SCRIM_STEPS * strength()).roundToInt() }
    }
    return graphicsLayer {
        val blur = blurStep
        val scrim = scrimStep
        // Only the mirrored margin depends on the continuous scale; it alone updates every frame.
        val scale = if (edgeMirror != null) shrink() else 1f
        val blurRadius = blur.toFloat() / VIDEO_ZOOM_BLUR_STEPS_PER_PX
        renderEffect = when {
            !depthEnabled -> null
            edgeMirror != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && scale < 1f ->
                edgeMirror.create(size.width, size.height, scale, blurRadius)
            blur == 0 -> null
            else -> blurEffects[blur] ?: BlurEffect(blurRadius, blurRadius).also { blurEffects[blur] = it }
        }
        colorFilter = if (scrim == 0) null else scrimFilters[scrim] ?: ColorFilter.tint(
            color = Color.Black.copy(alpha = scrim / 255f),
            blendMode = BlendMode.SrcAtop,
        ).also { scrimFilters[scrim] = it }
    }.graphicsLayer {
        val scale = shrink()
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The scale the background's shrink applies about the window centre, also read by a detached return
 * to place itself on its card: one source for the transform and its inverse.
 */
private fun PlaybackViewportState.backgroundScale(depthEnabled: Boolean, excludingVideoId: Int? = null): Float {
    if (!depthEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1f
    if (backgroundStrength(excludingVideoId) == 0f) return 1f
    val scale = 1f - MAX_VIDEO_ZOOM_BACKGROUND_SCALE * backgroundDepth(excludingVideoId)
    // Quantized here, once, so the layer, its mirrored margin and a detached return's inverse all see
    // the same value: the margin never seams against the page, and an unchanged step reuses its effect.
    return (scale * VIDEO_ZOOM_SCALE_STEPS).roundToInt() / VIDEO_ZOOM_SCALE_STEPS.toFloat()
}

/**
 * Fills the margin a shrunken page leaves by mirroring the page across its new edges, then blurs
 * the result so the reflection reads as a soft continuation rather than a seam. The page itself is
 * scaled by its own layer; this only paints the margin, so input is unaffected.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class VideoZoomEdgeMirror {
    private val shader = RuntimeShader(
        """
        uniform shader content;
        uniform float2 size;
        uniform float scale;

        half4 main(float2 position) {
            float2 page = size * scale;
            float2 origin = (size - page) * 0.5;
            // Reflect once across each edge of the scaled page; the margin never exceeds the page.
            float2 local = abs(position - origin);
            local = page - abs(page - local);
            return content.eval(clamp(origin + local, origin + 0.5, origin + page - 0.5));
        }
        """.trimIndent(),
    )

    private var last: androidx.compose.ui.graphics.RenderEffect? = null
    private var lastKey = floatArrayOf(-1f, -1f, -1f, -1f)

    /** Reuses the previous effect while the quantized scale, blur and size are unchanged. */
    fun create(width: Float, height: Float, scale: Float, blurRadius: Float): androidx.compose.ui.graphics.RenderEffect {
        val key = lastKey
        last?.let { if (key[0] == width && key[1] == height && key[2] == scale && key[3] == blurRadius) return it }
        key[0] = width
        key[1] = height
        key[2] = scale
        key[3] = blurRadius
        return build(width, height, scale, blurRadius).also { last = it }
    }

    private fun build(width: Float, height: Float, scale: Float, blurRadius: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("scale", scale)
        val mirror = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        if (blurRadius <= 0f) return mirror.asComposeRenderEffect()
        val blur = android.graphics.RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
        return android.graphics.RenderEffect.createChainEffect(blur, mirror).asComposeRenderEffect()
    }
}
