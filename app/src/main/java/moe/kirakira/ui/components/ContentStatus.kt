package moe.kirakira.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import moe.kirakira.ui.content.ContentState

@Composable
internal fun ContentStatus(
    state: ContentState<*>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    empty: Boolean = state.data == null,
    presentation: ContentUnavailablePresentation = ContentUnavailablePresentation.INLINE,
    emptyTitle: String = stringResource(ContentUnavailableState.EMPTY.titleRes),
    @DrawableRes emptyIconRes: Int = ContentUnavailableState.EMPTY.iconRes,
    retryEnabled: Boolean = true,
    emptyIcon: (@Composable () -> Unit)? = null,
    emptyDescription: String? = stringResource(ContentUnavailableState.EMPTY.descriptionRes),
) {
    when {
        state.loading -> ContentLoadingView(modifier = modifier, presentation = presentation)
        state.error != null -> ContentUnavailableView(
            state = ContentUnavailableState.ERROR,
            modifier = modifier,
            description = stringResource(state.error.messageRes()),
            onRetry = onRetry,
            retryEnabled = retryEnabled,
            presentation = presentation,
        )
        empty -> ContentUnavailableView(
            state = ContentUnavailableState.EMPTY,
            modifier = modifier,
            title = emptyTitle,
            description = emptyDescription,
            iconRes = emptyIconRes,
            presentation = presentation,
            icon = emptyIcon,
        )
    }
}
