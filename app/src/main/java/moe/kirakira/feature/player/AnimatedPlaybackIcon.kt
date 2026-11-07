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
import androidx.compose.ui.geometry.lerp
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
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
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
                drawingPath.cubicTo(
                    current[index++], current[index++],
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
private const val SEGMENT_SIZE = 6
private const val CONTOUR_SIZE = 2 + SEGMENTS * SEGMENT_SIZE

private fun rotate(points: FloatArray, degrees: Float) {
    val radians = Math.toRadians(degrees.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    for (index in points.indices step 2) {
        val x = points[index] - 12f
        val y = points[index + 1] - 12f
        points[index] = 12f + x * cosine - y * sine
        points[index + 1] = 12f + x * sine + y * cosine
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
                val previousEnd = targetOffset + (previous + 1) * SEGMENT_SIZE
                shifted[0] = target[previousEnd]
                shifted[1] = target[previousEnd + 1]
                repeat(SEGMENTS) { segment ->
                    val from = targetOffset + 2 + ((firstSegment + segment) % SEGMENTS) * SEGMENT_SIZE
                    target.copyInto(shifted, 2 + segment * SEGMENT_SIZE, from, from + SEGMENT_SIZE)
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

/** Construction runs once. Every side occupies four cubic segments in clockwise order. */
private class ContourBuilder(x: Float, y: Float) {
    private val points = mutableListOf(x, y)
    private val endPoint get() = Offset(points[points.size - 2], points.last())

    fun curve(first: Offset, second: Offset, end: Offset, splits: Int = 0) {
        if (splits == 0) {
            points.addAll(listOf(first.x, first.y, second.x, second.y, end.x, end.y))
            return
        }
        // Subdivide the source curve exactly so both shapes have matching side boundaries.
        val startControl = lerp(endPoint, first, 0.5f)
        val middleControl = lerp(first, second, 0.5f)
        val endControl = lerp(second, end, 0.5f)
        val leftControl = lerp(startControl, middleControl, 0.5f)
        val rightControl = lerp(middleControl, endControl, 0.5f)
        val midpoint = lerp(leftControl, rightControl, 0.5f)
        curve(startControl, leftControl, midpoint, splits - 1)
        curve(rightControl, endControl, end, splits - 1)
    }

    fun line(x: Float, y: Float, segments: Int = 4) {
        val start = endPoint
        val end = Offset(x, y)
        repeat(segments) { index ->
            curve(
                lerp(start, end, (index + 1f / 3f) / segments),
                lerp(start, end, (index + 2f / 3f) / segments),
                if (index + 1 == segments) end else lerp(start, end, (index + 1f) / segments),
            )
        }
    }

    fun build(): FloatArray {
        check(points.size == CONTOUR_SIZE)
        check(points[0] == points[points.size - 2] && points[1] == points.last())
        return points.toFloatArray()
    }
}

// Cerasus play.svg, split at the midpoint of the rounded tip to keep its silhouette.
private const val PLAY_TIP_X = 18.999975f
private const val PLAY_SPLIT_Y = 11.998575f

private val PLAY = ContourBuilder(7f, 6.13402f).apply {
    curve(Offset(7f, 5.2394f), Offset(8.02908f, 4.69583f), Offset(8.82159f, 5.18278f), splits = 2)
    line(18.45f, 11.0374f, 2)
    curve(Offset(18.81665f, 11.2639f), Offset(PLAY_TIP_X, 11.63195f), Offset(PLAY_TIP_X, PLAY_SPLIT_Y), splits = 1)
    line(7f, PLAY_SPLIT_Y)
    line(7f, 6.13402f)
}.build() + ContourBuilder(7f, PLAY_SPLIT_Y).apply {
    line(PLAY_TIP_X, PLAY_SPLIT_Y)
    curve(Offset(PLAY_TIP_X, 12.3652f), Offset(18.81665f, 12.7304f), Offset(18.45f, 12.9512f), splits = 1)
    line(8.82159f, 18.8172f, 2)
    curve(Offset(8.02908f, 19.3042f), Offset(7f, 18.7606f), Offset(7f, 17.866f), splits = 2)
    line(7f, PLAY_SPLIT_Y)
}.build()

// Cerasus pause.svg, using the same winding as the play contours.
private fun pauseBar(left: Float): FloatArray = ContourBuilder(left, 7f).apply {
    val right = left + 4f
    curve(Offset(left, 5.9f), Offset(left + 0.9f, 5f), Offset(left + 2f, 5f), splits = 1)
    curve(Offset(right - 0.9f, 5f), Offset(right, 5.9f), Offset(right, 7f), splits = 1)
    line(right, 17f)
    curve(Offset(right, 18.1f), Offset(right - 0.9f, 19f), Offset(left + 2f, 19f), splits = 1)
    curve(Offset(left + 0.9f, 19f), Offset(left, 18.1f), Offset(left, 17f), splits = 1)
    line(left, 7f)
}.build()

private val PAUSE = pauseBar(6f) + pauseBar(14f)
