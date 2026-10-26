package moe.kirakira.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneState
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import moe.kirakira.feature.player.PlaybackViewportState

/** Owns ordinary-page back dispatch and lifecycle cleanup; image scenes use NavDisplay's handler. */
@Composable
internal fun <T : Any> ActivityBackHandler(
    motion: PredictiveBackMotion,
    sceneState: SceneState<T>,
    predictiveBackEnabled: Boolean,
    enabled: Boolean,
    onBackRequested: () -> Boolean,
    canNavigateBack: () -> Boolean,
    onBack: () -> Unit,
    onStartGesture: (NavigationEvent) -> Unit,
    zoomState: PlaybackViewportState? = null,
) {
    val currentSceneState by rememberUpdatedState(sceneState)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentBackRequest by rememberUpdatedState(onBackRequested)
    val currentCanNavigateBack by rememberUpdatedState(canNavigateBack)
    val currentStartGesture by rememberUpdatedState(onStartGesture)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentZoomState by rememberUpdatedState(zoomState)
    val handler = remember(motion, predictiveBackEnabled) {
        object : NavigationEventHandler<SceneInfo<T>>(
            initialInfo = SceneInfo(sceneState.currentScene),
            isBackEnabled = false,
        ) {
            private var attempt: BackAttempt? = null

            override fun onBackStarted(event: NavigationEvent) {
                val key = currentSceneState.entries.last().contentKey
                val allowed = currentCanNavigateBack()
                attempt = BackAttempt(key, allowed)
                if (allowed && predictiveBackEnabled) {
                    if (currentZoomState != null) currentZoomState?.backProgress = 0f
                    else currentStartGesture(event)
                }
            }

            override fun onBackProgressed(event: NavigationEvent) {
                if (attempt?.allowed == true && predictiveBackEnabled) {
                    if (currentZoomState != null) currentZoomState?.backProgress = event.progress
                    else motion.progress(event)
                }
            }

            override fun onBackCancelled() {
                attempt = null
                currentZoomState?.backProgress = null
                if (predictiveBackEnabled) motion.cancel() else motion.reset()
            }

            override fun onBackCompleted() {
                val started = attempt
                attempt = null
                val key = currentSceneState.entries.last().contentKey
                if (started != null && key != started.key) {
                    motion.reset()
                    currentZoomState?.backProgress = null
                    return
                }
                val allowed = if (started != null && predictiveBackEnabled) {
                    started.allowed
                } else {
                    currentBackRequest()
                }
                if (!allowed) {
                    motion.reset()
                    currentZoomState?.backProgress = null
                    if (started != null && predictiveBackEnabled) currentBackRequest()
                    return
                }
                if (!predictiveBackEnabled) {
                    motion.reset()
                    popIfCurrent(key)
                    return
                }
                if (currentZoomState != null) {
                    popIfCurrent(key)
                    currentZoomState?.backProgress = null
                } else motion.complete { popIfCurrent(key) }
            }

            private fun popIfCurrent(key: Any) {
                val entries = currentSceneState.entries
                // Retired gestures and delayed animation callbacks must never pop a newer page.
                if (currentEnabled && entries.size > 1 && entries.last().contentKey == key) currentOnBack()
            }
        }
    }
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    SideEffect {
        motion.onTopChanged(sceneState.entries.last().contentKey)
        handler.setInfo(
            currentInfo = SceneInfo(sceneState.currentScene),
            backInfo = sceneState.previousScenes.asReversed().map { SceneInfo(it) },
        )
        handler.isBackEnabled = enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
    DisposableEffect(dispatcher, lifecycle, handler) {
        dispatcher.addHandler(handler)
        val observer = LifecycleEventObserver { _, _ ->
            val started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            handler.isBackEnabled = currentEnabled && started
            if (!started) motion.reset()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            handler.remove()
            motion.reset()
            currentZoomState?.backProgress = null
        }
    }
}

private data class BackAttempt(val key: Any, val allowed: Boolean)
