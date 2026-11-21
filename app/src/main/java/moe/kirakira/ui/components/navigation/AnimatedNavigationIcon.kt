package moe.kirakira.ui.components.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

internal enum class NavigationIcon(@param:DrawableRes val resource: Int) {
    HOME(R.drawable.ic_icon_home),
    SEARCH(R.drawable.ic_symbol_search),
    CERASUS_SEARCH(R.drawable.ic_icon_search),
    HISTORY(R.drawable.ic_icon_history),
    FAVORITES(R.drawable.ic_icon_star),
    FOLLOWING(R.drawable.ic_symbol_wifi_tethering),
    CERASUS_FOLLOWING(R.drawable.ic_icon_feed),
    PERSON(R.drawable.ic_icon_person),
    SETTINGS(R.drawable.ic_icon_settings),
}

@Composable
internal fun AnimatedNavigationIcon(
    icon: NavigationIcon,
    selected: Boolean,
    modifier: Modifier = Modifier,
    tint: ColorProducer? = null,
) {
    val contentColor = LocalContentColor.current
    val color = tint ?: ColorProducer { contentColor }
    val motionLayer = when (icon) {
        NavigationIcon.PERSON -> {
            PersonTabIcon(selected, color, modifier)
            return
        }
        NavigationIcon.FOLLOWING, NavigationIcon.CERASUS_FOLLOWING -> {
            FollowingTabIcon(icon, selected, color, modifier)
            return
        }
        NavigationIcon.HISTORY -> {
            HistoryTabIcon(selected, color, modifier)
            return
        }
        NavigationIcon.HOME -> Modifier.animateIconShape(homeChoreography, selected)
        NavigationIcon.SEARCH, NavigationIcon.CERASUS_SEARCH -> Modifier.animateIconShape(searchChoreography, selected)
        NavigationIcon.FAVORITES -> Modifier.animateIconShape(favoritesChoreography, selected)
        NavigationIcon.SETTINGS -> {
            val rotationSteps = rememberNavigationIconRotationSteps(selected, durationMillis = 520)
            Modifier.graphicsLayer {
                // Keep the new angle at rest; only wrap after three 120-degree steps.
                rotationZ = (rotationSteps.value % 3f) * 120f
            }
        }
    }

    val painter = painterResource(icon.resource)
    val iconModifier = modifier.size(24.dp).then(motionLayer)
    if (tint == null) {
        // The bottom bar already composites its foreground for indicator tinting.
        Icon(painter = painter, contentDescription = null, modifier = iconModifier, tint = contentColor)
    } else {
        Icon(painter = painter, contentDescription = null, modifier = iconModifier, tint = tint)
    }
}

@Composable
private fun Modifier.animateIconShape(choreography: TabIconChoreography, selected: Boolean): Modifier {
    val motion = rememberTabSelectionMotion(selected, choreography.durationMillis)
    return graphicsLayer { choreography.applyTo(this, motion) }
}

private class TabIconChoreography(
    val durationMillis: Int,
    private val pivot: TransformOrigin,
    private val translationYDp: MotionTrack? = null,
    private val rotationDegrees: MotionTrack? = null,
    private val widthScale: MotionTrack,
    private val heightScale: MotionTrack = widthScale,
) {
    fun applyTo(layer: GraphicsLayerScope, motion: TabSelectionMotion) {
        layer.transformOrigin = pivot
        translationYDp?.let { layer.translationY = with(layer) { motion.sample(it, 0f).dp.toPx() } }
        rotationDegrees?.let { layer.rotationZ = motion.sample(it, 0f) }
        layer.scaleX = motion.sample(widthScale, 1f)
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

// The rounded handle tip is at (784, 784) / 960 and (19.6, 19.6) / 24 in the two assets.
private val searchChoreography = TabIconChoreography(
    durationMillis = 420,
    pivot = TransformOrigin(784f / 960f, 784f / 960f),
    rotationDegrees = MotionTrack(0f, 0f, 0.2f, -12f, 0.46f, 7f, 0.72f, -2f, 1f, 0f),
    widthScale = navigationIconPulseScale,
)

private val favoritesChoreography = TabIconChoreography(
    durationMillis = 440,
    pivot = TransformOrigin.Center,
    translationYDp = MotionTrack(0f, 0f, 0.2f, 0.35f, 0.46f, -1.1f, 0.73f, 0.2f, 1f, 0f),
    rotationDegrees = MotionTrack(0f, 0f, 0.2f, -7f, 0.46f, 9f, 0.73f, -3f, 1f, 0f),
    widthScale = MotionTrack(0f, 1f, 0.2f, 1.09f, 0.46f, 0.94f, 0.73f, 1.03f, 1f, 1f),
    heightScale = MotionTrack(0f, 1f, 0.2f, 0.85f, 0.46f, 1.16f, 0.73f, 0.97f, 1f, 1f),
)

@Preview(showBackground = true)
@Composable
private fun AnimatedNavigationIconPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NavigationIcon.entries.chunked(4).forEach { icons ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    icons.forEach { icon ->
                        AnimatedNavigationIcon(icon = icon, selected = false)
                        AnimatedNavigationIcon(icon = icon, selected = true)
                    }
                }
            }
        }
    }
}
