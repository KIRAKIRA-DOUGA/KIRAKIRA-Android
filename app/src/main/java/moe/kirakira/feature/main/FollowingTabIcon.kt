package moe.kirakira.feature.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun FollowingTabIcon(selected: Boolean, modifier: Modifier = Modifier) {
    val paths = remember {
        followingPulseLayers.map { PathParser.createPathFromPathData(it.pathData).asComposePath() }
    }
    val scales = remember { followingPulseLayers.map { Animatable(1f) } }
    var previouslySelected by remember { mutableStateOf(selected) }

    LaunchedEffect(selected) {
        if (previouslySelected == selected) return@LaunchedEffect
        previouslySelected = selected
        coroutineScope {
            followingPulseLayers.forEachIndexed { index, layer ->
                launch {
                    val scale = scales[index]
                    if (!selected) {
                        scale.animateTo(1f, tween(durationMillis = 120, easing = FastOutSlowInEasing))
                    } else {
                        val start = scale.value
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = keyframes {
                                durationMillis = layer.delayMillis + 300
                                start at 0 using FastOutSlowInEasing
                                // Stagger on the animation clock so system duration scaling also applies.
                                start at layer.delayMillis using FastOutSlowInEasing
                                layer.peakScale at layer.delayMillis + 90 using FastOutSlowInEasing
                                layer.reboundScale at layer.delayMillis + 190 using FastOutSlowInEasing
                                1f at durationMillis
                            },
                        )
                    }
                }
            }
        }
    }

    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        scale(size.width / 960f, size.height / 960f, pivot = Offset.Zero) {
            paths.forEachIndexed { index, path ->
                // All three official contours share the signal's center, not the icon box's center.
                scale(scales[index].value, pivot = Offset(480f, 520f)) {
                    drawPath(path, color)
                }
            }
        }
    }
}

private class FollowingPulseLayer(
    val delayMillis: Int,
    val peakScale: Float,
    val reboundScale: Float,
    val pathData: String,
)

// Unchanged center / inner / outer contours from ic_symbol_wifi_tethering.xml.
// Material Symbols Rounded (Apache 2.0); see third_party/material-symbols/README.md.
private val followingPulseLayers = listOf(
    FollowingPulseLayer(
        delayMillis = 0,
        peakScale = 1.22f,
        reboundScale = 0.95f,
        pathData = "M423.5,576.5Q400,553 400,520Q400,487 423.5,463.5Q447,440 480,440Q513,440 536.5,463.5" +
            "Q560,487 560,520Q560,553 536.5,576.5Q513,600 480,600Q447,600 423.5,576.5Z",
    ),
    FollowingPulseLayer(
        delayMillis = 70,
        peakScale = 1.14f,
        reboundScale = 0.97f,
        pathData = "M346,668Q334,680 317,680.5Q300,681 290,667Q267,636 253.5,599Q240,562 240,520" +
            "Q240,420 310,350Q380,280 480,280Q580,280 650,350Q720,420 720,520Q720,562 706.5,599.5" +
            "Q693,637 670,667Q660,680 643,680.5Q626,681 614,669Q603,658 602.5,641Q602,624 612,610" +
            "Q625,590 632.5,567.5Q640,545 640,520Q640,454 593,407Q546,360 480,360Q414,360 367,407" +
            "Q320,454 320,520Q320,546 327.5,568Q335,590 348,610Q358,624 357.5,640.5Q357,657 346,668Z",
    ),
    FollowingPulseLayer(
        delayMillis = 140,
        peakScale = 1.1f,
        reboundScale = 0.98f,
        pathData = "M233,781Q221,793 204,793Q187,793 176,780Q131,727 105.5,661Q80,595 80,520Q80,437 111.5,364" +
            "Q143,291 197,237Q251,183 324,151.5Q397,120 480,120Q563,120 636,151.5Q709,183 763,237" +
            "Q817,291 848.5,364Q880,437 880,520Q880,595 854.5,661Q829,727 784,780Q773,793 756.5,793.5" +
            "Q740,794 728,782Q717,771 717,754Q717,737 728,724Q762,682 781,630Q800,578 800,520" +
            "Q800,386 707,293Q614,200 480,200Q346,200 253,293Q160,386 160,520Q160,578 179,629.5" +
            "Q198,681 233,723Q244,736 244.5,752.5Q245,769 233,781Z",
    ),
)
