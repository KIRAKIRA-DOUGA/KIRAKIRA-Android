package moe.kirakira.ui.navigation

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics

@Composable
internal fun Modifier.blockNavigationFocus(block: Boolean): Modifier {
    val focusManager = LocalFocusManager.current
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(block, focusManager) {
        if (block && hasFocus) focusManager.clearFocus(force = true)
    }
    val focusBoundary = focusProperties { onEnter = { if (block) cancelFocusChange() } }
        .onFocusChanged { hasFocus = it.hasFocus }
        .focusGroup()
    return if (block) focusBoundary.onPreviewKeyEvent { true } else focusBoundary
}

@Composable
internal fun Modifier.blockNavigationInput(block: Boolean): Modifier {
    val focusBoundary = blockNavigationFocus(block)
    if (!block) return focusBoundary
    return focusBoundary
        .clearAndSetSemantics {}
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
}
