package moe.kirakira.data.settings

internal data class PlaybackSettings(
    val outsideAppMiniPlayer: Boolean = true,
    val inAppMiniPlayer: Boolean = true,
    val autoplay: Boolean = false,
    val autoQuality: Boolean = true,
    val preferredVideoHeight: Int? = null,
)
