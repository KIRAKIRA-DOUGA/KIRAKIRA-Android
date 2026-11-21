package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MinimumInteractiveLeftAlignmentLine
import androidx.compose.material3.MinimumInteractiveTopAlignmentLine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import moe.kirakira.R

@Composable
internal fun ButtonLoadingIndicator(
    loading: Boolean,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    button: @Composable (Modifier) -> Unit,
) = ButtonLoadingIndicator(
    loading = loading,
    shape = shape,
    modifier = modifier,
    indicator = { IndeterminateLinearProgressIndicator(it, color, Color.Transparent) },
    button = button,
)

/** Measures the button first so the bottom overlay never changes its layout or touch target. */
@Composable
internal fun ButtonLoadingIndicator(
    loading: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    indicator: @Composable (Modifier) -> Unit,
    button: @Composable (Modifier) -> Unit,
) {
    // Keep the original button node and its content state when adding or removing the overlay.
    val controlContent = remember {
        movableContentOf { controlModifier: Modifier, content: @Composable (Modifier) -> Unit ->
            content(controlModifier)
        }
    }
    if (!loading) {
        controlContent(modifier, button)
        return
    }
    val progressSemantics = Modifier.buttonLoadingSemantics(loading)
    Layout(
        modifier = modifier,
        content = {
            controlContent(progressSemantics, button)
            Box(
                Modifier
                    .clip(shape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.BottomCenter,
            ) {
                indicator(Modifier.fillMaxWidth())
            }
        },
    ) { measurables, constraints ->
        val control = measurables[0].measure(constraints)
        val left = control[MinimumInteractiveLeftAlignmentLine].coerceAtLeast(0)
        val top = control[MinimumInteractiveTopAlignmentLine].coerceAtLeast(0)
        val surfaceWidth = (control.measuredWidth - 2 * left).coerceAtLeast(0)
        val surfaceHeight = (control.measuredHeight - 2 * top).coerceAtLeast(0)
        val indicator = measurables[1].measure(Constraints.fixed(surfaceWidth, surfaceHeight))
        layout(control.width, control.height) {
            control.place(0, 0)
            indicator.place(
                left + (control.width - control.measuredWidth) / 2,
                top + (control.height - control.measuredHeight) / 2,
            )
        }
    }
}

// Keep Material's disabled interaction handling while drawing the enabled palette.
internal fun ButtonColors.withEnabledAppearance(keepEnabledAppearance: Boolean): ButtonColors =
    if (keepEnabledAppearance) copy(
        disabledContainerColor = containerColor,
        disabledContentColor = contentColor,
    ) else this

internal fun IconButtonColors.withEnabledAppearance(keepEnabledAppearance: Boolean): IconButtonColors =
    if (keepEnabledAppearance) copy(
        disabledContainerColor = containerColor,
        disabledContentColor = contentColor,
    ) else this

@Composable
internal fun Modifier.buttonLoadingSemantics(loading: Boolean): Modifier {
    if (!loading) return this
    val workingDescription = stringResource(R.string.auth_working)
    return semantics {
        stateDescription = workingDescription
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        liveRegion = LiveRegionMode.Polite
    }
}

@Composable
internal fun CircularButtonContent(
    loading: Boolean,
    color: Color = LocalContentColor.current,
    content: @Composable () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        // Hide only the icon's drawing so its size and accessible name stay available.
        Box(Modifier.drawWithContent { if (!loading) drawContent() }) {
            content()
        }
        if (loading) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                IndeterminateCircularProgressIndicator(
                    modifier = Modifier.size(24.dp).clearAndSetSemantics {},
                    color = color,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
internal fun LoadingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    indicatorColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    val interactive = enabled && !loading
    IconButton(
        onClick = { if (interactive) onClick() },
        modifier = modifier.buttonLoadingSemantics(loading),
        enabled = interactive,
        colors = IconButtonDefaults.iconButtonColors().withEnabledAppearance(loading),
    ) {
        CircularButtonContent(loading, indicatorColor, content)
    }
}
