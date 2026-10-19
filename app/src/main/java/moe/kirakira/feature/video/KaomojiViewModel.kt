package moe.kirakira.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import moe.kirakira.data.kaomoji.RecentKaomojiStore

/** Shared by video entries; the navigation host owns initialization and pending selections. */
internal class KaomojiViewModel(private val store: RecentKaomojiStore) : ViewModel() {
    val recent = store.recent

    init {
        viewModelScope.launch { store.load() }
    }

    fun record(text: String) {
        viewModelScope.launch { store.record(text) }
    }
}
