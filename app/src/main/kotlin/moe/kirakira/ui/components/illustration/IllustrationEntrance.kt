package moe.kirakira.ui.components.illustration

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalInspectionMode
import moe.kirakira.ui.navigation.LocalDestinationActive

internal val illustrationEntranceSpec = tween<Float>(
    durationMillis = 1_000,
    easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f),
)

@Composable
internal fun rememberIllustrationEntranceProgress(isActive: Boolean): State<Float> {
    val active = isActive && LocalDestinationActive.current
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val showFinalFrame = LocalInspectionMode.current || durationScale?.scaleFactor == 0f
    val progress = remember(showFinalFrame) { Animatable(if (active && !showFinalFrame) 0f else 1f) }
    LaunchedEffect(progress, active) {
        if (active && !showFinalFrame) {
            progress.snapTo(0f)
            progress.animateTo(1f, illustrationEntranceSpec)
        }
    }
    return progress.asState()
}
