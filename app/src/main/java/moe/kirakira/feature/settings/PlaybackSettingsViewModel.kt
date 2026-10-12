package moe.kirakira.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.data.settings.PlaybackSettingsStore

internal class PlaybackSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow<PlaybackSettings?>(null)
    val settings = state.asStateFlow()
    private lateinit var store: PlaybackSettingsStore

    init {
        viewModelScope.launch {
            state.value = withContext(Dispatchers.IO) {
                store = PlaybackSettingsStore(application)
                store.read()
            }
        }
    }

    fun setOutsideAppMiniPlayer(enabled: Boolean) = update { copy(outsideAppMiniPlayer = enabled) }
    fun setInAppMiniPlayer(enabled: Boolean) = update { copy(inAppMiniPlayer = enabled) }
    fun setAutoplay(enabled: Boolean) = update { copy(autoplay = enabled) }

    fun setQuality(height: Int?) = update {
        copy(autoQuality = height == null, preferredVideoHeight = height ?: preferredVideoHeight)
    }

    private fun update(change: PlaybackSettings.() -> PlaybackSettings) {
        val next = state.value?.change() ?: return
        // apply updates the in-memory preferences synchronously and schedules disk I/O off main.
        store.write(next)
        state.value = next
    }
}
