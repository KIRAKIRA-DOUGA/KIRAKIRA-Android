package moe.kirakira.feature.settings

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PlaybackSettings(
    val autoPictureInPicture: Boolean = true,
    val autoplay: Boolean = false,
    val autoQuality: Boolean = true,
    val preferredVideoHeight: Int? = null,
)

private class PlaybackSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("kirakira_settings", Context.MODE_PRIVATE)

    fun read() = PlaybackSettings(
        autoPictureInPicture = preferences.getBoolean("playback_auto_pip", true),
        autoplay = preferences.getBoolean("playback_autoplay", false),
        autoQuality = preferences.getBoolean("playback_auto_quality", true),
        preferredVideoHeight = preferences.getInt("playback_video_height", 0).takeIf { it > 0 },
    )

    fun write(settings: PlaybackSettings) {
        preferences.edit {
            putBoolean("playback_auto_pip", settings.autoPictureInPicture)
            putBoolean("playback_autoplay", settings.autoplay)
            putBoolean("playback_auto_quality", settings.autoQuality)
            putInt("playback_video_height", settings.preferredVideoHeight ?: 0)
        }
    }
}

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

    fun setAutoPictureInPicture(enabled: Boolean) = update { copy(autoPictureInPicture = enabled) }
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
