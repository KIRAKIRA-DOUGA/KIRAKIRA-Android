package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/** Actions are supplied in priority order and stack only when their single-line widths do not fit. */
@Composable
internal fun ActionDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = icon,
        title = {
            ProvideTextStyle(LocalTextStyle.current.copy(textAlign = TextAlign.Center)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { title() }
            }
        },
        text = text,
        confirmButton = {
            ProvideTextStyle(LocalTextStyle.current.copy(textAlign = TextAlign.Center)) {
                AdaptiveDialogActions(content = actions)
            }
        },
    )
}

/** Each direct child is one button; the first action sits at the end of a horizontal row. */
@Composable
private fun AdaptiveDialogActions(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val spacing = 8.dp.roundToPx()
        val gapWidth = spacing.toLong() * (measurables.size - 1).coerceAtLeast(0)
        // Max intrinsic width includes the single-line label, button padding and minimum size.
        val widths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val rowWidth = widths.sumOf { it.toLong() } + gapWidth
        val stacked = rowWidth > constraints.maxWidth
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            constraints.constrainWidth(rowWidth.toInt())
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            val buttonWidth = if (stacked) width else widths[index]
            measurable.measure(
                constraints.copy(minWidth = buttonWidth, maxWidth = buttonWidth, minHeight = 0),
            )
        }
        val height = constraints.constrainHeight(
            if (stacked) {
                placeables.sumOf { it.height } + gapWidth.toInt()
            } else {
                placeables.maxOfOrNull { it.height } ?: 0
            },
        )
        layout(width, height) {
            if (stacked) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + spacing
                }
            } else {
                var x = width
                placeables.forEach { placeable ->
                    x -= placeable.width
                    placeable.placeRelative(x, (height - placeable.height) / 2)
                    x -= spacing
                }
            }
        }
    }
}
