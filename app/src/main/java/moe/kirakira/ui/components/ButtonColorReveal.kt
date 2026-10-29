package moe.kirakira.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MinimumInteractiveLeftAlignmentLine
import androidx.compose.material3.MinimumInteractiveTopAlignmentLine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

private const val REVEAL_DURATION_MILLIS = 600

/** A reversible wave keeps rapid toggles from accumulating fragmented foreground layers. */
internal class ButtonColorReveal(initialChecked: Boolean, initialColor: Color, initialDisabledColor: Color?) {
    var baseChecked by mutableStateOf(initialChecked)
    var baseColor by mutableStateOf(initialColor)
    var disabledColor by mutableStateOf(initialDisabledColor)
    var layers by mutableStateOf(listOf<ButtonColorWave>())
    var returningToBase by mutableStateOf(false)
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    var iconCoordinates by mutableStateOf<LayoutCoordinates?>(null)
    var visualInset by mutableStateOf(IntOffset.Zero)

    fun shadowColor(): Color {
        val base = materialShadowColor(baseColor)
        val wave = layers.singleOrNull() ?: return base
        return lerp(base, materialShadowColor(wave.color), wave.progress.value.coerceIn(0f, 1f))
    }

    fun shadowOpacity(unchecked: Float = 1f, checked: Float = 1f): Float {
        if (disabledColor != null) return 0f
        val base = if (baseChecked) checked else unchecked
        val wave = layers.singleOrNull() ?: return base
        val target = if (wave.checked) checked else unchecked
        // Losing a shadow starts immediately; gaining one waits until the reveal's tail.
        // Keep the mapping tied to the same wave so reversing never jumps in opacity.
        val phase = if (target < base) wave.progress.value / 0.3f
        else (wave.progress.value - 0.7f) / 0.3f
        val progress = phase.coerceIn(0f, 1f)
        val fraction = progress * progress * (3f - 2f * progress)
        return base + (target - base) * fraction
    }

    fun origin(width: Float, height: Float): Offset {
        val root = coordinates
        val icon = iconCoordinates
        if (root == null || icon == null || !root.isAttached || !icon.isAttached) {
            return Offset(width / 2f, height / 2f)
        }
        return root.localPositionOf(icon, Offset(icon.size.width / 2f, icon.size.height / 2f)) -
            Offset(visualInset.x.toFloat(), visualInset.y.toFloat())
    }
}

internal class ButtonColorWave(val checked: Boolean, val color: Color) {
    val progress = Animatable(0f)
}

@Composable
internal fun rememberButtonColorReveal(
    checked: Boolean,
    color: Color,
    disabledColor: Color,
    enabled: Boolean,
): ButtonColorReveal {
    val reveal = remember { ButtonColorReveal(checked, color, disabledColor.takeUnless { enabled }) }
    val motionSpec = tween<Float>(durationMillis = REVEAL_DURATION_MILLIS, easing = FastOutSlowInEasing)
    LaunchedEffect(checked, color, disabledColor, enabled) {
        if (!enabled) {
            // Disabled appearance is temporary; retain the previous state as the reveal's base.
            reveal.disabledColor = disabledColor
        } else {
            reveal.disabledColor = null
            // A follow request may disable the button before its checked state is confirmed.
            if (checked != reveal.baseChecked || color != reveal.baseColor || reveal.layers.isNotEmpty()) {
                val activeWave = reveal.layers.singleOrNull()
                val returningToBase = activeWave != null && checked == reveal.baseChecked && color == reveal.baseColor
                val wave = when {
                    returningToBase -> activeWave
                    activeWave != null && activeWave.checked == checked && activeWave.color == color -> activeWave
                    else -> {
                        // A palette change can introduce a third color. Keep only one source
                        // and destination even then, rather than retaining frozen circles.
                        if (activeWave != null) {
                            reveal.baseChecked = activeWave.checked
                            reveal.baseColor = activeWave.color
                        }
                        ButtonColorWave(checked, color).also { reveal.layers = listOf(it) }
                    }
                }
                reveal.returningToBase = returningToBase
                // Reverse the same radius; another state change must not add another mask.
                val targetProgress = if (returningToBase) 0f else 1f
                val remainingDuration = (REVEAL_DURATION_MILLIS * abs(targetProgress - wave.progress.value))
                    .toInt().coerceAtLeast(1)
                wave.progress.animateTo(
                    targetProgress,
                    if (remainingDuration == REVEAL_DURATION_MILLIS) motionSpec
                    else tween(remainingDuration, easing = FastOutSlowInEasing),
                )
                reveal.baseColor = color
                reveal.baseChecked = checked
                reveal.layers = emptyList()
                reveal.returningToBase = false
            }
        }
    }
    return reveal
}

@Composable
internal fun Modifier.buttonColorReveal(shape: Shape, reveal: ButtonColorReveal): Modifier {
    return observeButtonMeasurement { placeable ->
        reveal.visualInset = IntOffset(
            placeable[MinimumInteractiveLeftAlignmentLine].coerceAtLeast(0),
            placeable[MinimumInteractiveTopAlignmentLine].coerceAtLeast(0),
        )
    }.onGloballyPositioned { reveal.coordinates = it }.drawBehind {
        val inset = reveal.visualInset
        val width = (size.width - inset.x * 2f).coerceAtLeast(0f)
        val height = (size.height - inset.y * 2f).coerceAtLeast(0f)
        val path = Path().apply {
            addOutline(shape.createOutline(Size(width, height), layoutDirection, this@drawBehind))
        }
        translate(inset.x.toFloat(), inset.y.toFloat()) {
            clipPath(path) {
                val disabledColor = reveal.disabledColor
                if (disabledColor != null) {
                    drawRect(disabledColor)
                    return@clipPath
                }
                drawRect(reveal.baseColor)
                reveal.layers.forEach { wave ->
                    val origin = reveal.origin(width, height)
                    val radius = hypot(max(origin.x, width - origin.x), max(origin.y, height - origin.y))
                    drawCircle(wave.color, radius * wave.progress.value.coerceIn(0f, 1f), origin)
                }
            }
        }
    }
}

/** Each foreground branch is clipped by the very same circles as the background. */
@Composable
internal fun ButtonRevealContent(
    reveal: ButtonColorReveal,
    modifier: Modifier = Modifier,
    content: @Composable (Boolean) -> Unit,
) {
    val waves = if (reveal.disabledColor == null) reveal.layers else emptyList()
    val targetIndex = if (reveal.returningToBase) 0 else waves.size
    Layout(
        modifier = modifier.animateContentSize(
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            alignment = Alignment.Center,
        ),
        content = {
            // Null is the settled base; the same wave branch survives direction changes.
            (listOf<ButtonColorWave?>(null) + waves).forEachIndexed { index, wave ->
                key(wave) {
                    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    Box(
                        modifier = Modifier
                            .then(if (index != targetIndex) Modifier.clearAndSetSemantics {} else Modifier)
                            .onGloballyPositioned { coordinates = it }
                            .drawWithContent {
                                val root = reveal.coordinates
                                val local = coordinates
                                if (root == null || local == null || !root.isAttached || !local.isAttached) {
                                    return@drawWithContent
                                }
                                val inset = reveal.visualInset
                                val width = (root.size.width - inset.x * 2f).coerceAtLeast(0f)
                                val height = (root.size.height - inset.y * 2f).coerceAtLeast(0f)
                                fun circle(layer: ButtonColorWave): Path {
                                    val origin = reveal.origin(width, height)
                                    val center = local.localPositionOf(
                                        root,
                                        origin + Offset(inset.x.toFloat(), inset.y.toFloat()),
                                    )
                                    val radius = hypot(max(origin.x, width - origin.x), max(origin.y, height - origin.y)) *
                                        layer.progress.value.coerceIn(0f, 1f)
                                    return Path().apply { addOval(Rect(center, radius)) }
                                }
                                var mask = wave?.let { circle(it) } ?: Path().apply {
                                    addRect(Rect(Offset.Zero, size))
                                }
                                waves.drop(index).forEach { later ->
                                    mask = Path.combine(PathOperation.Difference, mask, circle(later))
                                }
                                clipPath(mask) { this@drawWithContent.drawContent() }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        content(wave?.checked ?: reveal.baseChecked)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val target = placeables[targetIndex]
        layout(target.width, target.height) {
            placeables.forEach { it.placeRelative((target.width - it.width) / 2, (target.height - it.height) / 2) }
        }
    }
}
