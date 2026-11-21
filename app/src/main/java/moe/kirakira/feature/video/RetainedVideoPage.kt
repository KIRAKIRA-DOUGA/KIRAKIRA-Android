package moe.kirakira.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import moe.kirakira.ui.components.LocalStatusBarScrollToTopEnabled

/** Stores survive Activity recreation; only retained route/session identities keep their models. */
internal class RetainedVideoPageViewModel : ViewModel() {
    private val stores = mutableMapOf<Pair<Long, Int>, ViewModelStoreOwner>()

    fun owner(sessionRevision: Long, videoId: Int): ViewModelStoreOwner =
        stores.getOrPut(sessionRevision to videoId) {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }

    fun retain(sessionRevision: Long, videoIds: Set<Int>) {
        stores.keys.filter { (revision, id) -> revision != sessionRevision || id !in videoIds }.forEach { key ->
            stores.remove(key)?.viewModelStore?.clear()
        }
    }

    override fun onCleared() {
        stores.values.forEach { it.viewModelStore.clear() }
        stores.clear()
    }
}

/** Keep each displayed video's composition and native view through its page and mini-player animations. */
@Composable
internal fun RetainedVideoPages(
    models: RetainedVideoPageViewModel,
    videoIds: List<Int>,
    retainedIds: Set<Int>,
    sessionRevision: Long,
    activeVideoId: Int?,
    content: @Composable (Int) -> Unit,
) {
    key(sessionRevision) {
        var savedIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
        val savedStates = rememberSaveableStateHolder()
        SideEffect {
            savedIds.filter { it !in retainedIds }.forEach { id ->
                savedStates.removeState(id)
            }
            savedIds = retainedIds.toList()
        }
        // Each keyed subtree keeps its own Surface as the new page joins the closing mini player.
        for (videoId in videoIds) {
            key(videoId) {
                val isActive = videoId == activeVideoId
                val lifecycleOwner = rememberLifecycleOwner(
                    maxLifecycle = if (isActive) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
                )
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides models.owner(sessionRevision, videoId),
                    LocalLifecycleOwner provides lifecycleOwner,
                    LocalStatusBarScrollToTopEnabled provides isActive,
                ) {
                    savedStates.SaveableStateProvider(videoId) { content(videoId) }
                }
            }
        }
    }
}
