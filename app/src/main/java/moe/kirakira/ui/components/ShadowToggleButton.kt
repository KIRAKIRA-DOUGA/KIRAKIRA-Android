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

/**
 * Official toggle button with a shared animated outline for its surface and project shadow.
 * [visualChecked] can retain the displayed state while an operation awaits confirmation.
 */
@Composable
internal fun ShadowToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: ToggleButtonSize = ToggleButtonDefaults.size,
    enabled: Boolean = true,
    icon: (@Composable (Boolean) -> Unit)? = null,
    shapes: ToggleButtonShapes = pressToRoundToggleButtonShapes(buttonSize),
    colors: ToggleButtonColors = ToggleButtonDefaults.colors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ToggleButtonDefaults.contentPaddingFor(buttonSize, hasStartIcon = icon != null),
    interactionSource: MutableInteractionSource? = null,
    visualChecked: Boolean = checked,
    shadowWhenChecked: Boolean = true,
    loading: Boolean = false,
    content: @Composable RowScope.(Boolean) -> Unit,
) {
    val interactive = enabled && !loading
    val showEnabledAppearance = enabled || loading
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val restingTargetShape = if (visualChecked) shapes.checkedShape else shapes.shape
    val outline = rememberMorphingButtonShape(
        targetShape = if (interactive && pressed) shapes.pressedShape else restingTargetShape,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val containerColor = if (visualChecked) colors.checkedContainerColor else colors.containerColor
    val reveal = rememberButtonColorReveal(
        checked = visualChecked,
        color = containerColor,
        disabledColor = colors.disabledContainerColor,
        enabled = showEnabledAppearance,
        loading = loading,
    )
    fun foregroundColor(checked: Boolean): Color = when {
        !showEnabledAppearance -> colors.disabledContentColor
        checked -> colors.checkedContentColor
        else -> colors.contentColor
    }

    ButtonLoadingIndicator(
        loading = loading,
        shape = outline,
        modifier = modifier,
        indicator = { ButtonRevealLoadingIndicator(reveal, ::foregroundColor, it) },
    ) { buttonModifier ->
        ToggleButton(
            checked = checked,
            onCheckedChange = { if (interactive) onCheckedChange(it) },
            modifier = buttonModifier.buttonShadow(
                outline,
                containerColor,
                interactive,
                source,
                opacity = { reveal.shadowOpacity(checked = if (shadowWhenChecked) 1f else 0f) },
                tint = {
                    if (shadowWhenChecked) reveal.shadowColor() else materialShadowColor(colors.containerColor)
                },
                showShadow = showEnabledAppearance,
            ).buttonColorReveal(outline, reveal),
            buttonSize = buttonSize,
            enabled = interactive,
            icon = icon?.let { iconContent ->
                {
                    ButtonRevealContent(
                        reveal,
                        Modifier.onGloballyPositioned { reveal.iconCoordinates = it },
                    ) { state ->
                        CompositionLocalProvider(LocalContentColor provides foregroundColor(state)) { iconContent(state) }
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
                CompositionLocalProvider(LocalContentColor provides foregroundColor(state)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { content(state) }
                }
            }
        }
    }
}
