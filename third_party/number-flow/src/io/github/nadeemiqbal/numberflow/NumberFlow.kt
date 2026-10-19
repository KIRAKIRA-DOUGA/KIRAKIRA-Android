package io.github.nadeemiqbal.numberflow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import kotlin.jvm.JvmName

/**
 * A per-digit animated number ticker.
 *
 * Renders [value] formatted as text, and on every value change animates the **individual
 * digits** that changed with a vertical rolling-slide — increasing values roll up,
 * decreasing values roll down. Non-digit characters (commas, decimals, currency symbols)
 * are placed by [format] and animate via the same slide when they shift position.
 *
 * ```
 * var stat by remember { mutableDoubleStateOf(1247.0) }
 * NumberFlow(value = stat, format = NumberFlowDefaults.thousands())
 * ```
 *
 * @param value the current numeric value. Each change triggers the rolling animation.
 * @param modifier the [Modifier] applied to the ticker.
 * @param format function that turns [value] into the displayed string — use
 *   [NumberFlowDefaults.thousands] for comma grouping, or supply your own for currency, units,
 *   percentages, etc.
 * @param style the text style applied to every character. Defaults to [LocalTextStyle].
 * @param color override the text color (defaults to the colour from [style]).
 * @param animationSpec the per-slot slide animation.
 */
@Composable
fun NumberFlow(
    value: Double,
    modifier: Modifier = Modifier,
    format: (Double) -> String = NumberFlowDefaults.PlainDouble,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    animationSpec: FiniteAnimationSpec<IntOffset> = NumberFlowDefaults.AnimationSpec,
) {
    val targetText = remember(value, format) { format(value) }
    var lastValue by remember { mutableDoubleStateOf(value) }
    val increasing = value >= lastValue
    SideEffect {
        if (lastValue != value) lastValue = value
    }
    NumberFlowRow(
        text = targetText,
        increasing = increasing,
        animationSpec = animationSpec,
        style = style,
        color = color,
        modifier = modifier,
    )
}

/**
 * Long-typed convenience overload of [NumberFlow]. See the [Double] overload for behaviour.
 */
@Composable
@JvmName("NumberFlowLong")
fun NumberFlow(
    value: Long,
    modifier: Modifier = Modifier,
    format: (Long) -> String = NumberFlowDefaults.PlainLong,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    animationSpec: FiniteAnimationSpec<IntOffset> = NumberFlowDefaults.AnimationSpec,
) {
    val targetText = remember(value, format) { format(value) }
    var lastValue by remember { mutableLongStateOf(value) }
    val increasing = value >= lastValue
    SideEffect {
        if (lastValue != value) lastValue = value
    }
    NumberFlowRow(
        text = targetText,
        increasing = increasing,
        animationSpec = animationSpec,
        style = style,
        color = color,
        modifier = modifier,
    )
}

/**
 * Int-typed convenience overload of [NumberFlow]. Delegates to the [Long] overload internally.
 */
@Composable
@JvmName("NumberFlowInt")
fun NumberFlow(
    value: Int,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    animationSpec: FiniteAnimationSpec<IntOffset> = NumberFlowDefaults.AnimationSpec,
) {
    NumberFlow(
        value = value.toLong(),
        modifier = modifier,
        format = { format(it.toInt()) },
        style = style,
        color = color,
        animationSpec = animationSpec,
    )
}

@Composable
private fun NumberFlowRow(
    text: String,
    increasing: Boolean,
    animationSpec: FiniteAnimationSpec<IntOffset>,
    style: TextStyle,
    color: Color,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.testTag("number_flow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in text.indices) {
            val char = text[index]
            key(index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val enterFromY: (Int) -> Int = if (increasing) { h -> h } else { h -> -h }
                        val exitToY: (Int) -> Int = if (increasing) { h -> -h } else { h -> h }
                        slideInVertically(animationSpec, initialOffsetY = enterFromY) togetherWith
                            slideOutVertically(animationSpec, targetOffsetY = exitToY)
                    },
                    label = "NumberFlow-slot-$index",
                ) { displayedChar ->
                    Text(
                        text = displayedChar.toString(),
                        style = style,
                        color = color,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
