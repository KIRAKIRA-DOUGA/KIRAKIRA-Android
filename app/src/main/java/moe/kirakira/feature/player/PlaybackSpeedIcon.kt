package moe.kirakira.feature.player

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import moe.kirakira.R
import moe.kirakira.ui.components.readVectorPathData
import moe.kirakira.ui.components.vectorPathContours

/** The filled Cerasus speed dial with a rotating needle cutout. */
@Composable
internal fun PlaybackSpeedIcon(
    angle: () -> Float,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val (needle, dial) = remember(resources) {
        val data = readVectorPathData(resources, R.drawable.ic_icon_speed).first()
        val (needleData, dialData) = vectorPathContours(data)
        val needle = PathParser().parsePathString(needleData).toPath().asAndroidPath()
        val dial = PathParser().parsePathString(dialData).toPath()
        needle to dial
    }
    val iconPath = remember { Path() }
    val needleRotation = remember { Matrix() }
    val color = LocalContentColor.current
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val displayedAngle = angle().coerceIn(PlaybackSpeedRules.ZERO_ANGLE, PlaybackSpeedRules.MAX_ANGLE)
            // The source needle points at 315 degrees around the dial's (12, 14) pivot.
            needleRotation.setRotate(displayedAngle - 315f, 12f, 14f)
            iconPath.rewind()
            iconPath.addPath(dial)
            // The two contours wind in opposite directions, so the needle cuts out the dial.
            iconPath.asAndroidPath().addPath(needle, needleRotation)
            drawPath(iconPath, color)
        }
    }
}
