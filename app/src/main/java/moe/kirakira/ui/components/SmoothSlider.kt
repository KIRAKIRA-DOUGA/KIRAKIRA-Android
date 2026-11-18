package moe.kirakira.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.requestFocus
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Animates track presses without interpolating input values or delaying completion callbacks. */
@Composable
internal fun SmoothSlider(
    state: SliderState,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = {
        SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled)
    },
    track: @Composable (SliderState) -> Unit = {
        SliderDefaults.Track(sliderState = it, colors = colors, enabled = enabled)
    },
) {
    val scope = rememberCoroutineScope()
    val motion = remember(state, scope) { SliderClickMotion(state, scope) }
    val inputSource = remember(interactionSource, motion) {
        SliderInputInteractionSource(interactionSource, motion)
    }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val focusRequester = remember { FocusRequester() }
    val hasFocus by interactionSource.collectIsFocusedAsState()
    val dragging = state.isDragging
    val inputChange = motion.inputChange
    val easing = rememberEmphasizedEasing()
    val animationSpec = remember(easing) { tween<Float>(durationMillis = 180, easing = easing) }

    SideEffect {
        // Read after the caller's SideEffect has applied any step or domain normalization.
        motion.synchronize(state.value, dragging, enabled, inputChange, animationSpec)
    }
    DisposableEffect(motion) {
        onDispose { motion.cancel() }
    }

    Box(
        modifier = modifier
            .semantics {
                if (!enabled) disabled()
                stateDescription = "${(state.value * 100).roundToInt() / 100f}"
                if (enabled) {
                    focused = hasFocus
                    requestFocus { focusRequester.requestFocus() }
                }
                setProgress { target ->
                    val next = state.accessibleValue(target)
                    if (!enabled || next == state.value) {
                        false
                    } else {
                        motion.recordInput(animate = false)
                        onValueChange(next)
                        onValueChangeFinished?.invoke()
                        true
                    }
                }
            }
            .progressSemantics(state.value, state.trackRange, state.steps),
        propagateMinConstraints = true,
    ) {
        // A separate official Slider preserves thumb, tick and gap geometry during interpolation.
        // Its slots retain normal colors and interactions, while the host has no input or semantics.
        Slider(
            state = motion.displayState,
            onValueChange = {},
            modifier = Modifier.clearAndSetSemantics { },
            enabled = false,
            interactionSource = interactionSource,
            thumb = thumb,
            track = track,
        )
        // This is the only input/focus target. Hiding drawing keeps native hit testing and keys
        // tied to the real value; the root exposes the same value to accessibility services.
        Slider(
            state = state,
            onValueChange = { value ->
                // A handled press already delivered its target; native onTap must not replay it.
                if (!inputSource.pressApplied || state.isDragging) {
                    motion.recordInput(animate = inputSource.consumeTap() && !state.isDragging)
                    onValueChange(value)
                }
            },
            onValueChangeFinished = {
                inputSource.clearTap()
                onValueChangeFinished?.invoke()
            },
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent {
                    inputSource.clearTap()
                    false
                }
                .pointerInput(state, inputSource, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        // Native Slider records the press offset in Main. Final consumes that
                        // offset in the same down event, preserving its RTL and step geometry.
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                        inputSource.applyPress(state) { currentOnValueChange(it) }
                    }
                }
                .clearAndSetSemantics { }
                .drawWithContent { },
            enabled = enabled,
            interactionSource = inputSource,
            thumb = thumb,
            track = track,
        )
    }
}

private class SliderInputChange(val animate: Boolean)

private class SliderClickMotion(
    private val state: SliderState,
    private val scope: CoroutineScope,
) {
    val displayState = SliderState(
        value = state.value.coerceIn(state.trackRange),
        steps = state.steps,
        trackRange = state.trackRange,
    ).apply { shouldAutoSnap = false }

    var inputChange: SliderInputChange? by mutableStateOf(null)
        private set

    private val position = Animatable(displayState.value)
    private var targetValue = displayState.value
    private var handledChange: SliderInputChange? = null
    private var animation: Job? = null

    fun recordInput(animate: Boolean) {
        inputChange = SliderInputChange(animate)
    }

    fun beginDrag() {
        cancel()
        displayState.value = state.value.coerceIn(state.trackRange)
    }

    fun synchronize(
        value: Float,
        dragging: Boolean,
        enabled: Boolean,
        change: SliderInputChange?,
        animationSpec: FiniteAnimationSpec<Float>,
    ) {
        val target = value.coerceIn(state.trackRange)
        val animate = change !== handledChange && change?.animate == true
        handledChange = change
        if (dragging || !enabled) {
            cancel()
            targetValue = target
            displayState.value = target
            return
        }
        if (target == targetValue) return
        targetValue = target
        cancel()
        if (animate) {
            animation = scope.launch {
                position.snapTo(displayState.value)
                position.animateTo(target, animationSpec) {
                    displayState.value = this.value
                }
            }
        } else {
            displayState.value = target
        }
    }

    fun cancel() {
        animation?.cancel()
        animation = null
    }
}

/** Apply the native press offset on down and retain it until tap completion to prevent replay. */
private class SliderInputInteractionSource(
    private val source: MutableInteractionSource,
    private val motion: SliderClickMotion,
) : MutableInteractionSource by source {
    private var pendingPress: PressInteraction.Press? = null
    var pressApplied: Boolean = false
        private set

    override suspend fun emit(interaction: Interaction) {
        observe(interaction)
        source.emit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean {
        observe(interaction)
        return source.tryEmit(interaction)
    }

    fun consumeTap(): Boolean = (pendingPress != null).also { clearTap() }

    fun applyPress(state: SliderState, onValueChange: (Float) -> Unit) {
        if (pendingPress == null || pressApplied) return
        pressApplied = true
        val previous = state.value
        state.dispatchRawDelta(0f)
        val target = state.value
        // Only the caller may accept or normalize the proposed value, as with native input.
        state.value = previous
        if (target != previous) {
            motion.recordInput(animate = true)
            onValueChange(target)
        }
    }

    fun clearTap() {
        pendingPress = null
        pressApplied = false
    }

    private fun observe(interaction: Interaction) {
        when (interaction) {
            is PressInteraction.Press -> {
                pendingPress = interaction
                pressApplied = false
            }
            is PressInteraction.Cancel -> if (interaction.press === pendingPress) clearTap()
            is DragInteraction.Start -> {
                clearTap()
                motion.beginDrag()
            }
        }
    }
}

private fun SliderState.accessibleValue(target: Float): Float {
    val bounded = target.coerceIn(trackRange)
    if (steps == 0 || trackRange.start == trackRange.endInclusive) return bounded
    val fraction = (bounded - trackRange.start) / (trackRange.endInclusive - trackRange.start)
    val step = (fraction * (steps + 1)).roundToInt()
    return (trackRange.start + (trackRange.endInclusive - trackRange.start) * step / (steps + 1))
        .coerceIn(trackRange)
}
