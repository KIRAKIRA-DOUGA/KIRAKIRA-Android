package moe.kirakira.feature.player

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import moe.kirakira.ui.components.video.VideoZoomOrigin
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
    var sourceRailFrame by mutableStateOf<(() -> Rect?)?>(null)
    var videoZoomBlurFraction by mutableStateOf<(() -> Float)?>(null)
    var videoCoversSource by mutableStateOf<State<Boolean>?>(null)
    val navigatingPage: Boolean
        get() = navigationReturn != null || navigationPage?.isTransitioning?.invoke() == true
    var exitingVideoId by mutableStateOf<Int?>(null)
    var backProgress by mutableStateOf<Float?>(null)
    val backPreviewActive by derivedStateOf(structuralEqualityPolicy()) { backProgress != null }
    var backToMiniPlayer = false
}

internal data class PlaybackPageViewport(val videoId: Int, val bounds: Rect)

internal class MiniPlayerExitViewport(
    val session: MiniPlayerExit,
    val origin: VideoZoomOrigin?,
    val pageViewport: PlaybackPageViewport?,
    val mainNavigationSize: IntSize,
)

internal class PlaybackNavigationPage(
    val transform: () -> NavigationPageTransform,
    val isTransitioning: () -> Boolean,
)
