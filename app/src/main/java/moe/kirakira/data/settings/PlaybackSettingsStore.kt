package moe.kirakira.data.settings

import android.content.Context
import androidx.core.content.edit

internal class PlaybackSettingsStore(context: Context) {
    private val preferences = settingsPreferences(context)

    private val defaults = PlaybackSettings()

    fun read() = PlaybackSettings(
        outsideAppMiniPlayer = preferences.getBoolean("playback_outside_app_mini_player", defaults.outsideAppMiniPlayer),
        inAppMiniPlayer = preferences.getBoolean("playback_in_app_mini_player", defaults.inAppMiniPlayer),
        autoplay = preferences.getBoolean("playback_autoplay", defaults.autoplay),
        autoQuality = preferences.getBoolean("playback_auto_quality", defaults.autoQuality),
        preferredVideoHeight = preferences.getInt("playback_video_height", defaults.preferredVideoHeight ?: 0).takeIf { it > 0 },
    )

    fun write(settings: PlaybackSettings) {
        preferences.edit {
            putBoolean("playback_outside_app_mini_player", settings.outsideAppMiniPlayer)
            putBoolean("playback_in_app_mini_player", settings.inAppMiniPlayer)
            putBoolean("playback_autoplay", settings.autoplay)
            putBoolean("playback_auto_quality", settings.autoQuality)
            putInt("playback_video_height", settings.preferredVideoHeight ?: 0)
        }
    }
}
