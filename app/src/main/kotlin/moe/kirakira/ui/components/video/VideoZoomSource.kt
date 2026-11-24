package moe.kirakira.ui.components.video

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize

internal val LocalVideoZoomState = compositionLocalOf<VideoZoomState?> { null }
internal val LocalVideoZoomOwner = compositionLocalOf<Any?> { null }

private val LocalVideoZoomGroup = compositionLocalOf<VideoZoomGroupKey?> { null }

internal data class VideoZoomGroupKey(val owner: Any?, val list: Any?)

internal data class VideoZoomCardKey(val group: VideoZoomGroupKey, val videoId: Int)

internal class VideoZoomState {
    var origin by mutableStateOf<VideoZoomOrigin?>(null)
        private set
    var ownerProvider: (() -> Any?)? = null
    var onOpenVideo: ((Int, VideoZoomOrigin?, () -> Unit) -> Unit)? = null
    // A card return can still be landing after another video has been selected.
    private val hiddenOrigins = mutableStateListOf<VideoZoomOrigin>()
    private val sources = SceneRegistrations<VideoZoomCardKey, VideoZoomSource>()
    private val groups = SceneRegistrations<VideoZoomGroupKey, State<Boolean>>()

    fun attach(source: VideoZoomSource) {
        sources.attach(source.key, source)
    }

    fun detach(source: VideoZoomSource) {
        sources.detach(source.key, source)
    }

    fun attachGroup(key: VideoZoomGroupKey, shown: State<Boolean>) {
        groups.attach(key, shown)
    }

    fun detachGroup(key: VideoZoomGroupKey, shown: State<Boolean>) {
        groups.detach(key, shown)
    }

    fun canOpen(owner: Any?): Boolean = ownerProvider?.let { it() == owner } ?: true

    fun select(origin: VideoZoomOrigin?) {
        this.origin = origin
    }

    fun clear() = select(null)

    fun hideSource(origin: VideoZoomOrigin) {
        if (hiddenOrigins.none { it === origin }) hiddenOrigins += origin
    }

    fun showSource(origin: VideoZoomOrigin) {
        hiddenOrigins.removeAll { it === origin }
    }

    /**
     * Hides by card identity rather than by its layout node: a card rebuilt while
     * its page changes Scene has no position until its first layout, and would draw its own, possibly
     * still loading, cover in that frame.
     */
    fun hides(key: VideoZoomCardKey): Boolean = hiddenOrigins.any { it.key == key }

    fun liveBounds(origin: VideoZoomOrigin): Rect? = sources[origin.key]?.bounds()

    fun awaitingCardLayout(origin: VideoZoomOrigin): Boolean = sources[origin.key]?.laidOut == false

    /** Visibility belongs to the list so lazy item disposal cannot be mistaken for a tab switch. */
    fun shown(key: VideoZoomGroupKey): Boolean = key.list == null || groups[key]?.value == true

    private var press: CardPress? = null

    fun beginPress(pointer: PointerId) {
        press = CardPress(pointer)
    }

    fun notePress(pointer: PointerId, source: VideoZoomSource) {
        press?.takeIf { it.pointer == pointer }?.source = source
    }

    fun endPress() {
        press = null
    }

    fun openPressed() {
        val pressed = press?.source ?: return
        // A surviving card owns cancellation and release through its normal clickable.
        if (pressed.laidOut) return
        val replacement = sources[pressed.key]?.takeUnless { it === pressed } ?: return
        val bounds = replacement.bounds() ?: return
        replacement.open(bounds)
    }

    fun painter(origin: VideoZoomOrigin): Painter? = sources[origin.key]?.painter ?: origin.painter

    private class CardPress(val pointer: PointerId) {
        var source: VideoZoomSource? = null
    }
}

/** A page can be laid out in two Scenes during a handoff. Keep the older registration until it leaves. */
private class SceneRegistrations<K, V : Any> {
    private val current = mutableStateMapOf<K, V>()
    private val previous = mutableMapOf<K, MutableList<V>>()

    operator fun get(key: K): V? = current[key]

    fun attach(key: K, value: V) {
        current.put(key, value)?.let { previous.getOrPut(key) { mutableListOf() }.add(it) }
    }

    fun detach(key: K, value: V) {
        val others = previous[key]
        if (current[key] === value) {
            val replacement = others?.removeLastOrNull()
            if (replacement == null) current.remove(key) else current[key] = replacement
        } else {
            others?.removeAll { it === value }
        }
        if (others?.isEmpty() == true) previous.remove(key)
    }
}

internal class VideoZoomOrigin(
    val key: VideoZoomCardKey,
    val bounds: Rect,
    val image: String?,
    val shape: CornerBasedShape,
    val painter: Painter?,
) {
    val owner get() = key.group.owner
    val videoId get() = key.videoId
}

internal class VideoZoomSource(
    private val state: VideoZoomState,
    val key: VideoZoomCardKey,
    private val image: String?,
    private val shape: () -> CornerBasedShape,
    private val onClick: () -> Unit,
) {
    var coordinates: LayoutCoordinates? = null
    var painter by mutableStateOf<Painter?>(null)
    val hidden: Boolean
        get() = state.hides(key)

    /** The whole artwork as drawn, through every layer transform such as the background's shrink. */
    val laidOut: Boolean
        get() = coordinates?.isAttached == true

    fun bounds(): Rect? {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return null
        val visible = coordinates.boundsInWindow()
        if (visible.width <= 0 || visible.height <= 0) return null
        return coordinates.boundsInWindow(clipBounds = false)
    }

    fun notePress(pointer: PointerId) = state.notePress(pointer, this)

    private fun canOpen(): Boolean = state.canOpen(key.group.owner) && state.shown(key.group)

    fun open(bounds: Rect? = bounds()) {
        if (!canOpen()) return
        val origin = bounds?.let {
            // Retain the loaded image itself. A source Scene may be replaced during navigation,
            // which invalidates its GraphicsLayer even though its image should remain continuous.
            VideoZoomOrigin(key, it, image, shape(), painter)
        }
        val open = state.onOpenVideo
        if (open == null) {
            state.select(origin)
            onClick()
        } else {
            open(key.videoId, origin, onClick)
        }
    }
}

/**
 * Marks the card's tappable area, so a tap can still complete while a return covers the page. It only
 * observes presses; the card's own click handling is untouched.
 */
internal fun Modifier.videoZoomCard(source: VideoZoomSource?): Modifier =
    if (source == null) this else then(VideoZoomCardElement(source))

private data class VideoZoomCardElement(val source: VideoZoomSource) : ModifierNodeElement<VideoZoomCardNode>() {
    override fun create() = VideoZoomCardNode(source)

    override fun update(node: VideoZoomCardNode) {
        node.source = source
    }
}

private class VideoZoomCardNode(var source: VideoZoomSource) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial) return
        pointerEvent.changes.firstOrNull { it.changedToDown() }?.let { source.notePress(it.id) }
    }

    override fun onCancelPointerInput() = Unit
}

internal fun Modifier.videoZoomSource(source: VideoZoomSource?): Modifier {
    if (source == null) return this
    return onGloballyPositioned { source.coordinates = it }
        .drawWithContent { if (!source.hidden) drawContent() }
}

/** Completes a tap if the hit card's Scene was rebuilt while the finger was down. */
internal fun Modifier.completeVideoZoomTap(state: VideoZoomState, returningToCard: () -> Boolean): Modifier =
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            if (!returningToCard() || currentEvent.changes.any { it.id != down.id && it.pressed }) {
                return@awaitEachGesture
            }
            state.beginPress(down.id)
            try {
                // The card's own clickable consumes down on Main. Start observing after that event.
                awaitPointerEvent(PointerEventPass.Final)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed ||
                        event.changes.any { it.id != down.id && (it.pressed || it.previousPressed) } ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    ) break
                    if (!change.pressed) {
                        state.openPressed()
                        break
                    }
                }
            } finally {
                state.endPress()
            }
        }
    }

@Composable
internal fun videoZoomSourceGroup(list: Any, shown: Boolean): ProvidedValue<VideoZoomGroupKey?> {
    val state = LocalVideoZoomState.current
    val owner = LocalVideoZoomOwner.current
    val key = remember(owner, list) { VideoZoomGroupKey(owner, list) }
    val visible = rememberUpdatedState(shown)
    DisposableEffect(state, key, visible) {
        state?.attachGroup(key, visible)
        onDispose { state?.detachGroup(key, visible) }
    }
    return LocalVideoZoomGroup provides key
}

@Composable
internal fun rememberVideoZoomSource(
    videoId: Int?,
    image: String?,
    shape: CornerBasedShape,
    onClick: () -> Unit,
): VideoZoomSource? {
    val state = LocalVideoZoomState.current ?: return null
    if (videoId == null) return null
    val owner = LocalVideoZoomOwner.current
    val inheritedGroup = LocalVideoZoomGroup.current
    val group = remember(owner, inheritedGroup) {
        inheritedGroup?.takeIf { it.owner == owner } ?: VideoZoomGroupKey(owner, null)
    }
    val key = remember(group, videoId) { VideoZoomCardKey(group, videoId) }
    val latestShape by rememberUpdatedState(shape)
    val latestClick by rememberUpdatedState(onClick)
    val source = remember(state, key, image) {
        VideoZoomSource(state, key, image, { latestShape }, { latestClick() })
    }
    DisposableEffect(state, source) {
        state.attach(source)
        onDispose { state.detach(source) }
    }
    return source
}

@Composable
internal fun rememberVideoZoomPlaceholder(state: VideoZoomState, videoId: Int): Painter? {
    val origin = state.origin?.takeIf { it.videoId == videoId } ?: return null
    return state.painter(origin)
}
