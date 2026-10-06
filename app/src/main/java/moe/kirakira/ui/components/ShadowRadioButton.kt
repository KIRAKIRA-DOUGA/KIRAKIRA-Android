package moe.kirakira.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

@Composable
internal fun ShadowRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val elevation by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            pressed -> 4f
            hovered || focused -> 2f
            else -> 1f
        },
        animationSpec = tween(durationMillis = if (enabled && pressed) 120 else 180),
        label = "radioButtonShadowElevation",
    )
    Box(modifier = modifier, propagateMinConstraints = true) {
        if (selected && enabled) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Spacer(
                    Modifier.size(RadioButtonIconSize)
                        .drawWithCache {
                            val circle = Path().apply { addOval(Rect(Offset.Zero, size)) }
                            onDrawWithContent {
                                // RadioButton is hollow, so exclude the shadow beneath its icon.
                                clipPath(circle, clipOp = ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                            }
                        }
                        .materialColorShadow(CircleShape, colors.selectedColor, opacityScale = 0.5f) { elevation },
                )
            }
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = colors,
            interactionSource = source,
        )
    }
}

// Matches Material 3 RadioButtonTokens.IconSize; the token is not a public API.
private val RadioButtonIconSize = 20.dp
