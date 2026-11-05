package moe.kirakira.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shares the remaining viewport between loading, empty and error content in a scrolling page.
 * [contentPadding] is the same padding supplied to the list. [leadingContentHeight] includes
 * preceding headers and their spacing. Already excluded space must not be deducted again.
 * The supplied modifier sets a minimum height, so long content can grow and scroll with the list.
 */
@Composable
internal fun ContentStatusViewport(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    leadingContentHeight: Dp = 0.dp,
    obscuredBottomHeight: Dp = 0.dp,
    content: @Composable BoxScope.(statusModifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        check(constraints.hasBoundedHeight) { "ContentStatusViewport requires a bounded height" }
        val statusHeight = (maxHeight - contentPadding.calculateTopPadding() -
            contentPadding.calculateBottomPadding() - leadingContentHeight - obscuredBottomHeight)
            .coerceAtLeast(0.dp)
        content(Modifier.fillMaxWidth().heightIn(min = statusHeight))
    }
}
