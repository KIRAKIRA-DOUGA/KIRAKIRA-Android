package moe.kirakira.feature.main

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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.readVectorPathData
import moe.kirakira.ui.components.vectorPathContours

/** Animate the original filled contours independently, with follow-through in the head. */
@Composable
internal fun PersonTabIcon(selected: Boolean, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val paths = remember(resources) {
        vectorPathContours(readVectorPathData(resources, R.drawable.ic_icon_person).first())
            .map { PathParser().parsePathString(it).toPath() }
    }
    val motion = remember { TabSelectionMotion(durationMillis = 460) }
    var previouslySelected by remember { mutableStateOf(selected) }

    LaunchedEffect(selected) {
        if (previouslySelected == selected) return@LaunchedEffect
        previouslySelected = selected
        motion.onSelectionChanged(selected)
    }

    val color = LocalContentColor.current
    Canvas(modifier = modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            translate(top = motion.sample(personHeadY, 0f)) {
                scale(
                    scaleX = motion.sample(personHeadWidth, 1f),
                    scaleY = motion.sample(personHeadHeight, 1f),
                    pivot = Offset(12f, 8f),
                ) {
                    drawPath(paths[0], color)
                }
            }
            scale(
                scaleX = motion.sample(personBodyWidth, 1f),
                scaleY = motion.sample(personBodyHeight, 1f),
                pivot = Offset(12f, 20f),
            ) {
                drawPath(paths[1], color)
            }
        }
    }
}

private val personHeadY = MotionTrack(0f, 0f, 0.18f, -1f, 0.4f, 0.8f, 0.65f, -0.32f, 1f, 0f)
private val personHeadWidth = MotionTrack(0f, 1f, 0.18f, 0.68f, 0.44f, 1.18f, 0.7f, 0.96f, 1f, 1f)
private val personHeadHeight = MotionTrack(0f, 1f, 0.18f, 1.26f, 0.44f, 0.86f, 0.7f, 1.04f, 1f, 1f)
private val personBodyWidth = MotionTrack(0f, 1f, 0.14f, 0.82f, 0.36f, 1.22f, 0.64f, 0.96f, 1f, 1f)
private val personBodyHeight = MotionTrack(0f, 1f, 0.14f, 1.22f, 0.36f, 0.82f, 0.64f, 1.04f, 1f, 1f)
