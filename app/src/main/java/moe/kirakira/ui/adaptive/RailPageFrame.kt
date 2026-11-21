package moe.kirakira.ui.adaptive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LocalRailPageFrame = compositionLocalOf { false }

/** A continuous book fold uses the whole safe area so the grid can straddle the crease. */
internal val browsePageMaxWidth: Dp
    @Composable get() = if (LocalAppWindowLayout.current.bookFold != null) Dp.Infinity else 1152.dp

@Composable
internal fun Modifier.browsePageWidth(): Modifier =
    fillMaxWidth().wrapContentWidth().widthIn(max = browsePageMaxWidth).fillMaxWidth()

/** Apply after horizontal insets. The frame's start stays fixed as content widths change. */
@Composable
internal fun Modifier.pageContentWidth(maxWidth: Dp): Modifier {
    if (!LocalRailPageFrame.current) {
        return fillMaxWidth().wrapContentWidth().widthIn(max = maxWidth)
    }
    return browsePageWidth()
        .wrapContentWidth(Alignment.Start)
        .widthIn(max = maxWidth)
}
