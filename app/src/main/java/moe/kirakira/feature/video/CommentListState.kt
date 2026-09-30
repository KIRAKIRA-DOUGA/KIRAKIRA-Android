package moe.kirakira.feature.video

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.content.CommentPage
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.VideoComment

internal data class CommentEntry(val comment: VideoComment, val page: Int)
internal data class CommentLocation(val request: Long, val page: Int)

internal data class CommentListState(
    val pages: Map<Int, CommentPage> = emptyMap(),
    val total: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: ApiFailure? = null,
    val previous: ContentState<Unit> = ContentState(),
    val next: ContentState<Unit> = ContentState(),
    val startReached: Boolean = false,
    val endReached: Boolean = false,
    val location: CommentLocation? = null,
    val updatingPages: Set<Int> = emptySet(),
) {
    val entries: List<CommentEntry> = pages.toSortedMap().flatMap { (page, data) ->
        data.comments.map { CommentEntry(it, page) }
    }.distinctBy { it.comment.id }
    val firstPage: Int get() = pages.keys.minOrNull() ?: 1
    val lastPage: Int get() = pages.keys.maxOrNull() ?: 1
    val totalPages: Int get() = ((total + 19) / 20).coerceAtLeast(1)
}

/** Owns a contiguous page window; a generation invalidates reads after navigation or refresh. */
internal class CommentListLoader(
    private val videoId: Int,
    private val repository: ContentRepository,
    private val launch: (suspend () -> Unit) -> Job,
) {
    private val mutableState = MutableStateFlow(CommentListState())
    val state = mutableState.asStateFlow()
    private val requests = mutableMapOf<Int, Job>()
    private var generation = 0L
    private var locationId = 0L
    private var failedPage = 1
    private var failedUpdate = false
    private var failedLast = false
    private var accountRevision = repository.session.value.revision
    private val ready: Boolean
        get() = repository.session.value.isReadyForContent && repository.session.value.revision == accountRevision

    fun reset() {
        cancelRequests()
        accountRevision = repository.session.value.revision
        mutableState.value = CommentListState()
    }

    private fun cancelRequests() {
        generation++
        requests.values.toList().forEach { it.cancel() }
        requests.clear()
        mutableState.value = mutableState.value.copy(
            loading = false, refreshing = false, previous = ContentState(), next = ContentState(),
            updatingPages = emptySet(),
        )
    }

    fun jump(page: Int, force: Boolean = false, refresh: Boolean = false, last: Boolean = false) {
        if (!ready) return
        val target = page.coerceAtLeast(1)
        cancelRequests()
        val current = mutableState.value
        if (!force && current.pages[target]?.comments?.isNotEmpty() == true) {
            mutableState.value = current.copy(error = null, location = CommentLocation(++locationId, target))
            return
        }
        failedPage = target
        failedUpdate = false
        failedLast = last
        mutableState.value = current.copy(loading = true, refreshing = refresh, error = null, location = null)
        read(target, replace = true, last = last)
    }

    fun refresh() = jump(1, force = true, refresh = true)
    fun retry() {
        if (!ready) return
        if (failedUpdate && failedPage in mutableState.value.pages) {
            if (failedPage !in requests) read(failedPage, update = true)
        } else jump(failedPage, force = true, last = failedLast)
    }

    fun consumeLocation(request: Long) {
        if (mutableState.value.location?.request == request) {
            mutableState.value = mutableState.value.copy(location = null)
        }
    }

    fun adjacent(before: Boolean, retry: Boolean = false) {
        if (!ready) return
        val current = mutableState.value
        if (current.loading || current.location != null || current.pages.isEmpty()) return
        val edge = if (before) current.previous else current.next
        if (edge.loading || (!retry && edge.error != null)) return
        if (before && (current.firstPage == 1 || current.startReached)) return
        if (!before && (current.lastPage >= current.totalPages || current.endReached)) return
        val page = if (before) current.firstPage - 1 else current.lastPage + 1
        if (page in requests) return
        mutableState.value = if (before) current.copy(previous = ContentState(loading = true))
        else current.copy(next = ContentState(loading = true))
        read(page, before = before)
    }

    fun reloadComment(id: String) {
        if (!ready) return
        val current = mutableState.value
        val page = current.entries.firstOrNull { it.comment.id == id }?.page ?: return
        if (current.loading || page in requests) return
        read(page, update = true)
    }

    fun posted() {
        val total = mutableState.value.total + 1
        mutableState.value = mutableState.value.copy(total = total)
        jump(((total + 19) / 20).coerceAtLeast(1), force = true, last = true)
    }

    private fun read(
        page: Int,
        replace: Boolean = false,
        before: Boolean = false,
        update: Boolean = false,
        last: Boolean = false,
    ) {
        val expectedGeneration = generation
        val revision = accountRevision
        if (update) {
            mutableState.value = mutableState.value.copy(
                updatingPages = mutableState.value.updatingPages + page, error = null,
            )
        }
        val job = launch {
            try {
                var result = repository.comments(videoId, page, revision)
                // Publishing can race other new comments; use a fresh count to find the last page.
                if (last && result.page != result.pages) {
                    result = repository.comments(videoId, result.pages, revision)
                }
                currentCoroutineContext().ensureActive()
                if (generation != expectedGeneration || repository.session.value.revision != revision) return@launch
                val current = mutableState.value
                val newPages = if (replace) mapOf(result.page to result) else current.pages + (page to result)
                mutableState.value = current.copy(
                    pages = newPages,
                    total = result.total,
                    loading = false,
                    refreshing = false,
                    error = if (replace || update) null else current.error,
                    previous = if (replace || before) ContentState() else current.previous,
                    next = if (replace || (!before && !update)) ContentState() else current.next,
                    startReached = if (replace) result.page == 1 else current.startReached ||
                        (before && result.comments.isEmpty()),
                    endReached = if (replace) result.comments.isEmpty()
                        else current.endReached || (!before && !update && result.comments.isEmpty()),
                    location = if (replace) CommentLocation(++locationId, result.page) else current.location,
                    updatingPages = current.updatingPages - page,
                )
            } catch (error: ApiException) {
                if (generation != expectedGeneration || repository.session.value.revision != revision) return@launch
                val current = mutableState.value
                mutableState.value = when {
                    replace || update -> current.copy(
                        loading = false, refreshing = false, error = error.failure,
                        updatingPages = current.updatingPages - page,
                    )
                    before -> current.copy(previous = ContentState(error = error.failure))
                    else -> current.copy(next = ContentState(error = error.failure))
                }
                if (update) {
                    failedPage = page
                    failedUpdate = true
                }
            } finally {
                if (generation == expectedGeneration) requests.remove(page)
            }
        }
        if (!job.isCompleted) requests[page] = job
    }
}
