package moe.kirakira.ui.components.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay

private const val CONTROLS_IDLE_TIMEOUT_MILLIS = 3_000L

/** Local viewing preference; touch/focus activity is deliberately not restored after recreation. */
internal class ImageViewerControlsState(initiallyVisible: Boolean = true) {
    var visible by mutableStateOf(initiallyVisible)
        private set
    var interacting by mutableStateOf(false)
        private set

    fun toggle() {
        visible = !visible
    }

    fun show() {
        visible = true
    }

    fun hide() {
        visible = false
    }

    fun onInteractionChange(active: Boolean) {
        interacting = active
    }

    companion object {
        val saver = Saver<ImageViewerControlsState, Boolean>(
            save = { it.visible },
            restore = { ImageViewerControlsState(it) },
        )
    }
}

@Composable
internal fun rememberImageViewerControlsState(
    autoHideEnabled: Boolean,
    forceVisible: Boolean,
): ImageViewerControlsState {
    val state = rememberSaveable(saver = ImageViewerControlsState.saver) { ImageViewerControlsState() }
    val accessibility = LocalAccessibilityManager.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(forceVisible) {
        if (forceVisible) state.show()
    }
    LaunchedEffect(state.visible, state.interacting, autoHideEnabled, forceVisible, lifecycleState, accessibility) {
        if (state.visible && !state.interacting && autoHideEnabled && !forceVisible &&
            lifecycleState == Lifecycle.State.RESUMED
        ) {
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = CONTROLS_IDLE_TIMEOUT_MILLIS,
                containsIcons = true,
                containsControls = true,
            ) ?: CONTROLS_IDLE_TIMEOUT_MILLIS
            delay(timeout)
            state.hide()
        }
    }
    return state
}
