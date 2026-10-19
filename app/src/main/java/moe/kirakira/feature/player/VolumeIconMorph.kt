package moe.kirakira.feature.player

import android.annotation.SuppressLint
import android.content.res.Resources
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.LayoutDirection
import moe.kirakira.R
import org.xmlpull.v1.XmlPullParser

/** Official volume_up contours; collapsing waves and shifting the speaker match down/mute geometry. */
internal class VolumeIconMorph(resources: Resources) {
    private val speaker: Path
    private val innerWave: Path
    private val outerWave: Path

    init {
        // getXml accepts compiled drawable XML too; these vector contours are needed for the morph.
        @SuppressLint("ResourceType")
        val data = resources.getXml(R.drawable.ic_symbol_volume_up).use { parser ->
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
        val contours = Regex("M[^M]+").findAll(data).map { it.value }.toList()
        fun path(data: String): Path = PathParser().parsePathString(data).toPath()
        outerWave = path(contours[0])
        speaker = path(contours[1])
        innerWave = path(contours[2])
    }

    /** Level 0/1/2 is zero/low/high; intermediate geometry is drawn without allocating paths. */
    fun draw(scope: DrawScope, level: Float, color: Color) = with(scope) {
        val progress = level.coerceIn(0f, 2f)
        scale(if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, 1f, Offset(480f, 480f)) {
            translate(left = (2f - progress) * 80f) {
                drawPath(speaker, color)
                val inner = progress.coerceIn(0f, 1f)
                if (inner > 0f) {
                    scale(inner, inner, Offset(560f, 480f)) { drawPath(innerWave, color) }
                }
                val outer = (progress - 1f).coerceIn(0f, 1f)
                if (outer > 0f) {
                    scale(outer, outer, Offset(600f, 480f)) { drawPath(outerWave, color) }
                }
            }
        }
    }
}
