package moe.kirakira.ui.navigation

import android.view.animation.AnimationUtils
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
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
import moe.kirakira.feature.player.PlaybackViewportState
import moe.kirakira.feature.player.PlaybackNavigationPage
import moe.kirakira.ui.components.video.LocalVideoZoomOwner
import moe.kirakira.ui.components.windowCornerRadius

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
    videoZoomState: PlaybackViewportState? = null,
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
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Navigation 3 caches entries by back stack, so their content must read the latest provider.
        entryProvider = { route ->
            NavEntry(navEntry = currentEntryProvider(route)) { key ->
                CompositionLocalProvider(LocalVideoZoomOwner provides key) {
                    currentEntryProvider(key).Content()
                }
            }
        },
    )
    val currentEntriesState = rememberUpdatedState(entries)
    val imageActive = entries.last().usesImageTransition
    val zoomRoute = entries.last().metadata[VIDEO_ZOOM_METADATA] == true
    // Keep outgoing ordinary pages above the retained video until their Scene is disposed.
    val videoCovered = !imageActive && (
        (!zoomRoute && entries.any { it.metadata[VIDEO_ZOOM_METADATA] == true }) ||
            videoZoomState?.coveringPageKeys?.isNotEmpty() == true
        )
    val videoCoveredState = rememberUpdatedState(videoCovered)
    val zoomPreview = zoomRoute && videoZoomState?.backProgress != null
    val previewKey = if (zoomPreview) entries.last().contentKey else motion.closingKey
    val returningToVideo = !zoomRoute && motion.closingKey == entries.last().contentKey &&
        entries.getOrNull(entries.lastIndex - 1)?.metadata?.get(VIDEO_ZOOM_METADATA) == true
    val readVideoReturn = remember(motion) { { motion.frame.pageTransform(true) } }
    SideEffect {
        videoZoomState?.navigationReturn = readVideoReturn.takeIf { returningToVideo }
    }
    val strategy = remember(motion, previewKey, currentEntriesState, zoomPreview, videoZoomState) {
        ActivitySceneStrategy(motion, previewKey, currentEntriesState, zoomPreview, videoZoomState, videoCoveredState)
    }
    val sceneState = rememberSceneState(entries, listOf(strategy), onBack = onBack)
    val topRoute = backStack.last()
    // The image viewer uses Navigation 3's seekable predictive back. Other pages retain AOSP motion.
    val displayState = key(if (imageActive) entries.last().contentKey else OrdinaryNavigationEventKey) {
        val state = rememberNavigationEventState(
            currentInfo = SceneInfo(sceneState.currentScene),
            backInfo = sceneState.previousScenes.asReversed().map { SceneInfo(it) },
        )
        NavigationBackHandler(
            state = state,
            isBackEnabled = imageActive && entries.size > 1,
            onBackCompleted = {
                // Keying the handler retires an old gesture participant when another instance opens.
                if (imageActive && backStack.size > 1 && backStack.lastOrNull() == topRoute) onImageBack(topRoute)
            },
        )
        state
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    ActivityBackHandler(
        motion = motion,
        sceneState = sceneState,
        predictiveBackEnabled = predictiveBackEnabled,
        enabled = entries.size > 1 && !imageActive,
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
                    size = size,
                    density = density,
                    cornerRadius = view.windowCornerRadius(),
                    darkTheme = darkTheme,
                )
            }
        },
        zoomState = videoZoomState.takeIf { zoomRoute },
    )
    SharedTransitionLayout(modifier = modifier.fillMaxSize().zIndex(if (videoCovered) 1f else 0f)) {
        val imageSizes = remember { ImageTransitionSizes() }
        CompositionLocalProvider(
            LocalImageSharedScope provides this,
            LocalImageTransitionSizes provides imageSizes,
        ) {
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = displayState,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged {
                        if (size != it && size != IntSize.Zero) motion.reset()
                        size = it
                    },
                // Image entries override these defaults through their Scene metadata. Scene.key
                // is a saved-state content key, not a route instance; never infer motion from it.
                transitionSpec = {
                    if (targetState.usesVideoZoom) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else ordinaryMotion.forward
                },
                popTransitionSpec = {
                    if (initialState.usesVideoZoom ||
                        motion.completedPop == (initialState.key to targetState.key)) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        ordinaryMotion.backward
                    }
                },
                predictivePopTransitionSpec = {
                    EnterTransition.None togetherWith ExitTransition.None
                },
            )
        }
    }
}

private data object OrdinaryNavigationEventKey

private val Scene<*>.usesVideoZoom: Boolean
    get() = entries.lastOrNull()?.metadata?.get(VIDEO_ZOOM_METADATA) == true

private class ActivitySceneStrategy<T : Any>(
    private val motion: PredictiveBackMotion,
    private val previewKey: Any?,
    private val currentEntries: State<List<NavEntry<T>>>,
    private val zoomPreview: Boolean,
    private val videoZoomState: PlaybackViewportState?,
    private val videoCovered: State<Boolean>,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> =
        ActivityScene(
            key = entries.last().contentKey,
            entries = entries.takeLast(
                if (entries.last().contentKey == previewKey || entries.last().metadata[VIDEO_ZOOM_METADATA] == true) 2 else 1,
            ),
            previousEntries = entries.dropLast(1),
            motion = motion,
            currentEntries = currentEntries,
            zoomPreview = zoomPreview,
            videoZoomState = videoZoomState,
            videoCovered = videoCovered,
        )
}

private data class ActivityScene<T : Any>(
    override val key: Any,
    override val entries: List<NavEntry<T>>,
    override val previousEntries: List<NavEntry<T>>,
    val motion: PredictiveBackMotion,
    val currentEntries: State<List<NavEntry<T>>>,
    val zoomPreview: Boolean,
    val videoZoomState: PlaybackViewportState?,
    val videoCovered: State<Boolean>,
) : Scene<T> {
    override val content: @Composable () -> Unit = {
        DisposableEffect(key, motion) {
            onDispose { motion.onSceneDisposed(key) }
        }
        val coversVideo = !usesVideoZoom && !entries.last().usesImageTransition &&
            previousEntries.lastOrNull()?.metadata?.get(VIDEO_ZOOM_METADATA) == true
        DisposableEffect(key, coversVideo, videoZoomState) {
            if (coversVideo) videoZoomState?.coveringPageKeys?.put(key, Unit)
            onDispose {
                if (coversVideo) videoZoomState?.coveringPageKeys?.remove(key)
            }
        }
        val preview = entries.size == 2 && (motion.closingKey == key || zoomPreview)
        val zoomLayer = entries.size == 2 && entries.last().metadata[VIDEO_ZOOM_METADATA] == true
        val owner = rememberLifecycleOwner(
            maxLifecycle = if (preview || zoomLayer) Lifecycle.State.STARTED else Lifecycle.State.RESUMED,
        )
        val lifecycleState by owner.lifecycle.currentStateAsState()
        val visibility = LocalNavAnimatedContentScope.current.transition
        if (usesVideoZoom && videoZoomState != null) {
            val ordinary by rememberUpdatedState(videoCovered.value && motion.completedPop?.second != key)
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
        val imageScene = entries.last().usesImageTransition
        val ordinaryPop = !preview && !imageScene && stack.none { it.contentKey == key } &&
            motion.completedPop?.first != key
        val exitOpacity = visibility.animateFloat(
            transitionSpec = { if (ordinaryPop) activityCloseFadeSpec() else snap() },
            label = "scene content retention",
        ) { if (it == EnterExitState.PostExit) 0f else 1f }
        // Keep the slide running, but retire invisible content so hits reach the incoming page.
        val retainContent by remember(ordinaryPop, exitOpacity) {
            derivedStateOf { !ordinaryPop || exitOpacity.value > 0f }
        }
        val interactive = !preview && !zoomLayer && stack.lastOrNull()?.contentKey == key &&
            lifecycleState.isAtLeast(Lifecycle.State.STARTED) && visibility.targetState == EnterExitState.Visible &&
            (!imageScene || (
                lifecycleState == Lifecycle.State.RESUMED && visibility.currentState == EnterExitState.Visible
                ))
        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        // This placeholder Scene must reveal the persistent VideoPage, not its
                        // original card page, under an ordinary page entering or leaving above it.
                        if (!zoomLayer || !videoCovered.value) drawContent()
                    }
                    .then(
                        if (retainContent && !interactive) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        } else Modifier,
                    ),
            ) {
                if (!retainContent) return@Box
                if (zoomLayer) {
                    entries.first().Content()
                    entries.last().Content()
                } else if (preview) {
                    val enteringTransform = remember(motion) { { motion.frame.pageTransform(true) } }
                    val closingTransform = remember(motion) { { motion.frame.pageTransform(false) } }
                    CompositionLocalProvider(LocalNavigationPageTransform provides enteringTransform) {
                        entries.first().Content()
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color.Black, alpha = motion.frame.scrimAlpha)
                    }
                    CompositionLocalProvider(LocalNavigationPageTransform provides closingTransform) {
                        entries.last().Content()
                    }
                } else {
                    entries.last().Content()
                }
            }
        }
    }
}
