package moe.kirakira.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var visualFollowing by remember { mutableStateOf(following) }
    LaunchedEffect(following, enabled) {
        if (enabled) visualFollowing = following
    }
    val colorScheme = MaterialTheme.colorScheme
    val shapes = ToggleButtonDefaults.shapesFor(buttonSize)
    val pressed by source.collectIsPressedAsState()
    val containerColor = if (visualFollowing) colorScheme.surfaceContainerHigh else colorScheme.primary
    val outline = rememberMorphingButtonShape(
        targetShape = if (enabled && pressed) shapes.pressedShape
        else if (visualFollowing) shapes.checkedShape else shapes.shape,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val colors = ToggleButtonDefaults.colors(
        containerColor = colorScheme.primary,
        contentColor = colorScheme.onPrimary,
        checkedContainerColor = colorScheme.surfaceContainerHigh,
        checkedContentColor = colorScheme.onSurfaceVariant,
    )
    val reveal = rememberButtonColorReveal(
        checked = visualFollowing,
        color = containerColor,
        disabledColor = colors.disabledContainerColor,
        enabled = enabled,
    )
    val buttonModifier = modifier.buttonShadow(
        outline,
        colorScheme.primary,
        enabled,
        source,
        opacity = { reveal.shadowOpacity(unchecked = 1f, checked = 0f) },
    )
    val label = stringResource(if (following) R.string.video_following else R.string.video_follow)
    ToggleButton(
        checked = following,
        onCheckedChange = onFollowingChange,
        modifier = buttonModifier.buttonColorReveal(outline, reveal).semantics { contentDescription = label },
        buttonSize = buttonSize,
        enabled = enabled,
        icon = {
            ButtonRevealContent(
                reveal,
                Modifier.onGloballyPositioned { reveal.iconCoordinates = it },
            ) { checked ->
                CompositionLocalProvider(
                    LocalContentColor provides when {
                        !enabled -> colors.disabledContentColor
                        checked -> colors.checkedContentColor
                        else -> colors.contentColor
                    },
                ) {
                    Icon(
                        painter = painterResource(if (checked) R.drawable.ic_symbol_check else R.drawable.ic_symbol_add),
                        contentDescription = null,
                    )
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
        interactionSource = source,
    ) {
        ButtonRevealContent(reveal, Modifier.clearAndSetSemantics {}) { checked ->
            CompositionLocalProvider(
                LocalContentColor provides when {
                    !enabled -> colors.disabledContentColor
                    checked -> colors.checkedContentColor
                    else -> colors.contentColor
                },
            ) {
                Text(stringResource(if (checked) R.string.video_following else R.string.video_follow))
            }
        }
    }
}
