package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import moe.kirakira.R

@Composable
internal fun CharacterCount(
    count: Int,
    limit: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val locale = LocalConfiguration.current.locales[0]
    val format: (Int) -> String = { String.format(locale, "%d", it) }
    val label = stringResource(R.string.character_count, format(count), limit)
    val suffix = stringResource(R.string.character_count, "", limit)
    Row(
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(label) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedNumberText(
            value = count,
            format = format,
            style = style,
            color = color,
        )
        Text(text = suffix, style = style, color = color)
    }
}
