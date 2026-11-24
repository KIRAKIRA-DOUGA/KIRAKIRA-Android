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

/** Owns ordinary back dispatch and image returns when predictive back is disabled. */
@Composable
internal fun <T : Any> ActivityBackHandler(
    motion: PredictiveBackMotion,
    sceneState: SceneState<T>,
    backStack: List<T>,
    predictiveBackEnabled: Boolean,
    enabled: Boolean,
    onBackRequested: () -> Boolean,
    canNavigateBack: () -> Boolean,
    onBack: () -> Unit,
    onStartGesture: (NavigationEvent) -> Unit,
    zoomState: PlaybackViewportState? = null,
) {
    val currentSceneState by rememberUpdatedState(sceneState)
    val currentBackStack by rememberUpdatedState(backStack)
    val currentSceneRoute by rememberUpdatedState(backStack.last())
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
            private var attempt: BackAttempt<T>? = null
            private var available = false

            override fun onBackStarted(event: NavigationEvent) {
                val key = currentSceneState.entries.last().contentKey
                val route = currentSceneRoute
                attempt?.finishPreview()
                if (!isCurrent(key, route)) {
                    motion.reset()
                    attempt = BackAttempt(key, route, false, null, null).also { it.invalidate() }
                    return
                }
                val allowed = currentCanNavigateBack()
                val zoom = currentZoomState.takeIf { allowed && predictiveBackEnabled }
                attempt = BackAttempt(key, route, allowed, zoom, zoom?.startBackPreview(route, event))
                if (allowed && predictiveBackEnabled && zoom == null) currentStartGesture(event)
            }

            override fun onBackProgressed(event: NavigationEvent) {
                val started = attempt?.takeIf { it.allowed && !it.invalidated && predictiveBackEnabled } ?: return
                if (!isBackEnabled || !isCurrent(started.key, started.route)) return
                if (started.usesVideoZoom) started.preview?.update(event) else motion.progress(event)
            }

            override fun onBackCancelled() {
                attempt?.finishPreview(transferVelocity = true)
                attempt = null
                if (predictiveBackEnabled) motion.cancel() else motion.reset()
            }

            override fun onBackCompleted() {
                val started = attempt
                attempt = null
                val key = currentSceneState.entries.last().contentKey
                val route = currentSceneRoute
                if (!isBackEnabled || !isCurrent(key, route) ||
                    (started != null && (started.invalidated || key != started.key || route != started.route))
                ) {
                    motion.reset()
                    started?.finishPreview()
                    return
                }
                val allowed = if (started != null && predictiveBackEnabled) {
                    started.allowed && currentCanNavigateBack()
                } else {
                    currentBackRequest()
                }
                if (!allowed) {
                    motion.reset()
                    started?.finishPreview()
                    if (started != null && predictiveBackEnabled) currentBackRequest()
                    return
                }
                if (!predictiveBackEnabled) {
                    motion.reset()
                    popIfCurrent(key, route)
                    return
                }
                if (currentZoomState != null) {
                    popIfCurrent(key, route)
                    started?.finishPreview(transferVelocity = true)
                } else motion.complete {
                    if (!isBackEnabled || !isCurrent(key, route)) {
                        motion.reset()
                        return@complete
                    }
                    if (currentCanNavigateBack()) {
                        popIfCurrent(key, route)
                    } else {
                        motion.reset()
                        currentBackRequest()
                    }
                }
            }

            // The back stack can change before composition publishes the corresponding Scene.
            private fun isCurrent(key: Any, route: T): Boolean =
                currentBackStack.lastOrNull() == route && currentSceneRoute == route &&
                    currentSceneState.entries.last().contentKey == key

            private fun popIfCurrent(key: Any, route: T) {
                if (isBackEnabled && currentBackStack.size > 1 && isCurrent(key, route)) currentOnBack()
            }

            fun abandonAttempt() {
                attempt?.finishPreview()
                attempt = null
            }

            fun onTopChanged(key: Any) {
                attempt?.takeUnless { it.key == key && it.route == currentSceneRoute }?.invalidate()
            }

            fun updateEnabled(enabled: Boolean) {
                if (available == enabled) return
                available = enabled
                isBackEnabled = enabled
                if (!enabled) {
                    // The dispatcher can still deliver the terminal event to an in-flight handler.
                    // Keep the retired attempt so that callback cannot become a fresh back request.
                    attempt?.invalidate()
                    motion.reset()
                }
            }
        }
    }
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    SideEffect {
        val topKey = sceneState.entries.last().contentKey
        handler.onTopChanged(topKey)
        motion.onTopChanged(topKey)
        handler.setInfo(
            currentInfo = SceneInfo(sceneState.currentScene),
            backInfo = sceneState.previousScenes.asReversed().map { SceneInfo(it) },
        )
        handler.updateEnabled(enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(dispatcher, lifecycle, handler) {
        dispatcher.addHandler(handler)
        val observer = LifecycleEventObserver { _, _ ->
            val started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            handler.updateEnabled(currentEnabled && started)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            handler.abandonAttempt()
            handler.remove()
            motion.reset()
        }
    }
}

private class BackAttempt<T : Any>(
    val key: Any,
    val route: T,
    val allowed: Boolean,
    private val zoom: PlaybackViewportState?,
    val preview: VideoBackPreview?,
) {
    val usesVideoZoom: Boolean get() = zoom != null
    var invalidated = false
        private set

    fun finishPreview(transferVelocity: Boolean = false) {
        if (preview != null) zoom?.finishBackPreview(preview, transferVelocity)
    }

    fun invalidate() {
        invalidated = true
        finishPreview()
    }
}
