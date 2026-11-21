package moe.kirakira.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import moe.kirakira.core.system.StatusBarScrollToTopBridge

private val LocalStatusBarScrollToTopBridge = compositionLocalOf<StatusBarScrollToTopBridge?> { null }
internal val LocalStatusBarScrollToTopEnabled = compositionLocalOf { true }

internal class ScrollToTopHeader(
    val remainingDistance: () -> Float,
    val isDragging: () -> Boolean = { false },
    val scrollBy: (Float) -> Unit,
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

@Composable
internal fun StatusBarScrollToTopEffect(
    state: LazyListState,
    enabled: Boolean = true,
    scrollContentAvailable: Boolean = true,
) {
    val header = LocalScrollToTopHeader.current
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
internal fun StatusBarScrollToTopEffect(state: LazyGridState, enabled: Boolean = true) {
    val header = LocalScrollToTopHeader.current
    val dragging by state.interactionSource.collectIsDraggedAsState()
    StatusBarScrollToTopEffect(
        identity = state,
        canScrollToTop = { state.canScrollBackward || header?.canExpand() == true },
        enabled = enabled && !dragging && header?.isDragging() != true,
    ) {
        state.smoothScrollToTop(header)
    }
}

@Composable
internal fun StatusBarScrollToTopEffect(
    state: ScrollState,
    enabled: Boolean = true,
    priority: Int = 1,
) {
    val header = LocalScrollToTopHeader.current
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
