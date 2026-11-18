package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MinimumInteractiveLeftAlignmentLine
import androidx.compose.material3.MinimumInteractiveTopAlignmentLine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import moe.kirakira.R

/** Measures the button first so the bottom overlay never changes its layout or touch target. */
@Composable
internal fun ButtonLoadingIndicator(
    loading: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val workingDescription = stringResource(R.string.auth_working)
    Layout(
        modifier = modifier,
        content = {
            controlContent(
                Modifier.semantics {
                    stateDescription = workingDescription
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    liveRegion = LiveRegionMode.Polite
                },
                button,
            )
            Box(
                Modifier
                    .clip(shape)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.BottomCenter,
            ) {
                IndeterminateLinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = color,
                    trackColor = Color.Transparent,
                )
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

@Composable
internal fun LoadingIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    indicatorColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    ButtonLoadingIndicator(loading, IconButtonDefaults.standardShape, modifier, indicatorColor) { buttonModifier ->
        IconButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !loading,
            content = content,
        )
    }
}
