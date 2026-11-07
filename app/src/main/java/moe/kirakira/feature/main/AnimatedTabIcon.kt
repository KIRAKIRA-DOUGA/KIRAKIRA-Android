package moe.kirakira.feature.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

internal enum class MainTabIcon(@param:DrawableRes val resource: Int) {
    HOME(R.drawable.ic_icon_home),
    SEARCH(R.drawable.ic_symbol_search),
    FOLLOWING(R.drawable.ic_symbol_wifi_tethering),
    ME(R.drawable.ic_icon_person),
}

@Composable
internal fun AnimatedTabIcon(
    icon: MainTabIcon,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (icon == MainTabIcon.FOLLOWING) {
        FollowingTabIcon(selected = selected, modifier = modifier)
        return
    }
    if (icon == MainTabIcon.ME) {
        PersonTabIcon(selected = selected, modifier = modifier)
        return
    }

    val choreography = if (icon == MainTabIcon.HOME) homeChoreography else searchChoreography
    val motion = remember(icon) { TabSelectionMotion(choreography.durationMillis) }
    var previouslySelected by remember(icon) { mutableStateOf(selected) }

    LaunchedEffect(icon, selected) {
        // Start at the restored selection without replaying an entrance motion.
        if (previouslySelected == selected) return@LaunchedEffect
        previouslySelected = selected
        motion.onSelectionChanged(selected)
    }

    Icon(
        painter = painterResource(icon.resource),
        contentDescription = null,
        modifier = modifier
            .size(24.dp)
            .graphicsLayer { choreography.applyTo(this, motion) },
    )
}

private class TabIconChoreography(
    val durationMillis: Int,
    private val pivot: TransformOrigin,
    private val translationYDp: MotionTrack? = null,
    private val rotationDegrees: MotionTrack? = null,
    private val widthScale: MotionTrack,
    private val heightScale: MotionTrack,
) {
    fun applyTo(layer: GraphicsLayerScope, motion: TabSelectionMotion) {
        layer.transformOrigin = pivot
        translationYDp?.let { layer.translationY = with(layer) { motion.sample(it, 0f).dp.toPx() } }
        rotationDegrees?.let { layer.rotationZ = motion.sample(it, 0f) }
        val width = motion.sample(widthScale, 1f)
        layer.scaleX = width
        layer.scaleY = motion.sample(heightScale, 1f)
    }
}

// Opposing width and height tracks squash the house before stretching it upward from its base.
private val homeChoreography = TabIconChoreography(
    durationMillis = 400,
    pivot = TransformOrigin(0.5f, 0.83f),
    translationYDp = MotionTrack(0f, 0f, 0.2f, 0.4f, 0.46f, -0.9f, 0.72f, 0.12f, 1f, 0f),
    widthScale = MotionTrack(0f, 1f, 0.2f, 1.14f, 0.46f, 0.88f, 0.72f, 1.04f, 1f, 1f),
    heightScale = MotionTrack(0f, 1f, 0.2f, 0.84f, 0.46f, 1.14f, 0.72f, 0.96f, 1f, 1f),
)

// Anchor the magnifier at the rounded handle tip (784, 784) in the 960-unit source viewport.
private val searchChoreography = TabIconChoreography(
    durationMillis = 420,
    pivot = TransformOrigin(784f / 960f, 784f / 960f),
    rotationDegrees = MotionTrack(0f, 0f, 0.2f, -12f, 0.46f, 7f, 0.72f, -2f, 1f, 0f),
    widthScale = MotionTrack(0f, 1f, 0.2f, 0.88f, 0.46f, 1.11f, 0.72f, 0.98f, 1f, 1f),
    heightScale = MotionTrack(0f, 1f, 0.2f, 0.88f, 0.46f, 1.11f, 0.72f, 0.98f, 1f, 1f),
)

@Preview(showBackground = true)
@Composable
private fun AnimatedTabIconPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MainTabIcon.entries.forEach { icon ->
                AnimatedTabIcon(icon = icon, selected = false)
                AnimatedTabIcon(icon = icon, selected = true)
            }
        }
    }
}
