package moe.kirakira.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.DanmakuSettingsStore

/** Device-wide, non-sensitive preferences shared by the player and settings destination. */
internal class DanmakuSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow<DanmakuSettings?>(null)
    val settings = state.asStateFlow()
    private var store: DanmakuSettingsStore? = null

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val store = DanmakuSettingsStore(application)
                store to store.read()
            }
            store = loaded.first
            state.value = loaded.second
        }
    }

    fun setEnabled(enabled: Boolean) {
        state.value?.let { update(it.copy(enabled = enabled)) }
    }

    fun update(value: DanmakuSettings) {
        val store = store ?: return
        val next = value.normalized()
        if (next == state.value) return
        store.write(next)
        state.value = next
    }
}
