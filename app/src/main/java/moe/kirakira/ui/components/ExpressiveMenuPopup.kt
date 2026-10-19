package moe.kirakira.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

private val MenuShadowOutsets = LayerOutsets(24.dp)

/**
 * Expressive menu host with room for group shadows in the animated offscreen buffer.
 * Outsets preserve the menu's measured size, anchor position and outside-click bounds.
 */
@Composable
fun ExpressiveMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return

    val positionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below)
    val transition = rememberTransition(visibility, label = "expressiveMenu")
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val alphaSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val scale = transition.animateFloat(
        transitionSpec = { scaleSpec },
        label = "menuScale",
    ) { visible -> if (visible) 1f else 0.8f }
    val opacity = transition.animateFloat(
        transitionSpec = { alphaSpec },
        label = "menuAlpha",
    ) { visible -> if (visible) 1f else 0f }
    val inspecting = LocalInspectionMode.current

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = MenuDefaults.DefaultMenuProperties,
    ) {
        Column(
            modifier = modifier.width(IntrinsicSize.Max).graphicsLayer {
                scaleX = if (inspecting) 1f else scale.value
                scaleY = scaleX
                alpha = if (inspecting) 1f else opacity.value
                transformOrigin = positionProvider.transformOrigin
                // Alpha < 1 uses an offscreen buffer even when clip is false.
                outsets = MenuShadowOutsets
                clip = false
            },
            content = content,
        )
    }
}
