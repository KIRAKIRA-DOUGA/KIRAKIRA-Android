package moe.kirakira.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import moe.kirakira.R
import moe.kirakira.feature.player.PlaybackSpeedRules.MAX_ANGLE
import moe.kirakira.feature.player.PlaybackSpeedRules.ZERO_ANGLE
import moe.kirakira.ui.components.rememberEmphasizedEasing
import moe.kirakira.ui.theme.KIRAKIRATheme

private val gaugeSpeeds = listOf(0f, PlaybackSpeedRules.range.start, 0.5f, 1f, 2f, PlaybackSpeedRules.range.endInclusive)

private class GaugeTickLabel(
    val speed: TextLayoutResult,
    val pitch: TextLayoutResult,
) {
    fun size(pitchReveal: Float): Size = Size(
        speed.size.width + (pitch.size.width - speed.size.width).coerceAtLeast(0) * pitchReveal,
        speed.size.height + pitch.size.height * pitchReveal,
    )
}

private class GaugeLayout(
    val geometry: PlaybackSpeedGaugeGeometry,
    val outerRadius: Float,
    val labelSizes: List<Size>,
    val zeroLabelAnchor: Offset,
    val readoutTop: Float,
    val height: Float,
)

private fun measureGaugeLayout(
    width: Float,
    gap: Float,
    trackWidth: Float,
    minReadoutOffset: Float,
    readoutSize: Size,
    labels: List<GaugeTickLabel>,
    pitchReveal: Float,
): GaugeLayout {
    val labelSizes = labels.map { it.size(pitchReveal) }
    val labelWidth = labelSizes.maxOf { it.width }
    val labelHeight = labelSizes.maxOf { it.height }
    val speedLabelHeight = labels.maxOf { it.speed.size.height }
    val chartHeight = width / 1.7f + 2f * (labelHeight - speedLabelHeight)
    val zeroSine = sin(Math.toRadians(ZERO_ANGLE.toDouble())).toFloat()
    val outerRadius = min(
        (width - 2 * labelWidth - 4 * gap) / 2,
        (chartHeight - 2 * labelHeight - 3 * gap) / (1 + zeroSine),
    ).coerceAtLeast(0f)
    val radius = (outerRadius - trackWidth / 2).coerceAtLeast(0f)
    val center = Offset(width / 2, outerRadius + labelHeight + gap)
    val zeroLabel = labelSizes.first()
    val zeroLabelAnchor = Offset(
        gaugePoint(center, outerRadius + gap + zeroLabel.width / 2f, ZERO_ANGLE).x,
        center.y + max(outerRadius * zeroSine + gap, labelHeight + gap),
    )
    val zeroLabelLeft = (zeroLabelAnchor.x - zeroLabel.width / 2f)
        .coerceIn(0f, max(0f, width - zeroLabel.width))
    val zeroLabelBottom = zeroLabelAnchor.y + zeroLabel.height / 2f
    val readoutLeft = center.x - readoutSize.width / 2f
    val baseReadoutTop = center.y + minReadoutOffset
    // Ease in the extra clearance when wide pitch labels approach the readout.
    val horizontalProximity = ((zeroLabelLeft + zeroLabel.width + gap - readoutLeft) / gap).coerceIn(0f, 1f)
    val extraClearance = (zeroLabelBottom + gap - baseReadoutTop).coerceAtLeast(0f)
    val readoutTop = baseReadoutTop + extraClearance * horizontalProximity * pitchReveal
    val height = max(chartHeight, max(readoutTop + readoutSize.height, zeroLabelBottom) + gap / 2f)
    return GaugeLayout(
        geometry = PlaybackSpeedGaugeGeometry(center, radius),
        outerRadius = outerRadius,
        labelSizes = labelSizes,
        zeroLabelAnchor = zeroLabelAnchor,
        readoutTop = readoutTop,
        height = height,
    )
}

private fun pitchShiftLabel(speed: Float): String {
    if (speed == 0f) return "−∞"
    val semitones = 12f * PlaybackSpeedRules.progress(speed)
    val rounded = round(semitones * 10f) / 10f
    return when {
        rounded < 0f -> "−${speedNumber(-rounded)}"
        rounded > 0f -> "+${speedNumber(rounded)}"
        else -> "0"
    }
}

/** Shared by the control icon and expanded gauge, including the paused/buffering zero position. */
@Composable
internal fun rememberPlaybackSpeedAngle(
    speed: Float,
    playing: Boolean,
    immediate: Boolean = false,
): State<Float> = animateFloatAsState(
    targetValue = PlaybackSpeedRules.angle(if (playing) speed else 0f),
    animationSpec = if (immediate) snap() else tween(durationMillis = 300, easing = rememberEmphasizedEasing()),
    label = "PlaybackSpeedAngle",
)

@Composable
internal fun PlaybackSpeedGauge(
    speed: Float,
    playing: Boolean,
    continuousSpeed: Boolean,
    onSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    sliderDragging: Boolean = false,
    showPitchShift: Boolean = false,
) {
    var gaugeDragging by remember { mutableStateOf(false) }
    val dragging = gaugeDragging || (!playing && sliderDragging)
    val angle by rememberPlaybackSpeedAngle(speed, playing || dragging, immediate = dragging)
    val pitchReveal by animateFloatAsState(
        targetValue = if (showPitchShift) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = rememberEmphasizedEasing()),
        label = "PlaybackSpeedPitchReveal",
    )
    val latestOnSpeed by rememberUpdatedState(onSpeed)
    val description = stringResource(R.string.player_speed)
    val colorScheme = MaterialTheme.colorScheme
    val primary = colorScheme.primary
    val sliderColors = SliderDefaults.colors()
    val readoutLabel = speedLabel(speed)
    val speedDescription = if (showPitchShift) {
        stringResource(R.string.player_speed_pitch_description, readoutLabel, pitchShiftLabel(speed))
    } else {
        readoutLabel
    }
    val adjustProgress: (Float) -> Boolean = { target ->
        val next = PlaybackSpeedRules.adjustedSpeed(speed, target, continuousSpeed)
        if (next != speed) onSpeed(next)
        next != speed
    }
    val readoutStyle = MaterialTheme.typography.headlineLarge.copy(
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = colorScheme.onSurfaceVariant)
    val pitchLabelStyle = MaterialTheme.typography.labelSmall.copy(
        color = colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Normal,
        textDirection = TextDirection.Ltr,
    )
    val textMeasurer = rememberTextMeasurer()
    val labels = remember(textMeasurer, labelStyle, pitchLabelStyle) {
        gaugeSpeeds.map { value ->
            val speedLabel = textMeasurer.measure(
                text = speedNumber(value),
                style = if (value == 1f) labelStyle.copy(fontWeight = FontWeight.SemiBold) else labelStyle,
                maxLines = 1,
            )
            val pitchLabel = textMeasurer.measure(text = pitchShiftLabel(value), style = pitchLabelStyle, maxLines = 1)
            GaugeTickLabel(speedLabel, pitchLabel)
        }
    }
    val majorAngles = gaugeSpeeds.map(PlaybackSpeedRules::angle)
    val readout = textMeasurer.measure(readoutLabel, readoutStyle, maxLines = 1)
    // Reserve the widest two-decimal readout so changing speed never changes the sheet height.
    val readoutBounds = listOf(
        readout,
        textMeasurer.measure(speedLabel(PlaybackSpeedRules.range.start), readoutStyle, maxLines = 1),
        textMeasurer.measure(speedLabel(1.25f), readoutStyle, maxLines = 1),
        textMeasurer.measure(speedLabel(3.99f), readoutStyle, maxLines = 1),
    )
    val readoutWidth = readoutBounds.maxOf { it.size.width }.toFloat()
    val readoutHeight = readoutBounds.maxOf { it.size.height }.toFloat()
    val needlePath = remember {
        Path().apply {
            moveTo(0f, -12.5f)
            lineTo(97.5f, -2.5f)
            cubicTo(98.881f, -2.5f, 100f, -1.381f, 100f, 0f)
            cubicTo(100f, 1.381f, 98.881f, 2.5f, 97.5f, 2.5f)
            lineTo(0f, 12.5f)
            cubicTo(-6.904f, 12.5f, -12.5f, 6.904f, -12.5f, 0f)
            cubicTo(-12.5f, -6.904f, -6.904f, -12.5f, 0f, -12.5f)
            close()
        }
    }

    BoxWithConstraints(
        modifier.widthIn(max = 320.dp).fillMaxWidth(),
    ) {
        val density = LocalDensity.current
        val gap = with(density) { 8.dp.toPx() }
        val width = constraints.maxWidth.toFloat()
        val trackWidth = with(density) { 8.dp.toPx() }
        // Keep the readout close to the pivot, below the paused needle at any text width.
        val minReadoutOffset = max(
            with(density) { 24.dp.toPx() },
            (readoutWidth / 2f + with(density) { 6.dp.toPx() }) *
                tan(Math.toRadians((180f - ZERO_ANGLE).toDouble())).toFloat() + gap,
        )
        val readoutSize = Size(readoutWidth, readoutHeight)
        val gaugeLayout by remember(width, gap, trackWidth, minReadoutOffset, readoutSize, labels) {
            derivedStateOf {
                measureGaugeLayout(width, gap, trackWidth, minReadoutOffset, readoutSize, labels, pitchReveal)
            }
        }
        val latestGaugeLayout by rememberUpdatedState { gaugeLayout }
        Box(
            Modifier.fillMaxWidth().layout { measurable, constraints ->
                val height = gaugeLayout.height.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            },
        ) {
            Canvas(
                Modifier.fillMaxSize()
                    .playbackSpeedDrag(
                        width = width,
                        geometry = { latestGaugeLayout().geometry },
                        displayedNeedleAngle = { angle },
                        onDraggingChange = { gaugeDragging = it },
                        onSpeed = { latestOnSpeed(it) },
                    )
                    .semantics {
                        contentDescription = description
                        stateDescription = speedDescription
                        progressBarRangeInfo = ProgressBarRangeInfo(PlaybackSpeedRules.progress(speed), PlaybackSpeedRules.progressRange)
                        setProgress(action = adjustProgress)
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val target = when (event.key) {
                            Key.DirectionRight, Key.DirectionUp -> PlaybackSpeedRules.progress(speed) + 0.1f
                            Key.DirectionLeft, Key.DirectionDown -> PlaybackSpeedRules.progress(speed) - 0.1f
                            Key.MoveHome -> PlaybackSpeedRules.progressRange.start
                            Key.MoveEnd -> PlaybackSpeedRules.progressRange.endInclusive
                            else -> return@onKeyEvent false
                        }
                        if (event.key == Key.MoveHome || event.key == Key.MoveEnd) {
                            onSpeed(PlaybackSpeedRules.speedAtProgress(target))
                        } else {
                            adjustProgress(target)
                        }
                        true
                    }
                    .focusable(),
            ) {
                val layout = gaugeLayout
                val (center, radius) = layout.geometry
                val outerRadius = layout.outerRadius
                val displayedAngle = angle.coerceIn(ZERO_ANGLE, MAX_ANGLE)
                val majorWidth = 2.dp.toPx()
                val clearance = majorWidth / 2 + 3.dp.toPx()
                val clearanceAngle = Math.toDegrees(
                    asin((clearance / max(radius, 1f)).coerceIn(0f, 1f)).toDouble(),
                ).toFloat()

                for ((startAngle, endAngle) in majorAngles.zipWithNext()) {
                    val start = startAngle + clearanceAngle
                    val end = endAngle - clearanceAngle
                    if (end <= start) continue
                    drawPath(
                        gaugeTrackPath(center, radius, trackWidth, start, end, 2.dp.toPx()),
                        sliderColors.inactiveTrackColor,
                    )
                    val activeEnd = min(displayedAngle, end)
                    if (activeEnd > start) {
                        drawPath(
                            gaugeTrackPath(center, radius, trackWidth, start, activeEnd, 2.dp.toPx()),
                            sliderColors.activeTrackColor,
                        )
                    }
                    val tickAngle = (startAngle + endAngle) / 2
                    val active = displayedAngle > ZERO_ANGLE && tickAngle <= displayedAngle
                    drawCircle(
                        color = if (active) sliderColors.activeTickColor else sliderColors.inactiveTickColor,
                        radius = SliderDefaults.TickSize.toPx() / 2,
                        center = gaugePoint(center, radius, tickAngle),
                    )
                }
                for (tickAngle in majorAngles) {
                    drawLine(
                        color = primary,
                        start = gaugePoint(center, max(0f, radius - 6.dp.toPx()), tickAngle),
                        end = gaugePoint(center, radius + 6.dp.toPx(), tickAngle),
                        strokeWidth = majorWidth,
                        cap = StrokeCap.Round,
                    )
                }

                val labelBounds = gaugeSpeeds.mapIndexed { index, value ->
                    val label = layout.labelSizes[index]
                    val anchor = when (value) {
                        0f -> layout.zeroLabelAnchor
                        PlaybackSpeedRules.range.start -> Offset(center.x - outerRadius - gap - label.width / 2f, center.y)
                        PlaybackSpeedRules.range.endInclusive -> Offset(center.x + outerRadius + gap + label.width / 2f, center.y)
                        else -> {
                            val tickAngle = majorAngles[index]
                            val radians = Math.toRadians(tickAngle.toDouble())
                            val textRadius = (label.width * abs(cos(radians)).toFloat() +
                                label.height * abs(sin(radians)).toFloat()) / 2
                            gaugePoint(center, outerRadius + gap + textRadius, tickAngle)
                        }
                    }
                    val left = (anchor.x - label.width / 2f).coerceIn(0f, max(0f, size.width - label.width))
                    val top = (anchor.y - label.height / 2f).coerceIn(0f, max(0f, size.height - label.height))
                    Rect(left, top, left + label.width, top + label.height)
                }
                // Place essential labels first; optional labels must not overlap them or each other.
                val occupied = mutableListOf<Rect>()
                for (index in listOf(0, 1, 3, 5, 2, 4)) {
                    val bounds = labelBounds[index]
                    val optional = index == 2 || index == 4
                    if (optional && occupied.any { it.overlaps(bounds.inflate(gap)) }) continue
                    val label = labels[index]
                    drawText(
                        label.speed,
                        topLeft = Offset(bounds.center.x - label.speed.size.width / 2f, bounds.top),
                    )
                    if (pitchReveal > 0f) {
                        val pitch = label.pitch
                        val left = (bounds.center.x - pitch.size.width / 2f)
                            .coerceIn(0f, max(0f, size.width - pitch.size.width))
                        val top = bounds.top + label.speed.size.height
                        drawText(
                            pitch,
                            topLeft = Offset(left, top),
                            alpha = pitchReveal,
                        )
                    }
                    occupied += bounds
                }

                val needleScale = layout.geometry.needleLength / 100f
                val needleHalfWidth = (needleScale * 12.5f).coerceIn(4.dp.toPx(), 8.dp.toPx())
                withTransform({
                    translate(center.x, center.y)
                    rotate(displayedAngle, pivot = Offset.Zero)
                    scale(needleScale, needleHalfWidth / 12.5f, pivot = Offset.Zero)
                }) {
                    drawPath(needlePath, primary)
                }
            }
            PlaybackSpeedText(
                speed = speed,
                style = readoutStyle,
                color = colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset { IntOffset(0, gaugeLayout.readoutTop.roundToInt()) },
            )
        }
    }
}

private fun gaugeTrackPath(
    center: Offset,
    radius: Float,
    width: Float,
    start: Float,
    end: Float,
    cornerRadius: Float,
): Path {
    val outer = radius + width / 2
    val inner = (radius - width / 2).coerceAtLeast(1f)
    val arcLength = inner * Math.toRadians((end - start).toDouble()).toFloat()
    val corner = min(cornerRadius, min(width / 2, arcLength / 2))
    val outerInset = Math.toDegrees((corner / outer.coerceAtLeast(1f)).toDouble()).toFloat()
    val innerInset = Math.toDegrees((corner / inner).toDouble()).toFloat()
    // Rounded sector corners retain a straight edge facing each major marker, like a Slider gap.
    return Path().apply {
        val outerStart = gaugePoint(center, outer - corner, start)
        moveTo(outerStart.x, outerStart.y)
        var bend = gaugePoint(center, outer, start)
        var endpoint = gaugePoint(center, outer, start + outerInset)
        quadraticTo(bend.x, bend.y, endpoint.x, endpoint.y)
        arcTo(
            Rect(center - Offset(outer, outer), Size(outer * 2, outer * 2)),
            start + outerInset, end - start - outerInset * 2, false,
        )
        bend = gaugePoint(center, outer, end)
        endpoint = gaugePoint(center, outer - corner, end)
        quadraticTo(bend.x, bend.y, endpoint.x, endpoint.y)
        endpoint = gaugePoint(center, inner + corner, end)
        lineTo(endpoint.x, endpoint.y)
        bend = gaugePoint(center, inner, end)
        endpoint = gaugePoint(center, inner, end - innerInset)
        quadraticTo(bend.x, bend.y, endpoint.x, endpoint.y)
        arcTo(
            Rect(center - Offset(inner, inner), Size(inner * 2, inner * 2)),
            end - innerInset, start - end + innerInset * 2, false,
        )
        bend = gaugePoint(center, inner, start)
        endpoint = gaugePoint(center, inner + corner, start)
        quadraticTo(bend.x, bend.y, endpoint.x, endpoint.y)
        close()
    }
}

internal fun gaugePoint(center: Offset, radius: Float, angle: Float): Offset {
    val radians = Math.toRadians(angle.toDouble())
    return center + Offset(cos(radians).toFloat() * radius, sin(radians).toFloat() * radius)
}

@Preview(locale = "zh", widthDp = 320)
@Preview(locale = "en", widthDp = 272, fontScale = 2f)
@Composable
private fun PlaybackSpeedGaugePreview() {
    KIRAKIRATheme {
        PlaybackSpeedGauge(speed = 1f, playing = true, continuousSpeed = false, onSpeed = {})
    }
}

@Preview(locale = "zh", widthDp = 272)
@Composable
private fun PausedPlaybackSpeedGaugePreview() {
    KIRAKIRATheme {
        PlaybackSpeedGauge(speed = 1f, playing = false, continuousSpeed = false, onSpeed = {})
    }
}
