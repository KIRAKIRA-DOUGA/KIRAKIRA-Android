package moe.kirakira.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import moe.kirakira.ui.components.video.VideoZoomState
import moe.kirakira.ui.navigation.NavigationPageTransform

/** Retained page geometry and layering; its native video view stays in one composition. */
internal class PlaybackViewportState {
    var windowOrigin by mutableStateOf(Offset.Zero)
    var pageViewport by mutableStateOf<PlaybackPageViewport?>(null)
    val cards = VideoZoomState()
    val coveringPageKeys = mutableStateMapOf<Any, Unit>()
    var navigationPage by mutableStateOf<PlaybackNavigationPage?>(null)
    var navigationReturn by mutableStateOf<(() -> NavigationPageTransform)?>(null)
    val navigatingPage: Boolean
        get() = navigationReturn != null || navigationPage?.isTransitioning?.invoke() == true
    var exitingVideoId by mutableStateOf<Int?>(null)
    var backProgress by mutableStateOf<Float?>(null)
    var backToMiniPlayer = false
}

internal data class PlaybackPageViewport(val videoId: Int, val bounds: Rect)

internal class PlaybackNavigationPage(
    val transform: () -> NavigationPageTransform,
    val isTransitioning: () -> Boolean,
)
