package moe.kirakira.ui.navigation

import android.view.animation.AnimationUtils
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.collectLatest
import moe.kirakira.ui.adaptive.LocalAppWindowLayout
import moe.kirakira.ui.adaptive.LocalRailPageFrame
import moe.kirakira.ui.adaptive.LocalWindowPane
import moe.kirakira.ui.adaptive.WindowPane
import moe.kirakira.ui.adaptive.windowCornerRadius
import moe.kirakira.ui.components.scaffold.LocalPageBackButtonVisible
import moe.kirakira.ui.components.video.LocalVideoZoomOwner

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

internal val LocalSceneOwnsStartRail = compositionLocalOf { false }
internal val LocalRetainActivityPageContent = compositionLocalOf { true }
internal val LocalSettingsDirectorySurface = compositionLocalOf<Modifier> { Modifier }

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
    val topDestination = rememberUpdatedState(backStack.lastOrNull())
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
                    } else key == topDestination.value,
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
    val topRoute = backStack.last()
    val zoomRoute = entries.last().metadata[VIDEO_ZOOM_METADATA] == true
    // Keep outgoing ordinary pages above the retained video until their Scene is disposed.
    val videoCovered = !imageActive && (
        (!zoomRoute && entries.any { it.metadata[VIDEO_ZOOM_METADATA] == true }) ||
            videoZoomState?.coveringPageKeys?.isNotEmpty() == true
        )
    val videoCoveredState = rememberUpdatedState(videoCovered)
    val videoSourceCoveredState = rememberUpdatedState(videoCovered || videoPageUnderImage)
    val zoomPreview = zoomRoute && videoZoomState?.backPreviewFor(topRoute) != null
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
            motion, previewKey, currentEntriesState, topDestination, zoomPreview, videoZoomState,
            videoCoveredState, videoSourceCoveredState,
            settingsPanes, settingsRoot, fullPageKeys, currentRailState,
        )
    }
    val sceneState = rememberSceneState(entries, listOf(strategy), onBack = onBack)
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
        backStack = backStack,
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
                previewRoute = topRoute.takeIf { zoomRoute && placement != null && !videoSourceCoveredState.value },
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
    previewRoute: T?,
) {
    val reveal = remember { Animatable(if (visible) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    var lastPlacement by remember { mutableStateOf<StartRailPlacement<T>?>(null) }
    if (visible && placement != null) SideEffect { lastPlacement = placement }
    LaunchedEffect(visible, previewRoute, videoZoomState, spec) {
        snapshotFlow { previewRoute?.let { videoZoomState?.backPreviewFor(it)?.progress } }.collectLatest { gesture ->
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

@Composable
internal fun BackPreview(
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
