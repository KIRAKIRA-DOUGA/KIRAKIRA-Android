package moe.kirakira.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Keep the animation above loading and visibility branches, so revealing controls never replays it. */
@Composable
internal fun rememberPlaybackIconMotion(
    playing: Boolean,
): PlaybackIconMotion {
    val motion = remember { PlaybackIconMotion(playing) }
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(playing, spatialSpec) { motion.animateTo(playing, spatialSpec) }
    return motion
}

@Composable
internal fun AnimatedPlaybackIcon(
    motion: PlaybackIconMotion,
    description: String,
    modifier: Modifier = Modifier,
) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp).semantics { contentDescription = description }) {
        scale(size.width / 960f, size.height / 960f, pivot = Offset.Zero) {
            drawPath(motion.path(), color)
        }
    }
}

internal class PlaybackIconMotion(initialPlaying: Boolean) {
    private val progress = Animatable(1f)
    private var playing = initialPlaying
    private var start = geometry(initialPlaying)
    private var end = start.copyOf()
    private val current = start.copyOf()
    private val drawingPath = Path()
    private var rotating = false

    suspend fun animateTo(playing: Boolean, animationSpec: FiniteAnimationSpec<Float>) {
        if (this.playing == playing) return
        this.playing = playing
        // Capture the displayed geometry before resetting the clock, including an interrupted turn.
        evaluate()
        val source = current.copyOf()
        val target = geometry(playing).also { rotate(it, -90f) }
        val matchedTarget = matchContours(source, target)
        progress.snapTo(0f)
        start = source
        end = matchedTarget
        rotating = true
        progress.animateTo(1f, animationSpec)
        // No renderer swap: both endpoints use the same exact Material Symbols contours.
        evaluate()
        start = current.copyOf()
        end = start.copyOf()
        rotating = false
    }

    fun path(): Path {
        evaluate()
        drawingPath.rewind()
        var index = 0
        repeat(2) {
            drawingPath.moveTo(current[index++], current[index++])
            repeat(SEGMENTS) {
                drawingPath.quadraticTo(
                    current[index++], current[index++], current[index++], current[index++],
                )
            }
            drawingPath.close()
        }
        return drawingPath
    }

    private fun evaluate() {
        val fraction = progress.value
        for (index in current.indices) {
            current[index] = start[index] + (end[index] - start[index]) * fraction
        }
        if (rotating) rotate(current, 90f * fraction)
    }
}

private const val SEGMENTS = 16
private const val CONTOUR_SIZE = 2 + SEGMENTS * 4

private fun rotate(points: FloatArray, degrees: Float) {
    val radians = Math.toRadians(degrees.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    for (index in points.indices step 2) {
        val x = points[index] - 480f
        val y = points[index + 1] - 480f
        points[index] = 480f + x * cosine - y * sine
        points[index + 1] = 480f + x * sine + y * cosine
    }
}

/** Match closed contours and their four side boundaries without reversing their winding. */
private fun matchContours(source: FloatArray, target: FloatArray): FloatArray {
    var bestScore = Float.POSITIVE_INFINITY
    var best = target
    for (swap in 0..1) {
        val candidate = FloatArray(target.size)
        var score = 0f
        for (contour in 0..1) {
            val sourceOffset = contour * CONTOUR_SIZE
            val targetOffset = (contour xor swap) * CONTOUR_SIZE
            var contourScore = Float.POSITIVE_INFINITY
            var bestContour = FloatArray(CONTOUR_SIZE)
            for (side in 0..3) {
                val shifted = FloatArray(CONTOUR_SIZE)
                val firstSegment = side * 4
                val previous = (firstSegment + SEGMENTS - 1) % SEGMENTS
                shifted[0] = target[targetOffset + 2 + previous * 4 + 2]
                shifted[1] = target[targetOffset + 2 + previous * 4 + 3]
                repeat(SEGMENTS) { segment ->
                    val from = targetOffset + 2 + ((firstSegment + segment) % SEGMENTS) * 4
                    target.copyInto(shifted, 2 + segment * 4, from, from + 4)
                }
                var distance = 0f
                for (index in shifted.indices) {
                    val delta = source[sourceOffset + index] - shifted[index]
                    distance += delta * delta
                }
                if (distance < contourScore) {
                    contourScore = distance
                    bestContour = shifted
                }
            }
            bestContour.copyInto(candidate, sourceOffset)
            score += contourScore
        }
        if (score < bestScore) {
            bestScore = score
            best = candidate
        }
    }
    return best
}

private fun geometry(playing: Boolean): FloatArray = (if (playing) PAUSE else PLAY).copyOf()

/** Construction runs once. Every side occupies four quadratic segments in clockwise order. */
private class ContourBuilder(x: Float, y: Float) {
    private val points = mutableListOf(x, y)

    fun quadratic(cx: Float, cy: Float, x: Float, y: Float) {
        points.addAll(listOf(cx, cy, x, y))
    }

    fun line(x: Float, y: Float, segments: Int = 4) {
        val startX = points[points.size - 2]
        val startY = points.last()
        repeat(segments) { index ->
            val middle = (index + 0.5f) / segments
            val end = (index + 1f) / segments
            quadratic(
                startX + (x - startX) * middle, startY + (y - startY) * middle,
                startX + (x - startX) * end, startY + (y - startY) * end,
            )
        }
    }

    fun build(): FloatArray {
        check(points.size == CONTOUR_SIZE)
        check(points[0] == points[points.size - 2] && points[1] == points.last())
        return points.toFloatArray()
    }
}

// Exact official Rounded FILL 1 geometry split along y=480, not a replacement polygon.
// Each half has four corresponding sides; both halves retain their original curved outer edges.
private val PLAY = ContourBuilder(320f, 273f).apply {
    quadratic(320f, 256f, 332f, 244.5f)
    quadratic(344f, 233f, 360f, 233f)
    quadratic(365f, 233f, 370.5f, 234.5f)
    quadratic(376f, 236f, 381f, 239f)
    line(707f, 446f, 2)
    quadratic(716f, 452f, 720.5f, 461f)
    quadratic(725f, 470f, 725f, 480f)
    line(320f, 480f)
    line(320f, 273f)
}.build() + ContourBuilder(320f, 480f).apply {
    line(725f, 480f, 4)
    quadratic(725f, 490f, 720.5f, 499f)
    quadratic(716f, 508f, 707f, 514f)
    line(381f, 721f, 2)
    quadratic(376f, 724f, 370.5f, 725.5f)
    quadratic(365f, 727f, 360f, 727f)
    quadratic(344f, 727f, 332f, 715.5f)
    quadratic(320f, 704f, 320f, 687f)
    line(320f, 480f)
}.build()

private fun pauseBar(left: Float): FloatArray = ContourBuilder(left, 280f).apply {
    quadratic(left, 247f, left + 23.5f, 223.5f)
    quadratic(left + 47f, 200f, left + 80f, 200f)
    quadratic(left + 113f, 200f, left + 136.5f, 223.5f)
    quadratic(left + 160f, 247f, left + 160f, 280f)
    line(left + 160f, 680f)
    quadratic(left + 160f, 713f, left + 136.5f, 736.5f)
    quadratic(left + 113f, 760f, left + 80f, 760f)
    quadratic(left + 47f, 760f, left + 23.5f, 736.5f)
    quadratic(left, 713f, left, 680f)
    line(left, 280f)
}.build()

private val PAUSE = pauseBar(240f) + pauseBar(560f)
