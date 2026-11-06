package moe.kirakira.feature.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@Composable
internal fun Modifier.dismissComposersOnOutsideTap(
    comment: ComposerState,
    danmaku: ComposerState,
    enabled: Boolean,
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val composers = remember(comment, danmaku) { listOf(comment, danmaku) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return onGloballyPositioned { coordinates = it }
        .pointerInput(composers, enabled, focus, keyboard) {
            if (!enabled) return@pointerInput
            // Observe before children, including buttons that consume taps, without consuming events.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (composers.none { it.inputFocused || it.panelOpen }) return@awaitEachGesture
                val root = coordinates?.takeIf { it.isAttached } ?: return@awaitEachGesture
                fun insideComposer(position: Offset): Boolean {
                    val windowPosition = root.localToWindow(position)
                    return composers.any {
                        it.inputCoordinates.containsWindowPosition(windowPosition) ||
                            (it.panelOpen && it.panelCoordinates.containsWindowPosition(windowPosition))
                    }
                }
                if (insideComposer(down.position)) return@awaitEachGesture
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.singleOrNull()?.takeIf { it.id == down.id }
                        ?: return@awaitEachGesture
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                        change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                    ) return@awaitEachGesture
                    if (change.changedToUp()) {
                        if (root.isAttached && !insideComposer(change.position)) {
                            composers.forEach { it.panelOpen = false }
                            focus.clearFocus(force = true)
                            keyboard?.hide()
                        }
                        return@awaitEachGesture
                    }
                    if (!change.pressed) return@awaitEachGesture
                }
            }
        }
}

private fun LayoutCoordinates?.containsWindowPosition(position: Offset): Boolean =
    this?.takeIf { it.isAttached }?.boundsInWindow()?.contains(position) == true
