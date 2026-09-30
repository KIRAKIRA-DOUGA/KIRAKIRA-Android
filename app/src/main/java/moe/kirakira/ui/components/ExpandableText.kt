package moe.kirakira.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

/** Selectable text with an externally owned expansion state and theme-driven content/label transitions. */
@Composable
fun ExpandableText(
    text: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    require(collapsedMaxLines > 0)
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val heightSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Int>()
    val textMeasurer = rememberTextMeasurer()
    val textStyle = LocalTextStyle.current.merge(style)
    val density = LocalDensity.current
    val expandLabel = stringResource(R.string.expandable_text_expand)
    val collapseLabel = stringResource(R.string.expandable_text_collapse)
    val stateLabel = stringResource(
        if (expanded) R.string.expandable_text_expanded else R.string.expandable_text_collapsed,
    )

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val textWidth = constraints.maxWidth
        val collapsedLayout = remember(textMeasurer, text, textStyle, textWidth, collapsedMaxLines) {
            textMeasurer.measure(
                text = text,
                style = textStyle,
                maxLines = collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = textWidth),
            )
        }
        val expandedHeight = remember(textMeasurer, text, textStyle, textWidth) {
            textMeasurer.measure(
                text = text,
                style = textStyle,
                constraints = Constraints(maxWidth = textWidth),
            ).size.height
        }
        val transition = updateTransition(expanded, label = "Expandable text")
        val height by transition.animateInt(
            transitionSpec = { heightSpec },
            label = "Text clipping height",
        ) { showingExpanded ->
            if (showingExpanded) expandedHeight else collapsedLayout.size.height
        }
        // Keep the full paragraph while either direction is animating, including an interrupted expansion.
        // Only restore the ellipsis after the closing clip has reached the collapsed height.
        val showFullText = transition.currentState || transition.targetState || transition.isRunning
        Column {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { height.coerceAtLeast(0).toDp() })
                    .clipToBounds(),
            ) {
                Text(
                    text = text,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true),
                    style = textStyle,
                    maxLines = if (showFullText) Int.MAX_VALUE else collapsedMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(
                visible = expanded || collapsedLayout.hasVisualOverflow,
                enter = expandVertically(spatial, expandFrom = Alignment.Top),
                exit = shrinkVertically(spatial, shrinkTowards = Alignment.Top),
            ) {
                TextButton(
                    onClick = { onExpandedChange(!expanded) },
                    modifier = Modifier.semantics { stateDescription = stateLabel },
                ) {
                    Text(
                        text = if (expanded) collapseLabel else expandLabel,
                        modifier = Modifier.animateContentSize(spatial),
                    )
                }
            }
        }
    }
}

@Preview(name = "Expandable text · Chinese", locale = "zh", widthDp = 320)
@Preview(name = "Expandable text · Large text", locale = "en", fontScale = 2f, widthDp = 320)
@Composable
private fun ExpandableTextPreview() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    KIRAKIRATheme(dynamicColor = false) {
        ExpandableText(
            text = stringResource(R.string.app_name),
            expanded = expanded,
            onExpandedChange = { expanded = it },
        )
    }
}
