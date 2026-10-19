package moe.kirakira.feature.player

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import moe.kirakira.R
import moe.kirakira.ui.components.AnimatedNumberText

@Composable
internal fun PlaybackSpeedText(
    speed: Float,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val label = speedLabel(speed)
    val suffix = stringResource(R.string.player_speed_value, "")
    Row(
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(label) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedNumberText(
            value = speed.toDouble(),
            format = { speedNumber(it.toFloat()) },
            style = style,
            color = color,
        )
        Text(text = suffix, style = style, color = color)
    }
}
