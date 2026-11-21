package moe.kirakira.ui.components.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.components.readVectorPathData
import moe.kirakira.ui.components.vectorPathContours

// Coordinates are in the original Cerasus icon's 24-unit viewport.
private val dialCenter = Offset(13f, 12f)
private val handPivot = Offset(12.75f, 12.55f)
private val minuteHandTip = Offset(12.75f, 8.75f)
private val hourHandTip = Offset(16.03f, 14.5f)
private const val MOTION_DURATION_MILLIS = 420
private const val HAND_WIDTH = 1.5f

private val rewindAngle = MotionTrack(0f, 0f, 0.2f, 6f, 0.46f, -30f, 0.72f, 3f, 1f, 0f)

@Composable
internal fun HistoryTabIcon(
    selected: Boolean,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val arrowRing = remember(resources) {
        val path = readVectorPathData(resources, NavigationIcon.HISTORY.resource).first()
        PathParser().parsePathString(vectorPathContours(path).first()).toPath()
    }
    val motion = rememberTabSelectionMotion(selected, durationMillis = MOTION_DURATION_MILLIS)
    val turns = rememberNavigationIconRotationSteps(selected, durationMillis = MOTION_DURATION_MILLIS)
    Canvas(modifier.size(24.dp)) {
        val color = tint()
        val turnAngle = -360f * (turns.value % 1f)
        val minuteAngle = motion.sample(rewindAngle, 0f)
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            scale(motion.sample(navigationIconPulseScale, 1f), pivot = dialCenter) {
                rotate(turnAngle, pivot = dialCenter) {
                    drawPath(arrowRing, color)
                }
                rotate(turnAngle, pivot = handPivot) {
                    drawLine(color, handPivot, hourHandTip, strokeWidth = HAND_WIDTH, cap = StrokeCap.Round)
                }
                rotate(minuteAngle, pivot = handPivot) {
                    drawLine(color, handPivot, minuteHandTip, strokeWidth = HAND_WIDTH, cap = StrokeCap.Round)
                }
            }
        }
    }
}
