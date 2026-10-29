package moe.kirakira.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned

/** Official toggle button with a shared animated outline for its surface and project shadow. */
@Composable
internal fun ShadowToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: ToggleButtonSize = ToggleButtonDefaults.size,
    enabled: Boolean = true,
    icon: (@Composable (Boolean) -> Unit)? = null,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapesFor(buttonSize),
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ToggleButtonDefaults.contentPaddingFor(buttonSize, hasStartIcon = icon != null),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.(Boolean) -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val restingTargetShape = if (checked) shapes.checkedShape else shapes.shape
    val outline = rememberMorphingButtonShape(
        targetShape = if (enabled && pressed) shapes.pressedShape else restingTargetShape,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val containerColor = if (checked) colors.checkedContainerColor else colors.containerColor
    val reveal = rememberButtonColorReveal(
        checked = checked,
        color = containerColor,
        disabledColor = colors.disabledContainerColor,
        enabled = enabled,
    )
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.buttonShadow(
            outline,
            containerColor,
            enabled,
            source,
            opacity = { reveal.shadowOpacity() },
            tint = { reveal.shadowColor() },
        ).buttonColorReveal(outline, reveal),
        buttonSize = buttonSize,
        enabled = enabled,
        icon = icon?.let { iconContent ->
            {
                ButtonRevealContent(
                    reveal,
                    Modifier.onGloballyPositioned { reveal.iconCoordinates = it },
                ) { state ->
                    CompositionLocalProvider(
                        LocalContentColor provides when {
                            !enabled -> colors.disabledContentColor
                            state -> colors.checkedContentColor
                            else -> colors.contentColor
                        },
                    ) { iconContent(state) }
                }
            }
        },
        shapes = ToggleButtonShapes(outline, outline, outline),
        colors = colors.copy(
            containerColor = Color.Transparent,
            checkedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        elevation = null,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
    ) {
        ButtonRevealContent(reveal) { state ->
            CompositionLocalProvider(
                LocalContentColor provides when {
                    !enabled -> colors.disabledContentColor
                    state -> colors.checkedContentColor
                    else -> colors.contentColor
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) { content(state) }
            }
        }
    }
}
