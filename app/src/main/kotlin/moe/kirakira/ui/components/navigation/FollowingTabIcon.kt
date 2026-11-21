package moe.kirakira.ui.components.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.components.icon.readVectorPathData
import moe.kirakira.ui.components.icon.vectorPathContours

private const val LAYER_SPAN = 0.8f

@Composable
internal fun FollowingTabIcon(
    icon: NavigationIcon,
    selected: Boolean,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val cerasus = icon == NavigationIcon.CERASUS_FOLLOWING
    val paths = remember(resources, icon) {
        val contours = vectorPathContours(readVectorPathData(resources, icon.resource).first())
        // Cerasus orders its contours outer/center/inner; Material Symbols uses outer/inner/center.
        val order = if (cerasus) listOf(1, 2, 0) else listOf(2, 1, 0)
        order.map { PathParser().parsePathString(contours[it]).toPath() }
    }
    val viewport = if (cerasus) 24f else 960f
    val center = Offset(viewport / 2f, viewport * (13f / 24f))
    val motion = rememberTabSelectionMotion(selected, durationMillis = 460)
    Canvas(modifier = modifier.size(24.dp)) {
        val color = tint()
        val t = motion.progress.value
        val amplitude = motion.amplitude.value
        scale(size.width / viewport, size.height / viewport, pivot = Offset.Zero) {
            paths.forEachIndexed { index, path ->
                val layer = followingPulseLayers[index]
                // Both assets use the same pulse from center to inner ring to outer ring.
                val local = ((t - layer.delay) / LAYER_SPAN).coerceIn(0f, 1f)
                val layerScale = 1f + (layer.track.at(local) - 1f) * amplitude
                scale(layerScale, pivot = center) {
                    drawPath(path, color)
                }
            }
        }
    }
}

private class FollowingPulseLayer(
    val delay: Float,
    peakScale: Float,
    reboundScale: Float,
) {
    val track = MotionTrack(
        0f, 1f, 0.18f, 0.82f, 0.43f, peakScale, 0.7f, reboundScale, 1f, 1f,
    )
}

private val followingPulseLayers = listOf(
    FollowingPulseLayer(delay = 0f, peakScale = 1.4f, reboundScale = 0.92f),
    FollowingPulseLayer(delay = 0.1f, peakScale = 1.2f, reboundScale = 0.96f),
    FollowingPulseLayer(delay = 0.2f, peakScale = 1.13f, reboundScale = 0.98f),
)
