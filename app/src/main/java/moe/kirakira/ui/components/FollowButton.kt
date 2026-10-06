@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.kirakira.R

@Composable
internal fun FollowButton(
    following: Boolean,
    onFollowingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: ToggleButtonSize = ToggleButtonDefaults.size,
) {
    val source = remember { MutableInteractionSource() }
    val shapes = ToggleButtonDefaults.shapesFor(buttonSize)
    val pressed by source.collectIsPressedAsState()
    val checkedProgress by animateFloatAsState(
        targetValue = if (following) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "followButtonCheckedShape",
    )
    val pressedProgress by animateFloatAsState(
        targetValue = if (enabled && pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "followButtonPressedShape",
    )
    val restingShape = interpolateFollowShape(shapes.shape, shapes.checkedShape, checkedProgress)
    val outline = interpolateFollowShape(restingShape, shapes.pressedShape, pressedProgress)
    val colors = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val buttonModifier = if (following) {
        modifier
    } else {
        modifier.buttonShadow(outline, colors.containerColor, enabled, source)
    }
    ToggleButton(
        checked = following,
        onCheckedChange = onFollowingChange,
        modifier = buttonModifier,
        buttonSize = buttonSize,
        enabled = enabled,
        icon = {
            Icon(
                painter = painterResource(if (following) R.drawable.ic_symbol_check else R.drawable.ic_symbol_add),
                contentDescription = null,
            )
        },
        // The surface and shadow use one animated outline, including checked-state transitions.
        shapes = ToggleButtonShapes(outline, outline, outline),
        colors = colors,
        elevation = null,
        interactionSource = source,
    ) {
        Text(stringResource(if (following) R.string.video_following else R.string.video_follow))
    }
}

private fun interpolateFollowShape(from: Shape, to: Shape, progress: Float): Shape =
    Interpolatable.lerp(from, to, progress) as? Shape ?: if (progress < 0.5f) from else to
