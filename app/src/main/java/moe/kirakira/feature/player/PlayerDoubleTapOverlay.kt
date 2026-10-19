package moe.kirakira.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.kirakira.R
import org.xmlpull.v1.XmlPullParser

/** Decorative feedback only; the player underneath retains all pointer handling. */
@Composable
internal fun PlayerDoubleTapOverlay(
    feedback: PlayerGestureFeedback,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = remember(feedback.direction) { DoubleTapArrowMotion() }
    val region = remember(feedback.direction) { DoubleTapRegionShape(feedback.direction > 0) }
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(motion) { motion.play() }
    LaunchedEffect(motion, feedback.tapSequence) { motion.requestPulse() }
    val label = stringResource(
        if (feedback.direction < 0) R.string.player_gesture_rewind else R.string.player_gesture_forward,
        abs(feedback.deltaMs) / 1_000,
    )
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.clipToBounds(),
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = label }) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .clip(region)
                    .background(Color.Black.copy(alpha = 0.22f))
                    .background(Color.White.copy(alpha = 0.07f))
                    .indication(interactions, ripple(bounded = true, color = Color.White)),
            ) {
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                LaunchedEffect(feedback.tapSequence, width, height) {
                    val origin = feedback.tapOrigin ?: Offset(0.5f, 0.5f)
                    val press = PressInteraction.Press(
                        Offset(origin.x.coerceIn(0f, 1f) * width, origin.y.coerceIn(0f, 1f) * height),
                    )
                    var released = false
                    try {
                        interactions.emit(press)
                        delay(80.milliseconds)
                        interactions.emit(PressInteraction.Release(press))
                        released = true
                    } finally {
                        if (!released) interactions.tryEmit(PressInteraction.Cancel(press))
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(if (feedback.direction > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxWidth(0.4f)
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DoubleTapArrows(
                    direction = feedback.direction,
                    motion = motion,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = label,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Animate each contour in the official symbol in its stored inward-to-outward order. */
@Composable
private fun DoubleTapArrows(direction: Int, motion: DoubleTapArrowMotion, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val arrows = remember(resources, direction) {
        val resource = if (direction > 0) R.drawable.ic_symbol_fast_forward else R.drawable.ic_symbol_fast_rewind
        val data = resources.getXml(resource).use { parser ->
            var pathData = ""
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {
                    pathData = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData")
                    break
                }
                parser.next()
            }
            pathData
        }
        Regex("M[^M]+").findAll(data).map {
            val path = PathParser().parsePathString(it.value).toPath()
            path to path.getBounds().center
        }.toList()
    }
    Canvas(modifier) {
        val first = motion.arrows[0].value.coerceIn(0f, 1f)
        val second = motion.arrows[1].value.coerceIn(0f, 1f)
        val groupTravel = 3.dp.toPx() * (first + second) / 2f
        arrows.forEachIndexed { index, (path, center) ->
            val emphasis = if (index == 0) first else second
            // Shared movement adds momentum; small independent travel preserves the arrow gap.
            val displacement = groupTravel + 2.dp.toPx() * emphasis
            translate(left = displacement * direction) {
                scale(size.width / 960f, size.height / 960f, Offset.Zero) {
                    scale(1f + 0.04f * emphasis, 1f + 0.04f * emphasis, center) {
                        drawPath(path, Color.White.copy(alpha = 0.64f + 0.36f * emphasis))
                    }
                }
            }
        }
    }
}

/** A single consumer completes each beat; only the latest pending decorative pulse is retained. */
private class DoubleTapArrowMotion {
    val arrows = List(2) { Animatable(0f) }
    private val pulses = Channel<Unit>(Channel.CONFLATED)

    fun requestPulse() {
        pulses.trySend(Unit)
    }

    suspend fun play() {
        for (pulse in pulses) {
            coroutineScope {
                arrows.forEachIndexed { index, arrow ->
                    launch {
                        arrow.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 120,
                                delayMillis = index * 55,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                        arrow.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        )
                    }
                }
            }
        }
    }
}

/** Physical left/right shape: playback direction does not follow the UI reading direction. */
private class DoubleTapRegionShape(private val forward: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(
            Path().apply {
                addOval(
                    Rect(
                        left = if (forward) size.width * 0.52f else -size.width * 0.72f,
                        top = -size.height * 0.5f,
                        right = if (forward) size.width * 1.72f else size.width * 0.48f,
                        bottom = size.height * 1.5f,
                    ),
                )
            },
        )
}
