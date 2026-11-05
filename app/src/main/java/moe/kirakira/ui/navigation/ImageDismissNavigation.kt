package moe.kirakira.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SceneState
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState

internal val LocalImageDismissNavigation = compositionLocalOf<ImageDismissNavigation?> { null }

/** A local touch input, isolated from system back and its predictive-animation preference. */
internal class ImageDismissNavigation : NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
    private val input = DirectNavigationEventInput()
    private var disposed = false
    private var completed = false
    var active by mutableStateOf(false)
        private set

    init {
        navigationEventDispatcher.addInput(input)
    }

    fun begin(): Boolean {
        if (active || disposed) return false
        active = true
        // Seek at zero to retain the real source page. The image itself follows the finger;
        // completing this event lets the existing shared-bounds return take over from there.
        input.backStarted(NavigationEvent(progress = 0f))
        return true
    }

    fun cancel() {
        if (!active || disposed || completed) return
        input.backCancelled()
        active = false
    }

    fun complete() {
        if (!active || disposed || completed) return
        completed = true
        input.backCompleted()
    }

    fun dispose() {
        disposed = true
        navigationEventDispatcher.removeInput(input)
        navigationEventDispatcher.dispose()
    }
}

@Composable
internal fun <T : Any> rememberImageDismissEventState(
    navigation: ImageDismissNavigation,
    sceneState: SceneState<T>,
    enabled: Boolean,
    onDismiss: () -> Unit,
): NavigationEventState<SceneInfo<T>> {
    val state = rememberNavigationEventState(
        currentInfo = SceneInfo(sceneState.currentScene),
        backInfo = sceneState.previousScenes.asReversed().map { SceneInfo(it) },
    )
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigation) {
        NavigationBackHandler(state = state, isBackEnabled = enabled, onBackCompleted = onDismiss)
    }
    DisposableEffect(navigation) {
        onDispose { navigation.dispose() }
    }
    return state
}
