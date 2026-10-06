package moe.kirakira.feature.follow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.kirakira.feature.video.isReadyForContent

@Composable
internal fun FollowListPage(
    model: FollowListViewModel,
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    key(session.revision) {
        FollowListScreen(
            state = state.takeIf { it.revision == session.revision } ?: FollowListState(session.revision),
            kind = model.kind,
            ready = session.isReadyForContent && state.revision == session.revision,
            onRefresh = model::refresh,
            onLoadMore = { model.loadMore() },
            onRetryMore = { model.loadMore(retry = true) },
            onBack = onBack,
            onOpenProfile = onOpenProfile,
            modifier = modifier,
            isActive = isActive,
        )
    }
}
