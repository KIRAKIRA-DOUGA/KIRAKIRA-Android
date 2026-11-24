package moe.kirakira.ui.navigation

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.navigationevent.NavigationEvent
import moe.kirakira.ui.components.video.VideoZoomState

/** Retained page geometry and layering; its native video view stays in one composition. */
internal class PlaybackViewportState {
    var windowOrigin by mutableStateOf(Offset.Zero)
    var pageViewport by mutableStateOf<PlaybackPageViewport?>(null)
    val cards = VideoZoomState()
    val coveringPageKeys = mutableStateMapOf<Any, Unit>()
    var navigationPage by mutableStateOf<PlaybackNavigationPage?>(null)
    var navigationReturn by mutableStateOf<(() -> NavigationPageTransform)?>(null)
    var sourceRailFrame by mutableStateOf<(() -> Rect?)?>(null)
    /** One push per retained video; a card return and the next expansion overlap. */
    val videoZoomPushes = mutableStateMapOf<Int, VideoZoomPush>()

    /** The strongest blur and dim, optionally ignoring one video so its own motion does not affect itself. */
    fun backgroundStrength(excludingVideoId: Int? = null): Float = strongest(excludingVideoId) { it.strength() }

    /** The deepest shrink, in units of the entry's full push; a return may start deeper than 1. */
    fun backgroundDepth(excludingVideoId: Int? = null): Float = strongest(excludingVideoId) { it.depth() }

    private inline fun strongest(excludingVideoId: Int?, read: (VideoZoomPush) -> Float): Float {
        var strongest = 0f
        for ((id, push) in videoZoomPushes) if (id != excludingVideoId) strongest = maxOf(strongest, read(push))
        return strongest.coerceAtLeast(0f)
    }
    var videoCoversSource by mutableStateOf<State<Boolean>?>(null)
    val navigatingPage: Boolean
        get() = navigationReturn != null || navigationPage?.isTransitioning?.invoke() == true
    var cardReturn by mutableStateOf<VideoCardReturn?>(null)
    val exitingVideoId: Int? get() = cardReturn?.videoId
    private var backPreview by mutableStateOf<VideoBackPreview?>(null)
    var canBackToMiniPlayer: () -> Boolean = { false }

    fun backPreviewFor(route: Any): VideoBackPreview? = backPreview?.takeIf { it.route == route }

    fun startBackPreview(route: Any, event: NavigationEvent): VideoBackPreview? {
        // A video returning to a video underneath stays on the stack until it lands.
        if (cardReturn != null) return null
        return VideoBackPreview(route, canBackToMiniPlayer()).apply { update(event) }.also { backPreview = it }
    }

    fun finishBackPreview(preview: VideoBackPreview, transferVelocity: Boolean) {
        if (backPreview !== preview) return
        if (transferVelocity) preview.finish()
        backPreview = null
    }
}

internal class VideoCardReturn(val videoId: Int, val sourceRoute: Any?)

internal class VideoBackPreview(val route: Any, val toMiniPlayer: Boolean) {
    var progress by mutableFloatStateOf(0f)
        private set
    var releaseVelocity = 0f
        private set
    private val tracker = VelocityTracker1D(isDataDifferential = false)

    fun update(event: NavigationEvent) {
        tracker.addDataPoint(event.sampleTimeMillis(), event.progress)
        progress = event.progress
    }

    fun finish() {
        releaseVelocity = tracker.calculateVelocity(maximumVelocity = 8f)
    }
}

/** How far a video pushes the page behind it: [strength] drives blur and dim, [depth] the shrink. */
internal class VideoZoomPush(val strength: () -> Float, val depth: () -> Float)

internal data class PlaybackPageViewport(val videoId: Int, val bounds: Rect)

internal class PlaybackNavigationPage(
    val transform: () -> NavigationPageTransform,
    val isTransitioning: () -> Boolean,
)
