package moe.kirakira.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.kirakira.feature.video.ContentState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ContentStatus(
    state: ContentState<*>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    empty: Boolean = state.data == null,
    presentation: ContentUnavailablePresentation = ContentUnavailablePresentation.INLINE,
    emptyTitle: String = stringResource(ContentUnavailableState.EMPTY.titleRes),
    @DrawableRes emptyIconRes: Int = ContentUnavailableState.EMPTY.iconRes,
) {
    when {
        state.loading -> Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        state.error != null -> ContentUnavailableView(
            state = ContentUnavailableState.ERROR,
            modifier = modifier,
            description = stringResource(state.error.messageRes()),
            onRetry = onRetry,
            presentation = presentation,
        )
        empty -> ContentUnavailableView(
            state = ContentUnavailableState.EMPTY,
            modifier = modifier,
            title = emptyTitle,
            iconRes = emptyIconRes,
            presentation = presentation,
        )
    }
}
