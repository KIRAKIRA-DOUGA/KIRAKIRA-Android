package moe.kirakira.ui.navigation

import android.view.animation.AnimationUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.collectLatest
import moe.kirakira.R
import moe.kirakira.ui.adaptive.LocalAppWindowLayout
import moe.kirakira.ui.adaptive.LocalRailPageFrame
import moe.kirakira.ui.adaptive.LocalWindowPane
import moe.kirakira.ui.adaptive.WindowPane
import moe.kirakira.ui.adaptive.windowCornerRadius
import moe.kirakira.ui.components.illustration.SettingsSearchLogo
import moe.kirakira.ui.components.scaffold.LocalPageBackButtonVisible
import moe.kirakira.ui.components.scaffold.LocalStatusBarScrollToTopEnabled
import moe.kirakira.ui.components.status.ContentUnavailableState
import moe.kirakira.ui.components.status.ContentUnavailableView
import moe.kirakira.ui.components.video.LocalVideoZoomOwner
import moe.kirakira.ui.theme.ThemeColorDefaults
import moe.kirakira.ui.theme.barSurfaceLayer

/** Entry metadata marking the destination that zooms out of a video card. */
internal const val VIDEO_ZOOM_METADATA = "moe.kirakira.video.zoom"

/** Tracks the committed destination, including while another entry is being previewed. */
internal val LocalDestinationActive = compositionLocalOf { true }

internal class ActivityStartRail<T : Any>(
    val width: Dp,
    val showsFor: (T) -> Boolean,
    val enterFromBottom: Boolean?,
    val content: @Composable (route: T, interactive: Boolean) -> Unit,
)

private val LocalSceneOwnsStartRail = compositionLocalOf { false }
private val LocalRetainActivityPageContent = compositionLocalOf { true }
private val LocalSettingsDirectorySurface = compositionLocalOf<Modifier> { Modifier }

/**
 * Navigation 3 owns entries, saved state and ordinary navigation. A two-entry Scene keeps both
 * pages alive through AOSP's gesture and post-commit phases before changing the back stack.
 */
@Composable
internal fun <T : Any> ActivityNavDisplay(
    backStack: List<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onBackRequested: () -> Boolean = { true },
    canNavigateBack: () -> Boolean = { true },
    predictiveBackEnabled: Boolean = false,
    onImageBack: (T) -> Unit = { onBack() },
    onImageDismiss: (T) -> Unit = onImageBack,
    videoZoomState: PlaybackViewportState? = null,
    videoPageUnderImage: Boolean = false,
    startRail: ActivityStartRail<T>? = null,
    settingsPanes: SettingsPaneLayout? = null,
    settingsRoot: T? = null,
    fullPageSettings: Set<T> = emptySet(),
    entryProvider: (T) -> NavEntry<T>,
) {
    val ordinaryMotion = rememberNavigationMotion()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val motion = remember(context, scope) {
        val interpolator = AnimationUtils.loadInterpolator(context, activityInterpolatorResource())
        PredictiveBackMotion(scope) { interpolator.getInterpolation(it) }
    }
    val currentEntryProvider by rememberUpdatedState(entryProvider)
    val currentRailState = rememberUpdatedState(startRail)
    val topDestination by rememberUpdatedState(backStack.lastOrNull())
    var composedImageEntries by remember { mutableIntStateOf(0) }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Navigation 3 caches entries by back stack, so their content must read the latest provider.
        entryProvider = { route ->
            val routeEntry = currentEntryProvider(route)
            val zoomPlaceholder = routeEntry.metadata[VIDEO_ZOOM_METADATA] == true
            NavEntry(navEntry = routeEntry) { key ->
                val pane = LocalSettingsPane.current
                CompositionLocalProvider(
                    LocalVideoZoomOwner provides key,
                    LocalDestinationActive provides if (pane == SettingsPane.DIRECTORY) {
                        LocalSettingsDirectoryActive.current
                    } else key == topDestination,
                ) {
                    val configuredRail = currentRailState.value.takeIf { pane == null }
                    val rail = configuredRail?.takeIf { it.showsFor(key) }
                    if (routeEntry.usesImageTransition) {
                        // Keep the source rail behind the viewer until its shared transition finishes.
                        DisposableEffect(key) {
                            composedImageEntries++
                            onDispose { composedImageEntries-- }
                        }
                    }
                    if (!LocalRetainActivityPageContent.current) return@CompositionLocalProvider
                    val entry = currentEntryProvider(key)
                    if (zoomPlaceholder) entry.Content()
                    else WindowPane(
                        // Nested flows already use the parent pane's local coordinates.
                        pane = if (pane == null && LocalWindowPane.current == null) {
                            LocalAppWindowLayout.current.pagePane
                        } else null,
                        modifier = if (pane == SettingsPane.DIRECTORY) LocalSettingsDirectorySurface.current else Modifier,
                    ) {
                        val gutter = if (pane != null) LocalNavigationPageStartGutter.current else rail?.width ?: 0.dp
                        CompositionLocalProvider(
                            LocalNavigationPageStartGutter provides gutter,
                            LocalPageBackButtonVisible provides (rail == null && LocalPageBackButtonVisible.current),
                            LocalRailPageFrame provides (rail != null),
                        ) {
                            entry.Content()
                        }
                        // A predictive preview or shared image transition owns the whole source page.
                        if (rail != null && LocalSceneOwnsStartRail.current) {
                            NavigationPage { rail.content(key, false) }
                        }
                    }
                }
            }
        },
    )
    val currentEntriesState = rememberUpdatedState(entries)
    val imageActive = entries.last().usesImageTransition
    val imagePredictiveBack = imageActive && predictiveBackEnabled
    val zoomRoute = entries.last().metadata[VIDEO_ZOOM_METADATA] == true
    // Keep outgoing ordinary pages above the retained video until their Scene is disposed.
    val videoCovered = !imageActive && (
        (!zoomRoute && entries.any { it.metadata[VIDEO_ZOOM_METADATA] == true }) ||
            videoZoomState?.coveringPageKeys?.isNotEmpty() == true
        )
    val videoCoveredState = rememberUpdatedState(videoCovered)
    val videoSourceCoveredState = rememberUpdatedState(videoCovered || videoPageUnderImage)
    val zoomPreview = zoomRoute && videoZoomState?.backPreviewActive == true
    val previewKey = if (zoomPreview) entries.last().contentKey else motion.closingKey
    val returningToVideo = !zoomRoute && motion.closingKey == entries.last().contentKey &&
        entries.getOrNull(entries.lastIndex - 1)?.metadata?.get(VIDEO_ZOOM_METADATA) == true
    val readVideoReturn = remember(motion) { { motion.frame.pageTransform(true) } }
    SideEffect {
        videoZoomState?.navigationReturn = readVideoReturn.takeIf { returningToVideo }
    }
    val fullPageKeys = remember(fullPageSettings) {
        fullPageSettings.mapTo(mutableSetOf()) { currentEntryProvider(it).contentKey }
    }
    val strategy = remember(motion, previewKey, zoomPreview, videoZoomState, settingsPanes, settingsRoot, fullPageKeys) {
        ActivitySceneStrategy(
            motion, previewKey, currentEntriesState, zoomPreview, videoZoomState,
            videoCoveredState, videoSourceCoveredState,
            settingsPanes, settingsRoot, fullPageKeys, currentRailState,
        )
    }
    val sceneState = rememberSceneState(entries, listOf(strategy), onBack = onBack)
    val topRoute = backStack.last()
    // Only opt into Navigation 3's seekable image return while the setting is enabled.
    // Changing the setting retires the previous gesture state and its handler.
    val displayState = key(if (imagePredictiveBack) entries.last().contentKey else OrdinaryNavigationEventKey) {
        val state = rememberNavigationEventState(
            currentInfo = SceneInfo(sceneState.currentScene),
            backInfo = sceneState.previousScenes.asReversed().map { SceneInfo(it) },
        )
        NavigationBackHandler(
            state = state,
            isBackEnabled = imagePredictiveBack && entries.size > 1,
            onBackCompleted = {
                // Keying the handler retires an old gesture participant when another instance opens.
                if (imagePredictiveBack && backStack.size > 1 && backStack.lastOrNull() == topRoute) onImageBack(topRoute)
            },
        )
        state
    }
    val dismissNavigation = remember(if (imageActive) entries.last().contentKey else null) {
        ImageDismissNavigation()
    }
    val dismissEventState = key(dismissNavigation) {
        rememberImageDismissEventState(
            navigation = dismissNavigation,
            sceneState = sceneState,
            enabled = imageActive && entries.size > 1,
            onDismiss = {
                if (imageActive && backStack.size > 1 && backStack.lastOrNull() == topRoute) onImageDismiss(topRoute)
            },
        )
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    val currentScene = sceneState.currentScene as ActivityScene<T>
    val currentPage = currentScene.page
    val previousPage = (sceneState.previousScenes.lastOrNull() as? ActivityScene<T>)?.page
    val detailBack = currentPage.sharesSettingsDirectory(previousPage)
    val window = LocalAppWindowLayout.current
    val pagePane = window.pagePane
    LaunchedEffect(settingsPanes, fullPageKeys, pagePane) {
        if (motion.completedPop == null) motion.reset()
    }
    ActivityBackHandler(
        motion = motion,
        sceneState = sceneState,
        predictiveBackEnabled = predictiveBackEnabled,
        enabled = entries.size > 1 && !imagePredictiveBack,
        onBackRequested = onBackRequested,
        canNavigateBack = canNavigateBack,
        onBack = onBack,
        onStartGesture = { event ->
            val current = entries.lastOrNull()
            val previous = entries.getOrNull(entries.lastIndex - 1)
            if (current != null && previous != null) {
                motion.start(
                    closing = current.contentKey,
                    entering = previous.contentKey,
                    event = event,
                    size = if (detailBack && settingsPanes != null) {
                        IntSize(settingsPanes.detail.width, settingsPanes.detail.height)
                    } else size,
                    density = density,
                    cornerRadius = view.windowCornerRadius(),
                    darkTheme = darkTheme,
                )
            }
        },
        zoomState = videoZoomState.takeIf { zoomRoute },
    )
    val previewingTop = previewKey != null && previewKey == entries.last().contentKey
    val previousRoute = backStack.getOrNull(backStack.lastIndex - 1)
    val railPage = currentScene.underlay?.takeIf { zoomRoute } ?: currentPage
    val railRoute = previousRoute?.takeIf { zoomRoute } ?: topRoute
    val inSettings = railPage.directory != null
    val currentHasRail = if (inSettings) settingsPanes?.rail == true else startRail?.showsFor(railRoute) == true
    val previousHasRail = if (previousPage?.directory != null) settingsPanes?.rail == true
        else previousRoute?.let { startRail?.showsFor(it) } == true
    val sceneOwnsRail = imageActive || composedImageEntries > 0 ||
        (previewingTop && !zoomRoute && currentHasRail != previousHasRail)
    // During an interruption, AnimatedContent also requests specs for scenes still on screen.
    // The committed Rail action supplies the direction for all of those transforms.
    fun railTransition(isPop: Boolean) = currentRailState.value?.enterFromBottom?.let { ordinaryMotion.vertical(it, isPop) }

    SharedTransitionLayout(modifier = modifier.fillMaxSize().zIndex(if (videoCovered || videoPageUnderImage) 1f else 0f)) {
        val imageSizes = remember { ImageTransitionSizes() }
        CompositionLocalProvider(
            LocalImageDismissNavigation provides dismissNavigation.takeIf { imageActive },
            LocalImageSharedScope provides this,
            LocalImageTransitionSizes provides imageSizes,
            LocalSceneOwnsStartRail provides sceneOwnsRail,
            LocalSettingsPane provides null,
        ) {
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = if (imageActive && dismissNavigation.active) dismissEventState else displayState,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged {
                        if (size != it && size != IntSize.Zero) motion.reset()
                        size = it
                    },
                // Image entries override these defaults through their Scene metadata.
                transitionSpec = {
                    if (targetState.usesVideoZoom || initialState.destinationKey == targetState.destinationKey) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else railTransition(isPop = false) ?: ordinaryMotion.forward
                },
                popTransitionSpec = {
                    if (initialState.usesVideoZoom ||
                        initialState.destinationKey == targetState.destinationKey ||
                        motion.completedPop == (initialState.destinationKey to targetState.destinationKey)) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        railTransition(isPop = true) ?: ordinaryMotion.backward
                    }
                },
                predictivePopTransitionSpec = {
                    EnterTransition.None togetherWith ExitTransition.None
                },
            )
        }
        if (!sceneOwnsRail) {
            val placement = startRail?.takeIf { currentHasRail }?.let {
                StartRailPlacement(
                    it, if (inSettings && settingsRoot != null) settingsRoot else railRoute,
                    if (inSettings) settingsPanes?.directory else pagePane,
                )
            }
            StartRailOverlay(
                placement = placement,
                visible = placement != null && (!zoomRoute ||
                    (!videoSourceCoveredState.value && videoZoomState?.exitingVideoId != null)),
                interactive = !zoomRoute && previewKey == null,
                videoZoomState = videoZoomState,
                seekVideoBack = zoomRoute && placement != null && !videoSourceCoveredState.value,
            )
        }
    }
}

private data class StartRailPlacement<T : Any>(
    val rail: ActivityStartRail<T>,
    val route: T,
    val pane: IntRect?,
)

@Composable
private fun <T : Any> StartRailOverlay(
    placement: StartRailPlacement<T>?,
    visible: Boolean,
    interactive: Boolean,
    videoZoomState: PlaybackViewportState?,
    seekVideoBack: Boolean,
) {
    val reveal = remember { Animatable(if (visible) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var lastPlacement by remember { mutableStateOf<StartRailPlacement<T>?>(null) }
    if (visible && placement != null) SideEffect { lastPlacement = placement }
    LaunchedEffect(visible, seekVideoBack, videoZoomState, spec) {
        snapshotFlow { if (seekVideoBack) videoZoomState?.backProgress else null }.collectLatest { gesture ->
            if (gesture != null) reveal.snapTo(gesture.coerceIn(0f, 1f))
            else {
                val target = if (visible) 1f else 0f
                if (reveal.value != target) reveal.animateTo(target, spec, initialVelocity = 0f)
                if (!visible) lastPlacement = null
            }
        }
    }
    val onScreen by remember { derivedStateOf { reveal.value > 0f } }
    if (!visible && !onScreen) return
    val shown = placement ?: lastPlacement ?: return
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val window = LocalAppWindowLayout.current
    WindowPane(shown.pane, Modifier.zIndex(1f)) {
        Box(Modifier.graphicsLayer {
            val width = size.width
            val left = shown.pane?.left ?: 0
            val right = shown.pane?.right ?: window.size.width
            val frame = videoZoomState?.sourceRailFrame?.invoke()
            // The video is above the rail. Keep the whole icon inside the exposed strip.
            val space = when {
                frame == null || frame.right <= left || frame.left >= right -> width
                ltr -> frame.left - left
                else -> right - frame.right
            }
            val exposed = (width * reveal.value).coerceIn(0f, space.coerceIn(0f, width))
            translationX = (width - exposed) * if (ltr) -1f else 1f
            alpha = if (width > 0f) exposed / width else 0f
        }) {
            shown.rail.content(shown.route, visible && interactive && !reveal.isRunning)
        }
    }
}

private data object OrdinaryNavigationEventKey

private data class SettingsSceneKey(val directoryKey: Any)

private val Scene<*>.destinationKey: Any
    get() = entries.last().contentKey

private val Scene<*>.usesVideoZoom: Boolean
    get() = entries.lastOrNull()?.metadata?.get(VIDEO_ZOOM_METADATA) == true

private data class ActivityScenePage<T : Any>(
    val top: NavEntry<T>,
    val directory: NavEntry<T>? = null,
    val settingsPath: List<Any> = emptyList(),
    val settingsCategoryIndex: Int? = null,
) {
    fun sharesSettingsDirectory(other: ActivityScenePage<*>?): Boolean =
        directory != null && directory.contentKey == other?.directory?.contentKey
}

private class ActivitySceneStrategy<T : Any>(
    private val motion: PredictiveBackMotion,
    private val previewKey: Any?,
    private val currentEntries: State<List<NavEntry<T>>>,
    private val zoomPreview: Boolean,
    private val videoZoomState: PlaybackViewportState?,
    private val videoCovered: State<Boolean>,
    private val videoSourceCovered: State<Boolean>,
    private val settingsPanes: SettingsPaneLayout?,
    private val settingsRoot: T?,
    private val fullPageKeys: Set<Any>,
    private val rail: State<ActivityStartRail<T>?>,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> {
        val page = scenePage(entries)
        val previousEntries = entries.dropLast(1)
        val needsUnderlay = page.top.contentKey == previewKey || page.top.metadata[VIDEO_ZOOM_METADATA] == true
        return ActivityScene(
            page = page,
            underlay = if (needsUnderlay && previousEntries.isNotEmpty()) scenePage(previousEntries) else null,
            previousEntries = previousEntries,
            motion = motion,
            currentEntries = currentEntries,
            zoomPreview = zoomPreview,
            videoZoomState = videoZoomState,
            videoCovered = videoCovered,
            videoSourceCovered = videoSourceCovered,
            settingsPanes = settingsPanes,
            settingsRoot = settingsRoot,
            rail = rail,
        )
    }

    private fun scenePage(entries: List<NavEntry<T>>): ActivityScenePage<T> {
        val top = entries.last()
        if (settingsPanes == null || top.contentKey in fullPageKeys) return ActivityScenePage(top)
        val root = entries.indexOfLast { it.metadata[SettingsPaneKey] == SettingsPane.DIRECTORY }
        val directory = entries.getOrNull(root) ?: return ActivityScenePage(top)
        if (entries.subList(root + 1, entries.size).any {
                val role = it.metadata[SettingsPaneKey]
                role != SettingsPane.CATEGORY && role != SettingsPane.SUBPAGE
            }) {
            return ActivityScenePage(top)
        }
        return ActivityScenePage(
            top = top,
            directory = directory,
            settingsPath = entries.subList(root, entries.size).map { it.contentKey },
            settingsCategoryIndex = entries.getOrNull(root + 1)?.metadata?.get(SettingsCategoryIndexKey),
        )
    }
}

private data class ActivityScene<T : Any>(
    val page: ActivityScenePage<T>,
    val underlay: ActivityScenePage<T>?,
    override val previousEntries: List<NavEntry<T>>,
    val motion: PredictiveBackMotion,
    val currentEntries: State<List<NavEntry<T>>>,
    val zoomPreview: Boolean,
    val videoZoomState: PlaybackViewportState?,
    val videoCovered: State<Boolean>,
    val videoSourceCovered: State<Boolean>,
    val settingsPanes: SettingsPaneLayout?,
    val settingsRoot: T?,
    val rail: State<ActivityStartRail<T>?>,
) : Scene<T> {
    // The directory owns the outer Scene throughout detail navigation, including the empty pane.
    override val key: Any = page.directory?.let { SettingsSceneKey(it.contentKey) } ?: page.top.contentKey
    override val entries: List<NavEntry<T>> =
        listOfNotNull(underlay?.directory, underlay?.top, page.directory, page.top).distinctBy { it.contentKey }

    override val content: @Composable () -> Unit = {
        val destination = page.top.contentKey
        DisposableEffect(destination, motion) {
            onDispose { motion.onDestinationDisposed(destination) }
        }
        val coversVideo = !usesVideoZoom && !entries.last().usesImageTransition &&
            previousEntries.lastOrNull()?.metadata?.get(VIDEO_ZOOM_METADATA) == true
        DisposableEffect(destination, coversVideo, videoZoomState) {
            if (coversVideo) videoZoomState?.coveringPageKeys?.put(destination, Unit)
            onDispose {
                if (coversVideo) videoZoomState?.coveringPageKeys?.remove(destination)
            }
        }
        val preview = underlay != null && (motion.closingKey == destination || zoomPreview)
        val zoomLayer = underlay != null && usesVideoZoom
        val owner = rememberLifecycleOwner(
            maxLifecycle = if (preview || zoomLayer) Lifecycle.State.STARTED else Lifecycle.State.RESUMED,
        )
        val lifecycleState by owner.lifecycle.currentStateAsState()
        val visibility = LocalNavAnimatedContentScope.current.transition
        if (usesVideoZoom && videoZoomState != null) {
            val ordinary by rememberUpdatedState(videoCovered.value && motion.completedPop?.second != destination)
            val ordinaryMotion = rememberNavigationMotion()
            val offset = visibility.animateFloat(
                transitionSpec = { if (ordinary) ordinaryMotion.pageSlideSpec else snap() },
                label = "retained video page slide",
            ) { state ->
                if (ordinary && state != EnterExitState.Visible) -ordinaryMotion.pageOffset else 0f
            }
            val page = remember(visibility, offset) {
                PlaybackNavigationPage(
                    transform = { NavigationPageTransform(x = offset.value) },
                    isTransitioning = {
                        ordinary && (visibility.isRunning || visibility.currentState != visibility.targetState)
                    },
                )
            }
            DisposableEffect(videoZoomState, page) {
                videoZoomState.navigationPage = page
                onDispose {
                    if (videoZoomState.navigationPage === page) videoZoomState.navigationPage = null
                }
            }
        }
        val stack = currentEntries.value
        val topKey = stack.lastOrNull()?.contentKey
        val imageScene = entries.last().usesImageTransition
        val ordinaryPop = !preview && !imageScene && stack.none { it.contentKey == destination } &&
            previousEntries.any { it.contentKey == topKey } && motion.completedPop?.first != destination
        val exitOpacity = visibility.animateFloat(
            transitionSpec = { if (ordinaryPop) activityCloseFadeSpec() else snap() },
            label = "scene content retention",
        ) { if (it == EnterExitState.PostExit) 0f else 1f }
        // Keep the slide running, but retire invisible content so hits reach the incoming page.
        val retainContent by remember(ordinaryPop, exitOpacity) {
            derivedStateOf { !ordinaryPop || exitOpacity.value > 0f }
        }
        val interactive = !preview && !zoomLayer && topKey == destination &&
            lifecycleState.isAtLeast(Lifecycle.State.STARTED) && visibility.targetState == EnterExitState.Visible &&
            (!imageScene || (
                lifecycleState == Lifecycle.State.RESUMED && visibility.currentState == EnterExitState.Visible
                ))
        val parentScrollToTopEnabled = LocalStatusBarScrollToTopEnabled.current
        val blockInput = retainContent && !interactive
        CompositionLocalProvider(
            LocalLifecycleOwner provides owner,
            LocalRetainActivityPageContent provides retainContent,
            LocalStatusBarScrollToTopEnabled provides (
                parentScrollToTopEnabled && interactive && lifecycleState == Lifecycle.State.RESUMED &&
                    visibility.currentState == EnterExitState.Visible && !visibility.isRunning
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        // Keep the source laid out for return geometry. Skip drawing it when
                        // the retained video covers it or a covering Scene needs to reveal the video.
                        if (zoomLayer &&
                            (videoSourceCovered.value || videoZoomState?.videoCoversSource?.value == true)
                        ) {
                            return@drawWithContent
                        }
                        drawContent()
                    }
                    .blockNavigationInput(blockInput),
            ) {
                if (zoomLayer) {
                    RenderPage(checkNotNull(underlay), interactive = false)
                    RenderPage(page, interactive = false)
                } else if (preview && !page.sharesSettingsDirectory(underlay)) {
                    BackPreview(
                        motion,
                        entering = { RenderPage(checkNotNull(underlay), interactive = false) },
                        closing = { RenderPage(page, interactive = false) },
                    )
                } else {
                    RenderPage(
                        page,
                        interactive,
                        previewDetail = underlay.takeIf { preview },
                    )
                }
            }
        }
    }

    @Composable
    private fun RenderPage(
        page: ActivityScenePage<T>,
        interactive: Boolean,
        previewDetail: ActivityScenePage<T>? = null,
    ) {
        if (page.directory == null) {
            page.top.Content()
        } else {
            NavigationPage {
                CompositionLocalProvider(LocalNavigationPageTransform provides { NavigationPageTransform() }) {
                    SettingsPagePanes(page, interactive) {
                        Box(Modifier.fillMaxSize().background(ThemeColorDefaults.settingsBackgroundColor())) {
                            if (previewDetail == null) SettingsDetailContent(page, interactive)
                            else BackPreview(
                                motion,
                                entering = { SettingsDetail(previewDetail, isActive = false) },
                                closing = { SettingsDetail(page, isActive = false) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsPagePanes(
        page: ActivityScenePage<T>,
        interactive: Boolean,
        detail: @Composable () -> Unit,
    ) {
        val directory = checkNotNull(page.directory)
        val panes = checkNotNull(settingsPanes)
        val rail = rail.value.takeIf { panes.rail }
        val adjacent = panes.adjacent
        // Let the directory shadow extend over the adjoining detail, leaving a real hinge clear.
        val directorySurface = if (adjacent) {
            Modifier.barSurfaceLayer().background(ThemeColorDefaults.settingsBackgroundColor())
        } else Modifier
        WindowPane(
            panes.directory,
            modifier = if (adjacent) Modifier.zIndex(1f) else Modifier,
            clipContent = !adjacent,
        ) {
            CompositionLocalProvider(
                LocalSettingsPane provides SettingsPane.DIRECTORY,
                LocalSettingsDirectoryActive provides interactive,
                LocalSettingsDirectorySurface provides directorySurface,
                LocalNavigationPageStartGutter provides (rail?.width ?: 0.dp),
                LocalPageBackButtonVisible provides (rail == null),
            ) {
                directory.Content()
            }
            if (rail != null && settingsRoot != null && LocalSceneOwnsStartRail.current) {
                rail.content(settingsRoot, false)
            }
        }
        WindowPane(panes.detail, content = detail)
    }

    @Composable
    private fun SettingsDetailContent(page: ActivityScenePage<T>, interactive: Boolean) {
        val ordinaryMotion = rememberNavigationMotion()
        val statusBarScrollToTopEnabled = LocalStatusBarScrollToTopEnabled.current
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it.top.contentKey },
            transitionSpec = {
                val initialCategory = initialState.settingsCategoryIndex
                val targetCategory = targetState.settingsCategoryIndex
                val transition = if (initialCategory != null && targetCategory != null &&
                    initialCategory != targetCategory
                ) {
                    val enterFromBottom = targetCategory > initialCategory
                    ordinaryMotion.vertical(enterFromBottom, isPop = !enterFromBottom)
                } else if (targetState.top.contentKey in initialState.settingsPath) {
                    ordinaryMotion.backward
                } else {
                    ordinaryMotion.forward
                }
                ContentTransform(
                    targetContentEnter = transition.targetContentEnter,
                    initialContentExit = transition.initialContentExit,
                    // Keep the same stacking order when an outgoing detail becomes the target again.
                    targetContentZIndex = (targetCategory ?: -1) +
                        if (targetState.top.metadata[SettingsPaneKey] == SettingsPane.SUBPAGE) 0.5f else 0f,
                    sizeTransform = null,
                )
            },
            label = "settings detail",
        ) { detail ->
            val selected = detail.top.contentKey == page.top.contentKey
            // An outgoing outer Scene only owns its own detail, including when resizing.
            if (selected || interactive) {
                val owner = rememberLifecycleOwner(
                    maxLifecycle = if (selected) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
                )
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalStatusBarScrollToTopEnabled provides (selected && statusBarScrollToTopEnabled),
                ) {
                    Box(Modifier.fillMaxSize().blockNavigationInput(!selected)) {
                        SettingsDetail(detail, isActive = selected && interactive)
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsDetail(page: ActivityScenePage<T>, isActive: Boolean) {
        if (page.top == page.directory) {
            NavigationPage {
                ContentUnavailableView(
                    state = ContentUnavailableState.EMPTY,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    title = stringResource(R.string.settings_select_item),
                    description = null,
                    icon = { SettingsSearchLogo(isActive = isActive) },
                )
            }
        } else {
            val role = checkNotNull(page.top.metadata[SettingsPaneKey])
            CompositionLocalProvider(
                LocalSettingsPane provides role,
                LocalPageBackButtonVisible provides (role == SettingsPane.SUBPAGE),
            ) {
                page.top.Content()
            }
        }
    }
}

@Composable
internal fun Modifier.blockNavigationFocus(block: Boolean): Modifier {
    val focusManager = LocalFocusManager.current
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(block, focusManager) {
        if (block && hasFocus) focusManager.clearFocus(force = true)
    }
    val focusBoundary = focusProperties { onEnter = { if (block) cancelFocusChange() } }
        .onFocusChanged { hasFocus = it.hasFocus }
        .focusGroup()
    return if (block) focusBoundary.onPreviewKeyEvent { true } else focusBoundary
}

@Composable
internal fun Modifier.blockNavigationInput(block: Boolean): Modifier {
    val focusBoundary = blockNavigationFocus(block)
    if (!block) return focusBoundary
    return focusBoundary
        .clearAndSetSemantics {}
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
}

@Composable
private fun BackPreview(
    motion: PredictiveBackMotion,
    entering: @Composable () -> Unit,
    closing: @Composable () -> Unit,
) {
    val enteringTransform = remember(motion) { { motion.frame.pageTransform(true) } }
    val closingTransform = remember(motion) { { motion.frame.pageTransform(false) } }
    CompositionLocalProvider(LocalNavigationPageTransform provides enteringTransform, content = entering)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color.Black, alpha = motion.frame.scrimAlpha)
    }
    CompositionLocalProvider(LocalNavigationPageTransform provides closingTransform, content = closing)
}
