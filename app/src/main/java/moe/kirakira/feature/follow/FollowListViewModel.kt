package moe.kirakira.feature.follow

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.data.content.FollowListUser
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.ContentViewModel
import moe.kirakira.feature.video.isReadyForContent

internal data class FollowListState(
    val revision: Long,
    val content: ContentState<List<FollowListUser>> = ContentState(loading = true),
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val moreError: ApiFailure? = null,
)

internal class FollowListViewModel(
    val uid: Long,
    val kind: FollowListKind,
    repository: ContentRepository,
) : ContentViewModel(repository) {
    private val _state = MutableStateFlow(FollowListState(revision))
    val state = _state.asStateFlow()
    private var page = 0
    private var receivedCount = 0L
    private var generation = 0L
    private var task: Job? = null

    init {
        observeAccount({
            generation++
            task = null
            page = 0
            receivedCount = 0
            _state.value = FollowListState(revision)
        }, ::refresh)
    }

    fun refresh() {
        if (!ready()) return
        requestPage(replacing = true)
    }

    fun loadMore(retry: Boolean = false) {
        val value = _state.value
        if (!ready() || value.content.data == null || value.content.loading || value.loadingMore ||
            value.content.error != null || !value.hasMore || (value.moreError != null && !retry)) return
        requestPage(replacing = false)
    }

    private fun ready() = session.value.isReadyForContent && revision == session.value.revision

    private fun requestPage(replacing: Boolean) {
        task?.cancel()
        val requestGeneration = ++generation
        val expected = revision
        val requestedPage = if (replacing) 1 else page + 1
        val previous = _state.value
        _state.value = previous.copy(
            content = previous.content.copy(loading = replacing, error = null),
            loadingMore = !replacing,
            moreError = null,
        )
        task = launchTask {
            try {
                val result = repository.followList(uid, kind, requestedPage, expected)
                if (!current(expected, requestGeneration)) return@launchTask
                val users = (if (replacing) result.users else previous.content.data.orEmpty() + result.users)
                    .distinctBy { it.uid }
                receivedCount = (if (replacing) 0L else receivedCount) + result.users.size
                page = requestedPage
                _state.value = FollowListState(
                    revision = expected,
                    content = ContentState(users),
                    hasMore = result.users.isNotEmpty() && receivedCount < result.totalCount,
                )
            } catch (error: ApiException) {
                if (!current(expected, requestGeneration)) return@launchTask
                val keep = error.failure in setOf(ApiFailure.NETWORK, ApiFailure.TIMEOUT, ApiFailure.SERVER)
                _state.value = when {
                    !keep -> {
                        page = 0
                        receivedCount = 0
                        FollowListState(expected, ContentState(error = error.failure))
                    }
                    replacing -> previous.copy(
                        content = previous.content.copy(loading = false, error = error.failure),
                        loadingMore = false,
                    )
                    else -> previous.copy(loadingMore = false, moreError = error.failure)
                }
            }
        }
    }

    private fun current(expected: Long, requestGeneration: Long) =
        expected == session.value.revision && requestGeneration == generation
}
