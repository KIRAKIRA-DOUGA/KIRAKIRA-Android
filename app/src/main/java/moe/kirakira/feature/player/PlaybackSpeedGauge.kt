package moe.kirakira.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

private const val ZERO_ANGLE = 150f
private const val MAX_ANGLE = 360f
private val gaugeSpeeds = listOf(0f, 0.25f, 0.5f, 1f, 2f, 4f)

@Composable
internal fun PlaybackSpeedGauge(
    speed: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val actualSpeed = if (playing) speed else 0f
    val angle by animateFloatAsState(
        targetValue = gaugeAngle(actualSpeed),
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "PlaybackSpeedGaugeAngle",
    )
    val description = stringResource(R.string.player_actual_speed_description, speedLabel(actualSpeed))
    val colorScheme = MaterialTheme.colorScheme
    val primary = colorScheme.primary
    val sliderColors = SliderDefaults.colors()
    val readoutLabel = speedLabel(speed)
    val readoutStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium)
    val labelColor = colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = labelColor)
    val textMeasurer = rememberTextMeasurer()
    val labels = gaugeSpeeds.map { value ->
        textMeasurer.measure(
            text = speedNumber(value),
            style = if (value == 1f) labelStyle.copy(fontWeight = FontWeight.SemiBold) else labelStyle,
            maxLines = 1,
        )
    }
    val majorAngles = gaugeSpeeds.map(::gaugeAngle)
    val readout = textMeasurer.measure(readoutLabel, readoutStyle, maxLines = 1)
    // Reserve the widest two-decimal readout so changing speed never changes the sheet height.
    val readoutBounds = listOf(
        readout,
        textMeasurer.measure(speedLabel(0.25f), readoutStyle, maxLines = 1),
        textMeasurer.measure(speedLabel(1.25f), readoutStyle, maxLines = 1),
        textMeasurer.measure(speedLabel(3.99f), readoutStyle, maxLines = 1),
    )
    val readoutWidth = readoutBounds.maxOf { it.size.width }.toFloat()
    val readoutHeight = readoutBounds.maxOf { it.size.height }.toFloat()
    val needlePath = remember {
        Path().apply {
            moveTo(0f, -8f)
            lineTo(94f, -6f)
            cubicTo(97.314f, -6f, 100f, -3.314f, 100f, 0f)
            cubicTo(100f, 3.314f, 97.314f, 6f, 94f, 6f)
            lineTo(0f, 8f)
            cubicTo(-4.418f, 8f, -8f, 4.418f, -8f, 0f)
            cubicTo(-8f, -4.418f, -4.418f, -8f, 0f, -8f)
            close()
        }
    }

    BoxWithConstraints(
        modifier.widthIn(max = 320.dp).fillMaxWidth(),
    ) {
        val density = LocalDensity.current
        val gap = with(density) { 8.dp.toPx() }
        val width = constraints.maxWidth.toFloat()
        val chartHeight = width / 1.7f
        val labelWidth = labels.maxOf { it.size.width }.toFloat()
        val labelHeight = labels.maxOf { it.size.height }.toFloat()
        val zeroSine = sin(Math.toRadians(ZERO_ANGLE.toDouble())).toFloat()
        val outerRadius = min(
            (width - 2 * labelWidth - 4 * gap) / 2,
            (chartHeight - 2 * labelHeight - 3 * gap) / (1 + zeroSine),
        ).coerceAtLeast(0f)
        val trackWidth = with(density) { 8.dp.toPx() }
        val radius = (outerRadius - trackWidth / 2).coerceAtLeast(0f)
        val center = Offset(width / 2, outerRadius + labelHeight + gap)
        // Keep the readout close to the pivot, below the paused needle at any text width.
        val readoutTop = center.y + max(
            with(density) { 24.dp.toPx() },
            (readoutWidth / 2f + with(density) { 6.dp.toPx() }) *
                tan(Math.toRadians((180f - ZERO_ANGLE).toDouble())).toFloat() + gap,
        )
        val height = max(chartHeight, readoutTop + readoutHeight + gap / 2)
        Box(Modifier.fillMaxWidth().height(with(density) { height.toDp() })) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }) {
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
                    val textSize = labels[index].size
                    val anchor = when (value) {
                        0f -> Offset(
                            gaugePoint(center, outerRadius + gap + textSize.width / 2f, ZERO_ANGLE).x,
                            center.y + max(outerRadius * zeroSine + gap, labelHeight + gap),
                        )
                        0.25f -> Offset(center.x - outerRadius - gap - textSize.width / 2f, center.y)
                        4f -> Offset(center.x + outerRadius + gap + textSize.width / 2f, center.y)
                        else -> {
                            val radians = Math.toRadians(gaugeAngle(value).toDouble())
                            val textRadius = (textSize.width * abs(cos(radians)).toFloat() +
                                textSize.height * abs(sin(radians)).toFloat()) / 2
                            gaugePoint(center, outerRadius + gap + textRadius, gaugeAngle(value))
                        }
                    }
                    val left = (anchor.x - textSize.width / 2f).coerceIn(0f, max(0f, size.width - textSize.width))
                    val top = (anchor.y - textSize.height / 2f).coerceIn(0f, max(0f, size.height - textSize.height))
                    Rect(left, top, left + textSize.width, top + textSize.height)
                }
                // Place essential labels first; optional labels must not overlap them or each other.
                val occupied = mutableListOf<Rect>()
                for (index in listOf(0, 1, 3, 5, 2, 4)) {
                    val bounds = labelBounds[index]
                    val optional = index == 2 || index == 4
                    if (optional && occupied.any { it.overlaps(bounds.inflate(gap)) }) continue
                    drawText(labels[index], topLeft = bounds.topLeft)
                    occupied += bounds
                }

                val needleScale = radius * 0.72f / 100f
                val needleHalfWidth = (needleScale * 8f).coerceIn(4.dp.toPx(), 6.dp.toPx())
                withTransform({
                    translate(center.x, center.y)
                    rotate(displayedAngle, pivot = Offset.Zero)
                    scale(needleScale, needleHalfWidth / 8f, pivot = Offset.Zero)
                }) {
                    drawPath(needlePath, primary)
                }
                drawCircle(primary, radius = needleHalfWidth + 1.dp.toPx(), center = center)
            }
            Text(
                readoutLabel,
                style = readoutStyle,
                color = colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = with(density) { readoutTop.toDp() }),
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

private fun gaugeAngle(speed: Float): Float =
    if (speed <= 0f) ZERO_ANGLE else 180f + 180f * (log2(speed.coerceIn(0.25f, 4f)) + 2f) / 4f

private fun gaugePoint(center: Offset, radius: Float, angle: Float): Offset {
    val radians = Math.toRadians(angle.toDouble())
    return center + Offset(cos(radians).toFloat() * radius, sin(radians).toFloat() * radius)
}

@Preview(locale = "zh", widthDp = 320)
@Preview(locale = "en", widthDp = 272, fontScale = 2f)
@Composable
private fun PlaybackSpeedGaugePreview() {
    KIRAKIRATheme {
        PlaybackSpeedGauge(speed = 1f, playing = true)
    }
}

@Preview(locale = "zh", widthDp = 272)
@Composable
private fun PausedPlaybackSpeedGaugePreview() {
    KIRAKIRATheme {
        PlaybackSpeedGauge(speed = 1f, playing = false)
    }
}
