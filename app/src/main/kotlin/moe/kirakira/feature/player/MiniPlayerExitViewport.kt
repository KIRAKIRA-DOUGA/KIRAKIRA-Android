package moe.kirakira.feature.player

import androidx.compose.ui.unit.IntSize
import moe.kirakira.ui.components.video.VideoZoomOrigin
import moe.kirakira.ui.navigation.PlaybackPageViewport

/** Geometry captured when the mini player leaves, so its exit animation outlives the page viewport. */
internal class MiniPlayerExitViewport(
    val session: MiniPlayerExit,
    val origin: VideoZoomOrigin?,
    val pageViewport: PlaybackPageViewport?,
    val mainNavigationSize: IntSize,
)
