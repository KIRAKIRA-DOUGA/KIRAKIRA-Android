package moe.kirakira.ui.navigation

import android.view.RoundedCorner
import android.view.View
import android.view.animation.AnimationUtils
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
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
    entryProvider: (T) -> NavEntry<T>,
) {
    val ordinaryMotion = rememberNavigationMotion()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val motion = remember(context, scope) {
        val interpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_extra_slow_in)
        PredictiveBackMotion(scope, Easing { interpolator.getInterpolation(it) })
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider,
    )
    val previewKey = motion.closingKey
    val strategy = remember(motion, previewKey) { ActivitySceneStrategy<T>(motion, previewKey) }
    val sceneState = rememberSceneState(entries, listOf(strategy), onBack = onBack)
    // Intentionally stays idle: a stock NavDisplay seek/finish would also animate these pages,
    // collapsing the platform's two phases into one tween. The handler below owns the gesture.
    val displayState = rememberNavigationEventState(currentInfo = SceneInfo(sceneState.currentScene))
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    val currentEntries by rememberUpdatedState(entries)
    val currentOnBack by rememberUpdatedState(onBack)
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
    val handler = remember(motion) {
        object : NavigationEventHandler<SceneInfo<T>>(
            initialInfo = SceneInfo(sceneState.currentScene),
            isBackEnabled = false,
        ) {
            override fun onBackStarted(event: NavigationEvent) = startGesture(event)

            override fun onBackProgressed(event: NavigationEvent) = motion.progress(event)

            override fun onBackCancelled() = motion.cancel()

            override fun onBackCompleted() {
                val key = currentEntries.lastOrNull()?.contentKey
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
    val canGoBack = entries.size > 1
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
        transitionSpec = { ordinaryMotion.forward },
        popTransitionSpec = {
            if (motion.completedPop == (initialState.key to targetState.key)) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                ordinaryMotion.backward
            }
        },
        predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
    )
}

private class ActivitySceneStrategy<T : Any>(
    private val motion: PredictiveBackMotion,
    private val previewKey: Any?,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> =
        ActivityScene(
            key = entries.last().contentKey,
            entries = entries.takeLast(if (entries.last().contentKey == previewKey) 2 else 1),
            previousEntries = entries.dropLast(1),
            motion = motion,
        )
}

private data class ActivityScene<T : Any>(
    override val key: Any,
    override val entries: List<NavEntry<T>>,
    override val previousEntries: List<NavEntry<T>>,
    val motion: PredictiveBackMotion,
) : Scene<T> {
    override val content: @Composable () -> Unit = {
        DisposableEffect(key, motion) {
            onDispose { motion.onSceneDisposed(key) }
        }
        val preview = entries.size == 2 && motion.closingKey == key
        val owner = rememberLifecycleOwner(
            maxLifecycle = if (preview) Lifecycle.State.STARTED else Lifecycle.State.RESUMED,
        )
        CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (preview) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        } else Modifier,
                    ),
            ) {
                if (preview) {
                    val frame = motion.frame
                    CompositionLocalProvider(LocalNavigationPageTransform provides frame.pageTransform(true)) {
                        entries.first().Content()
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color.Black, alpha = frame.scrimAlpha)
                    }
                    CompositionLocalProvider(LocalNavigationPageTransform provides frame.pageTransform(false)) {
                        entries.last().Content()
                    }
                } else {
                    entries.last().Content()
                }
            }
        }
    }
}

/** Public WindowInsets equivalent; system-only corner adjustments are not exposed to apps. */
private fun View.windowCornerRadius(): Float {
    val insets = rootWindowInsets ?: return 0f
    return listOf(
        RoundedCorner.POSITION_TOP_LEFT,
        RoundedCorner.POSITION_TOP_RIGHT,
        RoundedCorner.POSITION_BOTTOM_LEFT,
        RoundedCorner.POSITION_BOTTOM_RIGHT,
    ).mapNotNull { insets.getRoundedCorner(it)?.radius?.takeIf { radius -> radius > 0 } }
        .minOrNull()?.toFloat() ?: 0f
}
