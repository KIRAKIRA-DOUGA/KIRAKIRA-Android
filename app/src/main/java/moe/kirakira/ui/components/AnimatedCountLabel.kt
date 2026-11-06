package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

/** [template] is a localized label with exactly one %1$s numeric placeholder. */
@Composable
internal fun AnimatedCountLabel(
    count: Long,
    template: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    AnimatedCountTemplate(listOf(count), template, modifier, style, color, maxLines)
}

@Composable
internal fun AnimatedPageCount(
    currentPage: Int,
    totalPages: Int,
    template: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    AnimatedCountTemplate(listOf(currentPage.toLong(), totalPages.toLong()), template, modifier, style, color, 1)
}

private val countPlaceholder = Regex($$"%([12])\\$s")

@Composable
private fun AnimatedCountTemplate(
    values: List<Long>,
    template: String,
    modifier: Modifier,
    style: TextStyle,
    color: Color,
    maxLines: Int,
) {
    val numberStyle = style.copy(fontFeatureSettings = "tnum")
    val locale = LocalConfiguration.current.locales[0]
    val format: (Long) -> String = { String.format(locale, "%d", it) }
    val label = String.format(locale, template, *values.map(format).toTypedArray())
    val textMeasurer = rememberTextMeasurer()
    val width = textMeasurer.measure(label, numberStyle, softWrap = false, maxLines = 1).size.width
    Layout(
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(label) },
        measurePolicy = remember(width) { AnimatedCountMeasurePolicy(width) },
        content = {
            Text(label, style = numberStyle, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                var offset = 0
                for (match in countPlaceholder.findAll(template)) {
                    if (match.range.first > offset) {
                        Text(template.substring(offset, match.range.first), style = numberStyle, color = color)
                    }
                    AnimatedNumberText(
                        value = values[match.groupValues[1].toInt() - 1],
                        format = format,
                        style = numberStyle,
                        color = color,
                    )
                    offset = match.range.last + 1
                }
                if (offset < template.length) Text(template.substring(offset), style = numberStyle, color = color)
            }
        },
    )
}

private class AnimatedCountMeasurePolicy(private val singleLineWidth: Int) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val (plainLabel, animatedLabel) = measurables
        val label = if (singleLineWidth > constraints.maxWidth) plainLabel else animatedLabel
        val placeable = label.measure(constraints.copy(minWidth = 0, minHeight = 0))
        return layout(constraints.constrainWidth(placeable.width), constraints.constrainHeight(placeable.height)) {
            placeable.placeRelative(0, 0)
        }
    }

    // Text fields query their supporting label's intrinsic height before measuring it.
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.first().minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.first().maxIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.first().minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.first().maxIntrinsicWidth(height)
}
