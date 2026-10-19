package moe.kirakira.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Shape

/** Official toggle button with a shared animated outline for its surface and project shadow. */
@Composable
internal fun ShadowToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: ToggleButtonSize = ToggleButtonDefaults.size,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapesFor(buttonSize),
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ToggleButtonDefaults.contentPaddingFor(buttonSize, hasStartIcon = icon != null),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val checkedProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "toggleButtonCheckedShape",
    )
    val pressedProgress by animateFloatAsState(
        targetValue = if (enabled && pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "toggleButtonPressedShape",
    )
    val restingShape = interpolateToggleShape(shapes.shape, shapes.checkedShape, checkedProgress)
    val outline = interpolateToggleShape(restingShape, shapes.pressedShape, pressedProgress)
    val containerColor = if (checked) colors.checkedContainerColor else colors.containerColor
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.buttonShadow(outline, containerColor, enabled, source),
        buttonSize = buttonSize,
        enabled = enabled,
        icon = icon,
        shapes = ToggleButtonShapes(outline, outline, outline),
        colors = colors,
        elevation = null,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

private fun interpolateToggleShape(from: Shape, to: Shape, progress: Float): Shape =
    Interpolatable.lerp(from, to, progress) as? Shape ?: if (progress < 0.5f) from else to
