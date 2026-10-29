package moe.kirakira.ui.components.video

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

internal val LocalVideoZoomState = compositionLocalOf<VideoZoomState?> { null }
internal val LocalVideoZoomOwner = compositionLocalOf<Any?> { null }

internal class VideoZoomState {
    var origin by mutableStateOf<VideoZoomOrigin?>(null)
        private set
    var ownerProvider: (() -> Any?)? = null
    private var hiddenOrigin by mutableStateOf<VideoZoomOrigin?>(null)
    private val sources = mutableStateMapOf<Pair<Any?, Int>, Map<Any, VideoZoomSource>>()

    fun attach(owner: Any?, id: Int, source: VideoZoomSource) {
        val key = owner to id
        sources[key] = sources[key].orEmpty() + (source.token to source)
    }

    fun detach(owner: Any?, id: Int, source: VideoZoomSource) {
        val key = owner to id
        val remaining = sources[key].orEmpty() - source.token
        if (remaining.isEmpty()) sources.remove(key) else sources[key] = remaining
    }

    fun canOpen(owner: Any?): Boolean = ownerProvider?.let { it() == owner } ?: true

    fun select(origin: VideoZoomOrigin) {
        hiddenOrigin = null
        this.origin = origin
    }

    fun clear() {
        hiddenOrigin = null
        origin = null
    }

    fun hideSource(origin: VideoZoomOrigin) {
        if (this.origin === origin) hiddenOrigin = origin
    }

    fun showSource(origin: VideoZoomOrigin) {
        if (hiddenOrigin === origin) hiddenOrigin = null
    }

    fun hides(participant: VideoZoomSource): Boolean = hiddenOrigin?.let { source(it) === participant } == true

    private fun source(origin: VideoZoomOrigin): VideoZoomSource? {
        val participants = sources[origin.owner to origin.videoId] ?: return null
        return participants[origin.token]?.takeIf { it.bounds() != null }
            ?: participants.values.lastOrNull { it.bounds() != null }
    }

    fun bounds(origin: VideoZoomOrigin): Rect = source(origin)?.bounds() ?: origin.bounds

    fun painter(origin: VideoZoomOrigin): Painter? = source(origin)?.painter ?: origin.painter
}

internal class VideoZoomOrigin(
    val owner: Any?,
    val videoId: Int,
    val token: Any,
    val bounds: Rect,
    val image: String?,
    val shape: CornerBasedShape,
    val painter: Painter?,
)

internal class VideoZoomSource(
    private val state: VideoZoomState,
    private val owner: Any?,
    private val videoId: Int,
    private val image: String?,
    private val shape: () -> CornerBasedShape,
) {
    var coordinates: LayoutCoordinates? = null
    var painter by mutableStateOf<Painter?>(null)
    val token = Any()
    val hidden: Boolean
        get() = state.hides(this)

    fun bounds(): Rect? {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return null
        val visible = coordinates.boundsInWindow()
        if (visible.width <= 0 || visible.height <= 0) return null
        val position = coordinates.positionInWindow()
        return Rect(
            position.x, position.y,
            position.x + coordinates.size.width, position.y + coordinates.size.height,
        )
    }

    fun open(onOpen: () -> Unit) {
        if (!state.canOpen(owner)) return
        val bounds = bounds()
        if (bounds != null) {
            // Retain the loaded image itself. A source Scene may be replaced during navigation,
            // which invalidates its GraphicsLayer even though its image should remain continuous.
            state.select(VideoZoomOrigin(owner, videoId, token, bounds, image, shape(), painter))
        } else {
            state.clear()
        }
        onOpen()
    }
}

internal fun Modifier.videoZoomSource(source: VideoZoomSource?): Modifier {
    if (source == null) return this
    return onGloballyPositioned { source.coordinates = it }
        .drawWithContent { if (!source.hidden) drawContent() }
}

@Composable
internal fun rememberVideoZoomSource(
    videoId: Int?,
    image: String?,
    shape: CornerBasedShape,
): VideoZoomSource? {
    val state = LocalVideoZoomState.current ?: return null
    val owner = LocalVideoZoomOwner.current
    if (videoId == null) return null
    val latestShape by rememberUpdatedState(shape)
    val source = remember(state, owner, videoId, image) {
        VideoZoomSource(state, owner, videoId, image, { latestShape })
    }
    DisposableEffect(state, source) {
        state.attach(owner, videoId, source)
        onDispose { state.detach(owner, videoId, source) }
    }
    return source
}

@Composable
internal fun rememberVideoZoomPlaceholder(state: VideoZoomState, videoId: Int): Painter? {
    val origin = state.origin?.takeIf { it.videoId == videoId } ?: return null
    return state.painter(origin)
}
