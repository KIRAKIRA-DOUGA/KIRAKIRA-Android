package moe.kirakira.feature.player

import androidx.compose.ui.unit.IntSize
import moe.kirakira.ui.components.video.VideoZoomOrigin
import moe.kirakira.ui.navigation.PlaybackPageViewport
import moe.kirakira.ui.navigation.VideoCardReturn

/**
 * Geometry captured when a retiring session leaves, a closing mini player or a return still landing on
 * its card, so its exit animation outlives the page viewport.
 */
internal class MiniPlayerExitViewport(
    val session: MiniPlayerExit,
    val origin: VideoZoomOrigin?,
    val pageViewport: PlaybackPageViewport?,
    val mainNavigationSize: IntSize,
    val cardReturn: VideoCardReturn? = null,
)
