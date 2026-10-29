package moe.kirakira.feature.video

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.isReadyForContent
import moe.kirakira.data.content.CommentPage
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.VideoComment
import moe.kirakira.ui.content.ContentState

internal data class CommentEntry(val comment: VideoComment, val page: Int)
internal data class CommentLocation(val request: Long, val page: Int)

/** Derived entries are rebuilt only when the page window changes. */
internal class CommentPages(pages: Map<Int, CommentPage> = emptyMap()) {
    val pages = pages.toMap()
    val entries: List<CommentEntry> = this.pages.toSortedMap().flatMap { (page, data) ->
        data.comments.map { CommentEntry(it, page) }
    }.distinctBy { it.comment.id }
    val firstPage = this.pages.keys.minOrNull() ?: 1
    val lastPage = this.pages.keys.maxOrNull() ?: 1
}

internal data class CommentListState(
    val content: CommentPages = CommentPages(),
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
    val unconfirmedPages: Set<Int> = emptySet(),
) {
    val pages: Map<Int, CommentPage> get() = content.pages
    val entries: List<CommentEntry> get() = content.entries
    val firstPage: Int get() = content.firstPage
    val lastPage: Int get() = content.lastPage
    val totalPages: Int get() = ((total + 19) / 20).coerceAtLeast(1)
}

private enum class CommentOperation { JUMP, REFRESH, LAST, PREVIOUS, NEXT, UPDATE }
private data class CommentRequest(val page: Int, val operation: CommentOperation)

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
    private var failedRequest = CommentRequest(1, CommentOperation.JUMP)
    private var accountRevision = repository.session.value.revision
    private val ready: Boolean
        get() = repository.session.value.isReadyForContent && repository.session.value.revision == accountRevision

    fun reset() {
        cancelRequests()
        accountRevision = repository.session.value.revision
        mutableState.value = CommentListState()
    }

    fun contentUnavailable(failure: ApiFailure) {
        cancelRequests()
        failedRequest = CommentRequest(1, CommentOperation.JUMP)
        mutableState.value = CommentListState(error = failure)
    }

    private fun cancelRequests() {
        generation++
        requests.values.toList().forEach { it.cancel() }
        requests.clear()
        mutableState.value = mutableState.value.copy(
            loading = false, refreshing = false, previous = ContentState(), next = ContentState(),
            unconfirmedPages = mutableState.value.unconfirmedPages + mutableState.value.updatingPages,
            updatingPages = emptySet(),
        )
    }

    fun jump(page: Int) {
        if (!ready) return
        val target = page.coerceAtLeast(1)
        if (mutableState.value.pages[target]?.comments?.isNotEmpty() == true &&
            mutableState.value.updatingPages.isEmpty()
        ) {
            cancelRequests()
            mutableState.value = mutableState.value.copy(
                error = mutableState.value.error.takeIf { mutableState.value.unconfirmedPages.isNotEmpty() },
                location = CommentLocation(++locationId, target),
            )
            return
        }
        replace(CommentRequest(target, CommentOperation.JUMP))
    }

    private fun replace(request: CommentRequest) {
        if (!ready) return
        cancelRequests()
        failedRequest = request
        mutableState.value = mutableState.value.copy(
            loading = true, refreshing = request.operation == CommentOperation.REFRESH, error = null, location = null,
        )
        read(request)
    }

    fun refresh() = replace(CommentRequest(1, CommentOperation.REFRESH))

    fun retry() {
        if (!ready) return
        val request = failedRequest
        when (request.operation) {
            CommentOperation.UPDATE -> {
                if (request.page in mutableState.value.pages) {
                    if (request.page !in requests) read(request)
                } else replace(request.copy(operation = CommentOperation.JUMP))
            }
            CommentOperation.REFRESH -> replace(request.copy(operation = CommentOperation.JUMP))
            CommentOperation.JUMP, CommentOperation.LAST -> replace(request)
            CommentOperation.PREVIOUS, CommentOperation.NEXT -> Unit // Retried through the corresponding edge.
        }
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
        read(CommentRequest(page, if (before) CommentOperation.PREVIOUS else CommentOperation.NEXT))
    }

    fun reloadComment(id: String) {
        if (!ready) return
        val current = mutableState.value
        val page = current.entries.firstOrNull { it.comment.id == id }?.page ?: return
        if (current.loading || page in requests) return
        read(CommentRequest(page, CommentOperation.UPDATE))
    }

    fun markUnconfirmed(page: Int) {
        mutableState.value = mutableState.value.copy(unconfirmedPages = mutableState.value.unconfirmedPages + page)
    }

    fun posted() {
        val total = mutableState.value.total + 1
        mutableState.value = mutableState.value.copy(total = total)
        replace(CommentRequest(((total + 19) / 20).coerceAtLeast(1), CommentOperation.LAST))
    }

    private fun read(request: CommentRequest) {
        val (page, operation) = request
        val replace = when (operation) {
            CommentOperation.JUMP, CommentOperation.REFRESH, CommentOperation.LAST -> true
            CommentOperation.PREVIOUS, CommentOperation.NEXT, CommentOperation.UPDATE -> false
        }
        val before = operation == CommentOperation.PREVIOUS
        val update = operation == CommentOperation.UPDATE
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
                if (operation == CommentOperation.LAST && result.page != result.pages) {
                    result = repository.comments(videoId, result.pages, revision)
                }
                currentCoroutineContext().ensureActive()
                if (generation != expectedGeneration || repository.session.value.revision != revision) return@launch
                val current = mutableState.value
                val newPages = if (replace) mapOf(result.page to result) else current.pages + (page to result)
                mutableState.value = current.copy(
                    content = CommentPages(newPages),
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
                    unconfirmedPages = if (replace) emptySet() else current.unconfirmedPages - page,
                )
            } catch (error: ApiException) {
                currentCoroutineContext().ensureActive()
                if (generation != expectedGeneration || repository.session.value.revision != revision) return@launch
                if (!error.failure.retainsContent) {
                    contentUnavailable(error.failure)
                    return@launch
                }
                val current = mutableState.value
                mutableState.value = when {
                    replace || update -> current.copy(
                        loading = false, refreshing = false, error = error.failure,
                        updatingPages = current.updatingPages - page,
                        unconfirmedPages = if (update) current.unconfirmedPages + page else current.unconfirmedPages,
                    )
                    before -> current.copy(previous = ContentState(error = error.failure))
                    else -> current.copy(next = ContentState(error = error.failure))
                }
                if (update) {
                    failedRequest = request
                }
            } finally {
                if (generation == expectedGeneration) requests.remove(page)
            }
        }
        if (!job.isCompleted) requests[page] = job
    }
}
