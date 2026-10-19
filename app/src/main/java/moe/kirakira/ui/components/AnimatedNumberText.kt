package moe.kirakira.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import io.github.nadeemiqbal.numberflow.NumberFlow
import io.github.nadeemiqbal.numberflow.NumberFlowDefaults

internal val LocalNumberAnimationIdentity = compositionLocalOf<Any?> { null }

/** Rolls changing digits and smoothly resizes the numeric region; keep fixed units outside. */
@Composable
internal fun AnimatedNumberText(
    value: Double,
    modifier: Modifier = Modifier,
    format: (Double) -> String = NumberFlowDefaults.PlainDouble,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val label = format(value)
    key(LocalNumberAnimationIdentity.current) {
        NumberFlow(
            value = value,
            modifier = modifier
                .clearAndSetSemantics { text = AnnotatedString(label) }
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    alignment = Alignment.CenterStart,
                ),
            format = format,
            style = style,
            color = color,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        )
    }
}

/** Keeps integer precision, including Long.MIN_VALUE; the sign never rolls. */
@Composable
internal fun AnimatedNumberText(
    value: Long,
    modifier: Modifier = Modifier,
    format: (Long) -> String = NumberFlowDefaults.PlainLong,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val label = format(value)
    key(LocalNumberAnimationIdentity.current) {
        Row(
            modifier = modifier
                .clearAndSetSemantics { text = AnnotatedString(label) }
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    alignment = Alignment.CenterStart,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label.startsWith("-")) Text("-", style = style, color = color)
            NumberFlow(
                value = value,
                format = { format(it).removePrefix("-") },
                style = style,
                color = color,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            )
        }
    }
}

@Composable
internal fun AnimatedNumberText(
    value: Int,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    AnimatedNumberText(value.toLong(), modifier, { format(it.toInt()) }, style, color)
}
