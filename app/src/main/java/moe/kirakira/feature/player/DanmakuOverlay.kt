package moe.kirakira.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import kotlin.math.ceil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import moe.kirakira.data.content.DanmakuEntry
import moe.kirakira.data.content.DanmakuFontSize
import moe.kirakira.feature.settings.DanmakuSettings

private data class DanmakuSprite(
    val flight: DanmakuFlight,
    val text: TextLayoutResult,
    val outline: Brush?,
)

private data class DanmakuFrame(val sprites: List<DanmakuSprite> = emptyList(), val positionMs: Long = 0)

/** A passive drawing layer. Media3 is the sole clock; page-level progress polling is never used. */
@Composable
internal fun DanmakuOverlay(
    player: Player,
    entries: List<DanmakuEntry>,
    settings: DanmakuSettings,
    contentKey: Any,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var videoSize by remember(player, contentKey) { mutableStateOf(player.videoSize) }
    val wake = remember(player, contentKey) { Channel<Unit>(Channel.CONFLATED) }
    val viewport = remember(canvasSize, videoSize) { fittedVideoRect(canvasSize, videoSize) }
    // Replacing this state also removes an old frame immediately on pool/account/layout changes.
    val frame = remember(player, contentKey, entries, settings, viewport, density) { mutableStateOf(DanmakuFrame()) }

    DisposableEffect(player, wake) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                videoSize = player.videoSize
                wake.trySend(Unit)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, contentKey, entries, settings, viewport, density, measurer) {
        if (viewport.width <= 0 || viewport.height <= 0 || entries.isEmpty() || !settings.enabled) return@LaunchedEffect
        val styles = DanmakuFontSize.entries.associateWith { size ->
            val baseSize = when (size) {
                DanmakuFontSize.SMALL -> 12
                DanmakuFontSize.MEDIUM -> 16
                DanmakuFontSize.LARGE -> 22
            }
            TextStyle(
                fontSize = (baseSize * settings.fontScalePercent / 100f).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 1.25.em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            )
        }
        fun measure(entry: DanmakuEntry): TextLayoutResult = measurer.measure(
            text = entry.text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' '),
            style = styles.getValue(entry.style.fontSize),
            softWrap = false,
            maxLines = 1,
            // Keep paragraph dimensions representable even for oversized server text at large font scales.
            constraints = Constraints(maxWidth = 32_760),
            overflow = TextOverflow.Ellipsis,
        )
        val timeline = buildDanmakuTimeline(
            entries, settings, viewport.width, viewport.height * settings.areaPercent / 100f,
            horizontalGap = with(density) { 6.dp.toPx() },
            verticalGap = with(density) { 2.dp.toPx() },
            baseScrollSpeedPxPerSecond = with(density) { 144.dp.toPx() },
        ) { entry -> measure(entry).size }
        val cached = mutableMapOf<DanmakuFlight, DanmakuSprite>()
        while (currentCoroutineContext().isActive) {
            val position = player.currentPosition.coerceAtLeast(0)
            val visible = timeline.visibleAt(position)
            val visibleSet = visible.toSet()
            cached.keys.retainAll(visibleSet)
            val sprites = visible.map { flight ->
                cached.getOrPut(flight) {
                    val text = measure(flight.entry)
                    DanmakuSprite(
                        flight, text,
                        if (flight.entry.style.enableRainbow) Brush.horizontalGradient(
                            listOf(Color(0xFFF2509E), Color(0xFF308BCD)),
                            endX = text.size.width.toFloat().coerceAtLeast(1f),
                        ) else null,
                    )
                }
            }
            frame.value = DanmakuFrame(sprites, position)
            when {
                !player.isPlaying -> wake.receive()
                sprites.isNotEmpty() -> withFrameNanos { }
                else -> {
                    val next = timeline.nextStartAfter(position)
                    if (next == null) wake.receive()
                    else {
                        val delayMs = ceil((next - position) / player.playbackParameters.speed.toDouble())
                            .coerceIn(1.0, 60_000.0).toLong()
                        withTimeoutOrNull(delayMs) { wake.receive() }
                    }
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize().onSizeChanged { canvasSize = it }.clearAndSetSemantics { }) {
        val current = frame.value
        val opacity = settings.opacityPercent / 100f
        translate(viewport.left, viewport.top) {
            clipRect(right = viewport.width, bottom = viewport.height * settings.areaPercent / 100f) {
                current.sprites.forEach { sprite ->
                    val flight = sprite.flight
                    translate(flight.leftAt(current.positionMs, viewport.width), flight.top) {
                        val outline = sprite.outline
                        if (outline != null) {
                            drawText(
                                sprite.text, brush = outline, topLeft = Offset.Zero,
                                alpha = opacity, shadow = Shadow.None, drawStyle = Stroke(width = 4f),
                            )
                        }
                        // Cached paragraphs retain paint state: null does not reset a previous Stroke to Fill.
                        drawText(
                            sprite.text,
                            color = Color(0xFF000000.toInt() or flight.entry.style.color),
                            alpha = opacity,
                            shadow = if (outline == null) Shadow(Color.Black, blurRadius = 3f) else Shadow.None,
                            drawStyle = Fill,
                        )
                    }
                }
            }
        }
    }
}

private fun fittedVideoRect(canvas: IntSize, video: VideoSize): Rect {
    if (canvas.width <= 0 || canvas.height <= 0 || video.width <= 0 || video.height <= 0) return Rect.Zero
    val ratio = video.width.toFloat() * video.pixelWidthHeightRatio / video.height
    if (!ratio.isFinite() || ratio <= 0) return Rect.Zero
    val width = minOf(canvas.width.toFloat(), canvas.height * ratio)
    val height = width / ratio
    val left = (canvas.width - width) / 2f
    val top = (canvas.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}
