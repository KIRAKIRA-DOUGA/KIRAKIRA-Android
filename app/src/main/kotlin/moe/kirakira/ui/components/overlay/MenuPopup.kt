package moe.kirakira.ui.components.overlay

import android.os.Build
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopPauseEffect
import moe.kirakira.ui.components.scaffold.frostedSurfaceBackground

private val MenuShadowOutsets = LayerOutsets(24.dp)

internal val LocalMenuHazeState = staticCompositionLocalOf<HazeState?> { null }

internal fun Modifier.menuHazeSource(state: HazeState): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) hazeSource(state) else this

/**
 * Menu host with room for the panel shadow in the animated offscreen buffer.
 * Outsets preserve the menu's measured size, anchor position and outside-click bounds.
 */
@Composable
fun MenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 320.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return
    StatusBarScrollToTopPauseEffect()

    val positionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below)
    val transition = rememberTransition(visibility, label = "menu")
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
    val hazeState = if (inspecting) null else LocalMenuHazeState.current
    val shape = MaterialTheme.shapes.large

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = MenuDefaults.DefaultMenuProperties,
    ) {
        Box(
            modifier = modifier
                .width(IntrinsicSize.Max)
                .graphicsLayer {
                    scaleX = if (inspecting) 1f else scale.value
                    scaleY = scaleX
                    alpha = if (inspecting) 1f else opacity.value
                    transformOrigin = positionProvider.transformOrigin
                    // Alpha < 1 uses an offscreen buffer even when clip is false.
                    outsets = MenuShadowOutsets
                    clip = false
                },
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .frostedSurfaceBackground(shape, hazeState, MenuDefaults.ShadowElevation),
            )
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxHeight)
                        .clip(shape)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Composable
internal fun menuItemShapes(): MenuItemShapes {
    val shape = MaterialTheme.shapes.small
    return MenuDefaults.itemShapes(shape = shape, selectedShape = shape)
}

@Composable
internal fun menuItemColors(
    textColor: Color = Color.Unspecified,
    leadingIconColor: Color = Color.Unspecified,
) = MenuDefaults.selectableItemColors(
    textColor = textColor,
    leadingIconColor = leadingIconColor,
    containerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onPrimary,
)
