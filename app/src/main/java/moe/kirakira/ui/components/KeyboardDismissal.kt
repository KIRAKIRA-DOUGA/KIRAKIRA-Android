package moe.kirakira.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Dismiss only for unclaimed short taps, preserving text-field and button interactions. */
@Composable
internal fun Modifier.dismissKeyboardOnBlankTap(enabled: Boolean = true): Modifier {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return pointerInput(enabled, focus, keyboard) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            // Wait until children have handled the event; claimed taps keep their normal behavior.
            val down = awaitFirstDown(pass = PointerEventPass.Final)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.singleOrNull()?.takeIf { it.id == down.id }
                    ?: return@awaitEachGesture
                if (change.isConsumed ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                    change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                ) return@awaitEachGesture
                if (change.changedToUpIgnoreConsumed()) {
                    focus.clearFocus()
                    keyboard?.hide()
                    return@awaitEachGesture
                }
            }
        }
    }
}
