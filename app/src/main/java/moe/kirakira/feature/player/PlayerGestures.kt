package moe.kirakira.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.ui.components.video.durationText
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal data class PlayerGestureFeedback(
    val positionMs: Long? = null,
    val deltaMs: Long = 0,
    val volumePercent: Int? = null,
    val direction: Int = 1,
    val tapOrigin: Offset? = null,
    val tapSequence: Long = 0,
)

/** Transient input state, shared by the background and the shield above exiting controls. */
internal class PlayerGestureState(
    private val scope: CoroutineScope,
    private val position: () -> Long,
    private val duration: () -> Long,
    private val seek: (Long) -> Unit,
    private val readVolume: () -> Float?,
    private val changeVolume: (Float) -> Float?,
    private val singleTap: () -> Unit,
    private val togglePlayback: () -> Unit,
    private val interactionFinished: () -> Unit,
) {
    var dragging by mutableStateOf(false)
        private set
    val previewPositionMs: Long?
        get() = if (dragging) feedback?.positionMs else null

    var feedback by mutableStateOf<PlayerGestureFeedback?>(null)
        private set
    var feedbackVisible by mutableStateOf(false)
        private set
    private var pendingTap: Job? = null
    private var hideFeedback: Job? = null
    private var tapSide = 0
    private var tapTime = Long.MIN_VALUE
    private var repeated = false
    private var repeatedTarget = 0L
    private var accumulated = 0L
    private var tapSequence = 0L
    private var startPosition = 0L
    private var startVolume: Float? = null

    fun reset() {
        pendingTap?.cancel()
        hideFeedback?.cancel()
        pendingTap = null
        tapSide = 0
        repeated = false
        dragging = false
        feedbackVisible = false
        feedback = null
    }

    fun tap(side: Int, time: Long, timeout: Duration, minimumInterval: Duration, origin: Offset) {
        val interval = (time - tapTime).milliseconds
        val continuing = repeated && side != 0 && feedbackVisible && feedback?.tapOrigin != null
        val allowedInterval = if (continuing) 800.milliseconds else timeout
        if (side == tapSide && interval in minimumInterval..allowedInterval) {
            pendingTap?.cancel()
            pendingTap = null
            if (side == 0) {
                togglePlayback()
                interactionFinished()
                tapTime = Long.MIN_VALUE
                repeated = false
                return
            }
            val before = if (repeated) repeatedTarget else position()
            val target = (before + side * 5_000L).coerceIn(0, duration())
            accumulated = if (repeated) accumulated + target - before else target - before
            repeatedTarget = target
            repeated = true
            seek(target)
            tapSequence++
            show(
                PlayerGestureFeedback(
                    positionMs = target,
                    deltaMs = accumulated,
                    direction = side,
                    tapOrigin = origin,
                    tapSequence = tapSequence,
                ),
            )
            finishFeedback()
        } else {
            // Resolve an earlier single tap before starting another sequence.
            if (pendingTap != null) {
                pendingTap?.cancel()
                singleTap()
            }
            repeated = false
            pendingTap = scope.launch {
                delay(timeout)
                pendingTap = null
                singleTap()
            }
        }
        tapSide = side
        tapTime = time
    }

    fun startDrag() {
        pendingTap?.cancel()
        pendingTap = null
        repeated = false
        tapSide = 0
        hideFeedback?.cancel()
        feedback = null
        feedbackVisible = false
        dragging = true
        startPosition = position()
        startVolume = readVolume()
    }

    fun drag(horizontal: Boolean, delta: Offset, width: Int, height: Int) {
        if (horizontal) {
            val target = (startPosition + (delta.x / width.coerceAtLeast(1) *
                duration().coerceAtMost(90_000L)).toLong()).coerceIn(0, duration())
            show(
                PlayerGestureFeedback(
                    positionMs = target,
                    deltaMs = target - startPosition,
                    direction = if (delta.x < 0) -1 else 1,
                ),
            )
        } else {
            startVolume?.let { initial ->
                changeVolume((initial - delta.y * 1.5f / height.coerceAtLeast(1)).coerceIn(0f, 1f))?.let {
                    show(PlayerGestureFeedback(volumePercent = (it * 100).roundToInt()))
                }
            }
        }
    }

    fun endDrag(horizontal: Boolean, commit: Boolean) {
        if (commit && horizontal) feedback?.positionMs?.let(seek)
        dragging = false
        if (commit) finishFeedback() else {
            feedbackVisible = false
            interactionFinished()
        }
    }

    private fun show(value: PlayerGestureFeedback) {
        hideFeedback?.cancel()
        feedback = value
        feedbackVisible = true
    }

    private fun finishFeedback() {
        interactionFinished()
        hideFeedback = scope.launch {
            delay(800.milliseconds)
            feedbackVisible = false
        }
    }
}

internal fun Modifier.playerGestures(
    state: PlayerGestureState,
    enabled: Boolean,
    seekable: Boolean,
    identity: Any,
): Modifier = pointerInput(state, enabled, seekable, identity) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown()
        val edge = 24.dp.toPx()
        val edgeStart = down.position.x < edge || down.position.x > size.width - edge
        var horizontal: Boolean? = null
        var ended = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.size != 1 || change.isConsumed) break
                val delta = change.position - down.position
                if (horizontal == null && delta.getDistance() > viewConfiguration.touchSlop) {
                    val isHorizontal = abs(delta.x) > abs(delta.y)
                    if (edgeStart || (isHorizontal && !seekable) ||
                        (!isHorizontal && down.position.x < size.width / 2f)
                    ) break
                    horizontal = isHorizontal
                    state.startDrag()
                }
                if (horizontal != null) {
                    change.consume()
                    state.drag(horizontal, delta, size.width, size.height)
                }
                if (!change.pressed) {
                    if (horizontal != null) {
                        state.endDrag(horizontal, commit = true)
                    } else if (change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                        change.consume()
                        val side = when {
                            !seekable -> 0
                            change.position.x < size.width / 3f -> -1
                            change.position.x > size.width * 2f / 3f -> 1
                            else -> 0
                        }
                        state.tap(
                            side, change.uptimeMillis, viewConfiguration.doubleTapTimeoutMillis.milliseconds,
                            viewConfiguration.doubleTapMinTimeMillis.milliseconds,
                            Offset(
                                change.position.x / size.width.coerceAtLeast(1),
                                change.position.y / size.height.coerceAtLeast(1),
                            ),
                        )
                    }
                    ended = true
                    break
                }
                // Children can claim a button/slider/scroll interaction later in this pass.
                if (horizontal == null && awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) break
            }
        } finally {
            if (!ended && horizontal != null) state.endDrag(horizontal, commit = false)
        }
    }
}

@Composable
internal fun PlayerGestureOverlay(state: PlayerGestureState, modifier: Modifier = Modifier) {
    val feedback = state.feedback
    BoxWithConstraints(modifier) {
        val volume = feedback?.volumePercent
        val sliderHeight = (maxHeight - 88.dp).coerceIn(16.dp, 160.dp)
        if (feedback?.tapOrigin != null) {
            PlayerDoubleTapOverlay(feedback, state.feedbackVisible, Modifier.matchParentSize())
        }
        AnimatedVisibility(
            visible = state.feedbackVisible && feedback != null && feedback.tapOrigin == null,
            modifier = Modifier
                .align(if (volume != null) Alignment.CenterEnd else Alignment.Center)
                .padding(horizontal = if (volume != null) 24.dp else 0.dp),
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            if (feedback != null && feedback.tapOrigin == null) {
                if (volume != null) {
                    PlayerVolumeHud(volume, sliderHeight)
                } else {
                    val label = stringResource(
                        if (feedback.direction < 0) R.string.player_gesture_rewind else R.string.player_gesture_forward,
                        abs(feedback.deltaMs) / 1_000,
                    )
                    val description = feedback.positionMs?.let { "$label, ${durationText(it)}" } ?: label
                    Column(
                        Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .clearAndSetSemantics { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painterResource(
                                if (feedback.direction < 0) R.drawable.ic_symbol_fast_rewind
                                else R.drawable.ic_symbol_fast_forward,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White,
                        )
                        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                        feedback.positionMs?.let {
                            Text(durationText(it), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerVolumeHud(percent: Int, sliderHeight: Dp) {
    val label = stringResource(R.string.player_gesture_volume, percent)
    val slider = rememberSliderState()
    SideEffect { slider.value = percent / 100f }
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val sliderColors = SliderDefaults.colors(
        disabledThumbColor = colors.primary,
        disabledActiveTrackColor = colors.primary,
        disabledInactiveTrackColor = colors.secondaryContainer,
    )
    val resources = LocalResources.current
    val volumeIcon = remember(resources) { VolumeIconMorph(resources) }
    val iconLevel = animateFloatAsState(
        targetValue = when {
            percent == 0 -> 0f
            percent <= 50 -> 1f
            else -> 2f
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "VolumeIconMorph",
    )
    Column(
        Modifier.clearAndSetSemantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Keep official slider geometry and disable input without dimming the HUD.
        VerticalSlider(
            state = slider,
            onValueChange = {},
            enabled = false,
            topToBottom = false,
            modifier = Modifier.height(sliderHeight),
            colors = sliderColors,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    isVertical = true,
                    enabled = false,
                    colors = sliderColors,
                    thumbSize = DpSize(48.dp, 4.dp),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    trackCornerSize = 10.dp,
                    modifier = Modifier
                        .width(40.dp)
                        .drawWithContent {
                            drawContent()
                            val iconSize = 24.dp.toPx()
                            val iconX = (size.width - iconSize) / 2
                            val iconY = 8.dp.toPx()
                            val activeTop = (size.height * (1f - percent / 100f) + 8.dp.toPx())
                                .coerceIn(0f, size.height)
                            // The icon stays upright and uses the foreground of each track segment.
                            clipRect(bottom = activeTop) {
                                translate(iconX, iconY) {
                                    scale(iconSize / 960f, iconSize / 960f, Offset.Zero) {
                                        volumeIcon.draw(this, iconLevel.value, colors.onSecondaryContainer)
                                    }
                                }
                            }
                            clipRect(top = activeTop) {
                                translate(iconX, iconY) {
                                    scale(iconSize / 960f, iconSize / 960f, Offset.Zero) {
                                        volumeIcon.draw(this, iconLevel.value, colors.onPrimary)
                                    }
                                }
                            }
                        },
                    enabled = false,
                    colors = sliderColors,
                    thumbTrackGapSize = 6.dp,
                    trackInsideCornerSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
        )
        Text(
            stringResource(R.string.player_gesture_volume_value, percent),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
