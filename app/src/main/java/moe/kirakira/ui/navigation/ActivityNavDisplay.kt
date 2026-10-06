package moe.kirakira.ui.navigation

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.animation.AnimationUtils
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Easing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

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
    entryProvider: (T) -> NavEntry<T>,
) {
    val ordinaryMotion = rememberNavigationMotion()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val motion = remember(context, scope) {
        val interpolator = AnimationUtils.loadInterpolator(context, activityInterpolatorResource())
        PredictiveBackMotion(scope, Easing { interpolator.getInterpolation(it) })
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider,
    )
    val currentEntriesState = rememberUpdatedState(entries)
    val imageActive = entries.last().usesImageTransition
    val previewKey = motion.closingKey
    val strategy = remember(motion, previewKey, currentEntriesState) {
        ActivitySceneStrategy(motion, previewKey, currentEntriesState)
    }
    val sceneState = rememberSceneState(entries, listOf(strategy), onBack = onBack)
    val topRoute = backStack.last()
    // The image viewer uses Navigation 3's seekable predictive back. Other pages retain AOSP motion.
    val displayState = rememberNavigationEventState(
        currentInfo = SceneInfo(sceneState.currentScene),
        backInfo = sceneState.previousScenes.asReversed().map { SceneInfo(it) },
    )
    NavigationBackHandler(
        state = displayState,
        isBackEnabled = imageActive && entries.size > 1,
        onBackCompleted = {
            // A close button may already have removed the viewer during the gesture.
            if (imageActive && backStack.size > 1 && backStack.lastOrNull() == topRoute) onBack()
        },
    )
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    val currentEntries by currentEntriesState
    val currentOnBack by rememberUpdatedState(onBack)
    val currentBackRequest by rememberUpdatedState(onBackRequested)
    val currentCanNavigateBack by rememberUpdatedState(canNavigateBack)
    val startGesture by rememberUpdatedState<(NavigationEvent) -> Unit> { event ->
        val current = currentEntries.lastOrNull()
        val previous = currentEntries.getOrNull(currentEntries.lastIndex - 1)
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
    }
    val handler = remember(motion, predictiveBackEnabled) {
        object : NavigationEventHandler<SceneInfo<T>>(
            initialInfo = SceneInfo(sceneState.currentScene),
            isBackEnabled = false,
        ) {
            private var backStarted = false
            private var backAllowed = true
            private var backKey: Any? = null

            override fun onBackStarted(event: NavigationEvent) {
                backStarted = true
                backAllowed = currentCanNavigateBack()
                backKey = currentEntries.lastOrNull()?.contentKey
                if (backAllowed && predictiveBackEnabled) startGesture(event)
            }

            override fun onBackProgressed(event: NavigationEvent) {
                if (backAllowed && predictiveBackEnabled) motion.progress(event)
            }

            override fun onBackCancelled() {
                backStarted = false
                backKey = null
                if (predictiveBackEnabled) motion.cancel() else motion.reset()
            }

            override fun onBackCompleted() {
                val wasStarted = backStarted
                val startedKey = backKey
                backStarted = false
                backKey = null
                if (wasStarted && currentEntries.lastOrNull()?.contentKey != startedKey) {
                    motion.reset()
                    return
                }
                if (!wasStarted || !predictiveBackEnabled) backAllowed = currentBackRequest()
                if (!backAllowed) {
                    motion.reset()
                    if (wasStarted && predictiveBackEnabled) currentBackRequest()
                    return
                }
                val key = currentEntries.lastOrNull()?.contentKey
                if (!predictiveBackEnabled) {
                    motion.reset()
                    if (currentEntries.size > 1 && currentEntries.lastOrNull()?.contentKey == key) {
                        currentOnBack()
                    }
                    return
                }
                motion.complete {
                    // Ignore stale terminal events if another navigation already changed the stack.
                    if (currentEntries.size > 1 && currentEntries.lastOrNull()?.contentKey == key) {
                        currentOnBack()
                    }
                }
            }
        }
    }
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canGoBack = entries.size > 1 && !imageActive
    val currentCanGoBack by rememberUpdatedState(canGoBack)
    SideEffect {
        motion.onTopChanged(entries.last().contentKey)
        handler.setInfo(
            currentInfo = SceneInfo(sceneState.currentScene),
            backInfo = sceneState.previousScenes.asReversed().map { SceneInfo(it) },
        )
        handler.isBackEnabled = canGoBack && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
    DisposableEffect(dispatcher, lifecycle, handler) {
        dispatcher.addHandler(handler)
        val observer = LifecycleEventObserver { _, _ ->
            val started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            handler.isBackEnabled = currentCanGoBack && started
            if (!started) motion.reset()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            handler.remove()
            motion.reset()
        }
    }
    SharedTransitionLayout {
        val imageSizes = remember { ImageTransitionSizes() }
        CompositionLocalProvider(
            LocalImageSharedScope provides this,
            LocalImageTransitionSizes provides imageSizes,
        ) {
            NavDisplay(
                sceneState = sceneState,
                navigationEventState = displayState,
                modifier = modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged {
                        if (size != it && size != IntSize.Zero) motion.reset()
                        size = it
                    },
                // Image entries override these defaults through their Scene metadata. Scene.key
                // is a saved-state content key, not a route instance; never infer motion from it.
                transitionSpec = { ordinaryMotion.forward },
                popTransitionSpec = {
                    if (motion.completedPop == (initialState.key to targetState.key)) {
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

private class ActivitySceneStrategy<T : Any>(
    private val motion: PredictiveBackMotion,
    private val previewKey: Any?,
    private val currentEntries: State<List<NavEntry<T>>>,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> =
        ActivityScene(
            key = entries.last().contentKey,
            entries = entries.takeLast(if (entries.last().contentKey == previewKey) 2 else 1),
            previousEntries = entries.dropLast(1),
            motion = motion,
            currentEntries = currentEntries,
        )
}

private data class ActivityScene<T : Any>(
    override val key: Any,
    override val entries: List<NavEntry<T>>,
    override val previousEntries: List<NavEntry<T>>,
    val motion: PredictiveBackMotion,
    val currentEntries: State<List<NavEntry<T>>>,
) : Scene<T> {
    override val content: @Composable () -> Unit = {
        DisposableEffect(key, motion) {
            onDispose { motion.onSceneDisposed(key) }
        }
        val preview = entries.size == 2 && motion.closingKey == key
        val owner = rememberLifecycleOwner(
            maxLifecycle = if (preview) Lifecycle.State.STARTED else Lifecycle.State.RESUMED,
        )
        val lifecycleState by owner.lifecycle.currentStateAsState()
        val visibility = LocalNavAnimatedContentScope.current.transition
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
        val interactive = !preview && stack.lastOrNull()?.contentKey == key &&
            lifecycleState.isAtLeast(Lifecycle.State.STARTED) && visibility.targetState == EnterExitState.Visible &&
            (!imageScene || (
                lifecycleState == Lifecycle.State.RESUMED && visibility.currentState == EnterExitState.Visible
                ))
        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            Box(
                Modifier
                    .fillMaxSize()
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
                if (preview) {
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

private const val DEFAULT_WINDOW_CORNER_RADIUS_DP = 28f

/** Prefer screen corners; fall back to 28dp when the platform provides no positive radius. */
private fun View.windowCornerRadius(): Float {
    val fallbackRadius = DEFAULT_WINDOW_CORNER_RADIUS_DP * resources.displayMetrics.density
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallbackRadius
    val insets = rootWindowInsets ?: return fallbackRadius
    return listOf(
        RoundedCorner.POSITION_TOP_LEFT,
        RoundedCorner.POSITION_TOP_RIGHT,
        RoundedCorner.POSITION_BOTTOM_LEFT,
        RoundedCorner.POSITION_BOTTOM_RIGHT,
    ).mapNotNull { insets.getRoundedCorner(it)?.radius?.takeIf { radius -> radius > 0 } }
        .minOrNull()?.toFloat() ?: fallbackRadius
}
