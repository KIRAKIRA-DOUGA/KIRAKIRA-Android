package moe.kirakira.feature.player

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.tan
import moe.kirakira.R
import org.xmlpull.v1.XmlPullParser

/** An outlined dial with a moving gap aligned to the official speed icon's needle contour. */
@Composable
internal fun PlaybackSpeedIcon(
    angle: () -> Float,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val needle = remember(resources) {
        // getXml also accepts compiled VectorDrawables; retain the official needle geometry.
        @SuppressLint("ResourceType")
        val data = resources.getXml(R.drawable.ic_symbol_speed).use { parser ->
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
        PathParser().parsePathString(data.substring(0, data.indexOf('M', startIndex = 1))).toPath()
    }
    val dial = remember { SpeedIconDial() }
    val color = LocalContentColor.current
    Canvas(modifier) {
        scale(size.width / 960f, size.height / 960f, pivot = Offset.Zero) {
            val displayedAngle = angle().coerceIn(150f, 360f)
            drawPath(
                dial.path(displayedAngle),
                color,
                style = Stroke(width = 80f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            // The source needle points at 315 degrees around the dial's (480, 560) pivot.
            rotate(displayedAngle - 315f, pivot = Offset(480f, 560f)) {
                // Leave room above the outline's bottom edge at the 150-degree zero position.
                scale(0.85f, 0.85f, pivot = Offset(480f, 560f)) {
                    drawPath(needle, color)
                }
            }
        }
    }
}

/** Follow the fixed round dial and flat base, cutting the same angular gap at every needle position. */
private class SpeedIconDial {
    private val radius = 360f
    private val baseOffset = 200f
    private val baseAngle = Math.toDegrees(asin((baseOffset / radius).toDouble())).toFloat()
    private val startAngle = 180f - baseAngle
    private val sweepAngle = 180f + 2f * baseAngle
    private val arcLength = radius * Math.toRadians(sweepAngle.toDouble()).toFloat()
    private val rightEdge = 480f + radius * cos(Math.toRadians(baseAngle.toDouble())).toFloat()
    private val measure = PathMeasure().apply {
        setPath(
            Path().apply {
                arcTo(Rect(120f, 200f, 840f, 920f), startAngle, sweepAngle, true)
                close()
            },
            false,
        )
    }
    private val result = Path()

    fun path(angle: Float): Path {
        val start = distanceAt(angle + 25f)
        val end = distanceAt(angle - 25f)
        result.rewind()
        if (end > start) {
            measure.getSegment(start, end, result, true)
        } else {
            measure.getSegment(start, measure.length, result, true)
            measure.getSegment(0f, end, result, false)
        }
        return result
    }

    private fun distanceAt(angle: Float): Float {
        val normalized = (angle - startAngle + 360f) % 360f + startAngle
        return if (normalized <= startAngle + sweepAngle) {
            radius * Math.toRadians((normalized - startAngle).toDouble()).toFloat()
        } else {
            val x = 480f + baseOffset / tan(Math.toRadians(normalized.toDouble())).toFloat()
            arcLength + rightEdge - x
        }.coerceIn(0f, measure.length)
    }
}
