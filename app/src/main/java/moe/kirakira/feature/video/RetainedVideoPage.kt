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

/** Retain page state and its native video view while its navigation entry becomes a mini player. */
@Composable
internal fun RetainedVideoPage(
    models: RetainedVideoPageViewModel,
    videoId: Int,
    retainedIds: Set<Int>,
    sessionRevision: Long,
    isActive: Boolean,
    content: @Composable () -> Unit,
) {
    key(sessionRevision) {
        var savedIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
        val savedStates = rememberSaveableStateHolder()
        val owner = models.owner(sessionRevision, videoId)
        SideEffect {
            savedIds.filter { it !in retainedIds }.forEach { id ->
                savedStates.removeState(id)
            }
            savedIds = retainedIds.toList()
        }
        val lifecycleOwner = rememberLifecycleOwner(
            maxLifecycle = if (isActive) Lifecycle.State.RESUMED else Lifecycle.State.STARTED,
        )
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides owner,
            LocalLifecycleOwner provides lifecycleOwner,
        ) {
            savedStates.SaveableStateProvider(videoId) { content() }
        }
    }
}
