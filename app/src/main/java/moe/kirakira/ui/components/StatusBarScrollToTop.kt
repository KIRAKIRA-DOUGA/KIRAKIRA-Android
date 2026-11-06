package moe.kirakira.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import moe.kirakira.core.system.StatusBarScrollToTopBridge

private val LocalStatusBarScrollToTopBridge = compositionLocalOf<StatusBarScrollToTopBridge?> { null }
internal val LocalStatusBarScrollToTopEnabled = compositionLocalOf { true }

internal class ScrollToTopHeader(
    val remainingDistance: () -> Float,
    val isDragging: () -> Boolean = { false },
    val scrollBy: (Float) -> Unit,
    val onExpanded: () -> Unit = {},
) {
    val canExpand: () -> Boolean = { remainingDistance() > 0f }
    val expand: suspend () -> Unit = { smoothExpand() }
}
private val LocalScrollToTopHeader = compositionLocalOf<ScrollToTopHeader?> { null }

@Composable
internal fun StatusBarScrollToTopHost(content: @Composable () -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity
    val bridge = remember(activity) { activity?.let(::StatusBarScrollToTopBridge) }
    DisposableEffect(bridge) {
        bridge?.attach()
        onDispose { bridge?.dispose() }
    }
    CompositionLocalProvider(LocalStatusBarScrollToTopBridge provides bridge, content = content)
}

/** Call only for the foreground page's body. Changing [identity] cancels its old request. */
@Composable
internal fun StatusBarScrollToTopEffect(
    identity: Any,
    canScrollToTop: () -> Boolean,
    enabled: Boolean = true,
    priority: Int = 1,
    onScrollToTop: suspend () -> Unit,
) {
    val bridge = LocalStatusBarScrollToTopBridge.current ?: return
    val pageEnabled = LocalStatusBarScrollToTopEnabled.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled && pageEnabled && lifecycleState == Lifecycle.State.RESUMED)
    val currentCanScroll by rememberUpdatedState(canScrollToTop)
    val currentAction by rememberUpdatedState(onScrollToTop)
    val target = remember(bridge, identity, lifecycle, scope, priority) {
        StatusBarScrollToTopBridge.Target(
            enabled = { currentEnabled && lifecycle.currentState == Lifecycle.State.RESUMED },
            canScrollToTop = { currentCanScroll() },
            scope = scope,
            scrollToTop = { currentAction() },
            priority = priority,
        )
    }
    DisposableEffect(bridge, target) {
        bridge.add(target)
        onDispose { bridge.remove(target) }
    }
    LaunchedEffect(bridge, target) {
        snapshotFlow { target.enabled() to target.canScrollToTop() }.collect { bridge.refresh() }
    }
}

/** A modal surface reserves the foreground target without providing a scrolling action. */
@Composable
internal fun StatusBarScrollToTopPauseEffect(paused: Boolean = true) {
    val identity = remember { Any() }
    StatusBarScrollToTopEffect(identity, { false }, enabled = paused, priority = Int.MAX_VALUE) {}
}

/** Own the drag so the pause can extend through Material's suspendable fling and snap. */
@Composable
internal fun Modifier.dragCollapsibleTopAppBar(behavior: TopAppBarScrollBehavior): Modifier {
    val bridge = LocalStatusBarScrollToTopBridge.current
    val scope = rememberCoroutineScope()
    val dragPause = remember(bridge, scope, behavior) { scrollToTopPauseTarget(scope) }
    val dragState = remember(behavior) { DraggableState { behavior.state.heightOffset += it } }
    var settlingJob by remember(dragPause) { mutableStateOf<Job?>(null) }
    DisposableEffect(bridge, dragPause) {
        onDispose {
            settlingJob?.cancel()
            bridge?.remove(dragPause)
        }
    }
    return pauseStatusBarScrollToTopOnTouch().draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStarted = {
            val previousSettle = settlingJob
            settlingJob = null
            if (previousSettle == null) bridge?.add(dragPause) else previousSettle.cancel()
        },
        onDragStopped = { velocity ->
            val job = coroutineContext.job
            settlingJob = job
            try {
                behavior.nestedScrollConnection.onPostFling(Velocity.Zero, Velocity(0f, velocity))
            } finally {
                if (settlingJob === job) {
                    settlingJob = null
                    bridge?.remove(dragPause)
                }
            }
        },
    )
}

@Composable
private fun Modifier.pauseStatusBarScrollToTopOnTouch(): Modifier {
    val bridge = LocalStatusBarScrollToTopBridge.current ?: return this
    val scope = rememberCoroutineScope()
    val pauseTarget = remember(bridge, scope) { scrollToTopPauseTarget(scope) }
    return pointerInput(bridge, pauseTarget) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            bridge.add(pauseTarget)
            try {
                // Observe without consuming, so title buttons and the draggable handle the gesture.
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                // Also release the pause when this pointer-input node is cancelled or detached.
                bridge.remove(pauseTarget)
            }
        }
    }
}

private fun scrollToTopPauseTarget(scope: CoroutineScope) = StatusBarScrollToTopBridge.Target(
    enabled = { true },
    canScrollToTop = { false },
    scope = scope,
    scrollToTop = {},
    priority = Int.MAX_VALUE,
)

@Composable
internal fun StatusBarScrollToTopEffect(
    state: LazyListState,
    enabled: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    scrollContentAvailable: Boolean = true,
) {
    val header = scrollBehavior?.let { rememberScrollToTopHeader(it) } ?: LocalScrollToTopHeader.current
    val dragging by state.interactionSource.collectIsDraggedAsState()
    StatusBarScrollToTopEffect(
        identity = state to scrollContentAvailable,
        canScrollToTop = { (scrollContentAvailable && state.canScrollBackward) || header?.canExpand() == true },
        enabled = enabled && !dragging && header?.isDragging() != true,
    ) {
        if (scrollContentAvailable) {
            state.smoothScrollToTop(header)
        } else {
            // A header can be collapsed before this lazy list has ever been laid out.
            header?.smoothExpand()
        }
    }
}

@Composable
internal fun StatusBarScrollToTopEffect(
    state: ScrollState,
    enabled: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    priority: Int = 1,
) {
    val header = scrollBehavior?.let { rememberScrollToTopHeader(it) } ?: LocalScrollToTopHeader.current
    val dragging by state.interactionSource.collectIsDraggedAsState()
    StatusBarScrollToTopEffect(
        identity = state,
        canScrollToTop = { state.value > 0 || header?.canExpand() == true },
        enabled = enabled && !dragging && header?.isDragging() != true,
        priority = priority,
    ) {
        state.smoothScrollToTop(header)
    }
}

/** Page-sized empty/error bodies can own a short scroll inside an otherwise stationary list. */
@Composable
internal fun StatusBarScrollToTopFallbackEffect(state: ScrollState) {
    val header = LocalScrollToTopHeader.current
    val dragging by state.interactionSource.collectIsDraggedAsState()
    StatusBarScrollToTopEffect(
        identity = state,
        canScrollToTop = { state.value > 0 },
        enabled = !dragging && header?.isDragging() != true,
        priority = 2,
    ) {
        state.smoothScrollToTop(header)
    }
}

@Composable
internal fun StatusBarScrollToTopHeader(
    scrollBehavior: TopAppBarScrollBehavior,
    content: @Composable () -> Unit,
) {
    val header = rememberScrollToTopHeader(scrollBehavior)
    StatusBarScrollToTopEffect(scrollBehavior, header.canExpand, priority = 0, onScrollToTop = header.expand)
    CompositionLocalProvider(LocalScrollToTopHeader provides header, content = content)
}

@Composable
internal fun StatusBarScrollToTopHeader(
    state: CollapsingTabState,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dragging = state.interactionSource.collectIsDraggedAsState()
    val header = remember(state) {
        ScrollToTopHeader(
            remainingDistance = { state.offset },
            isDragging = { dragging.value },
            scrollBy = { delta ->
                state.nestedScrollConnection.onPostScroll(
                    consumed = Offset.Zero,
                    available = Offset(0f, delta),
                    source = NestedScrollSource.SideEffect,
                )
            },
        )
    }
    StatusBarScrollToTopEffect(
        state, header.canExpand, enabled = enabled && !dragging.value, priority = 0, onScrollToTop = header.expand,
    )
    CompositionLocalProvider(LocalScrollToTopHeader provides header, content = content)
}

@Composable
private fun rememberScrollToTopHeader(behavior: TopAppBarScrollBehavior): ScrollToTopHeader {
    return remember(behavior) {
        ScrollToTopHeader(
            remainingDistance = { -behavior.state.heightOffset },
            scrollBy = { delta ->
                behavior.nestedScrollConnection.onPostScroll(
                    consumed = Offset.Zero,
                    available = Offset(0f, delta),
                    source = NestedScrollSource.SideEffect,
                )
            },
            onExpanded = { behavior.state.contentOffset = 0f },
        )
    }
}
