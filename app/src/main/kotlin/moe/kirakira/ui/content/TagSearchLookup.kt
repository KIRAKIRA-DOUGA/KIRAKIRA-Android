package moe.kirakira.ui.content

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.content.VideoTag

internal data class TagSearchState(
    val query: String = "",
    val candidates: ContentState<List<VideoTag>> = ContentState(),
)

/** Runs candidate reads in the owning ViewModel's account task scope. */
internal class TagSearchLookup(
    private val launchAccountTask: (suspend (Long) -> Unit) -> Job,
    private val isCurrentAccount: (Long) -> Boolean,
    private val fetch: suspend (String, Long) -> List<VideoTag>,
    private val publish: (TagSearchState) -> Unit,
) {
    private var job: Job? = null

    fun search(query: String, current: TagSearchState, debounce: Boolean) {
        cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            publish(TagSearchState(query))
            return
        }
        val previous = current.candidates.data.takeIf { current.query.trim() == trimmed }
        val loading = ContentState(data = previous, loading = true)
        publish(TagSearchState(query, loading))
        job = launchAccountTask { revision ->
            if (debounce) delay(500.milliseconds)
            val result = try {
                ContentState(fetch(trimmed, revision))
            } catch (error: ApiException) {
                loading.readFailed(error.failure)
            }
            currentCoroutineContext().ensureActive()
            if (isCurrentAccount(revision)) publish(TagSearchState(query, result))
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
