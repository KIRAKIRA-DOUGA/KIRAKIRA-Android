package moe.kirakira.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow

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
    val locale = LocalConfiguration.current.locales[0]
    val format: (Long) -> String = { String.format(locale, "%d", it) }
    val label = String.format(locale, template, *values.map(format).toTypedArray())
    val textMeasurer = rememberTextMeasurer()
    val width = textMeasurer.measure(label, style, softWrap = false, maxLines = 1).size.width
    BoxWithConstraints(modifier) {
        // Preserve wrapping for labels and single-line ellipsis for the constrained page toolbar.
        if (constraints.hasBoundedWidth && width > constraints.maxWidth) {
            Text(label, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        } else {
            Row(
                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(label) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var offset = 0
                for (match in countPlaceholder.findAll(template)) {
                    if (match.range.first > offset) {
                        Text(template.substring(offset, match.range.first), style = style, color = color)
                    }
                    AnimatedNumberText(
                        value = values[match.groupValues[1].toInt() - 1],
                        format = format,
                        style = style,
                        color = color,
                    )
                    offset = match.range.last + 1
                }
                if (offset < template.length) Text(template.substring(offset), style = style, color = color)
            }
        }
    }
}
