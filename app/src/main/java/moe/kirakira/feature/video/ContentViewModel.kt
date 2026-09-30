package moe.kirakira.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.SessionOperationType
import moe.kirakira.data.auth.SessionState
import moe.kirakira.data.content.ContentRepository

internal data class ContentState<T>(val data: T? = null, val loading: Boolean = false, val error: ApiFailure? = null)

/** In-flight reads, mutations and drafts belong to one published account revision. */
internal abstract class ContentViewModel(protected val repository: ContentRepository) : ViewModel() {
    protected var revision = repository.session.value.revision
    private val tasks = mutableSetOf<Job>()
    private val reads = mutableMapOf<Any, Job>()
    private val _actionError = MutableStateFlow<ApiFailure?>(null)
    val actionError = _actionError.asStateFlow()
    protected val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    val session get() = repository.session

    protected fun observeAccount(reset: () -> Unit, reload: () -> Unit) {
        viewModelScope.launch {
            // Startup publishes the stored account before validating and publishing it again.
            // Wait for the final identity so a fast response is not cleared by that second revision.
            repository.session.filter { it.isReadyForContent }.map { it.revision }.distinctUntilChanged().collect {
                tasks.toList().forEach { task -> task.cancel() }
                tasks.clear()
                reads.clear()
                revision = it
                _busy.value = false
                _actionError.value = null
                reset()
                reload()
            }
        }
    }

    protected fun <T> load(target: MutableStateFlow<ContentState<T>>, fetch: suspend (Long) -> T) {
        // A pull-to-refresh during startup must not bypass the account observer's readiness gate.
        if (!repository.session.value.isReadyForContent) return
        val expected = revision
        reads.remove(target)?.cancel()
        target.value = target.value.copy(loading = true, error = null)
        reads[target] = launchTask {
            try {
                val data = fetch(expected)
                if (expected == repository.session.value.revision) target.value = ContentState(data)
            } catch (error: ApiException) {
                if (expected == repository.session.value.revision) {
                    val keep = error.failure in setOf(ApiFailure.NETWORK, ApiFailure.TIMEOUT, ApiFailure.SERVER)
                    target.value = ContentState(if (keep) target.value.data else null, error = error.failure)
                }
            }
        }
    }

    protected fun mutate(block: suspend (Long) -> Unit) {
        if (_busy.value) return
        val expected = revision
        _busy.value = true
        _actionError.value = null
        launchTask {
            try {
                block(expected)
            } catch (error: ApiException) {
                if (expected == repository.session.value.revision) _actionError.value = error.failure
            } finally {
                if (expected == revision) _busy.value = false
            }
        }
    }

    fun dismissActionError() { _actionError.value = null }

    protected fun launchTask(block: suspend () -> Unit): Job {
        val job = viewModelScope.launch { block() }
        tasks += job
        job.invokeOnCompletion { tasks.remove(job) }
        return job
    }
}

internal val SessionState.isReadyForContent: Boolean
    get() = !isLoading && operation?.type != SessionOperationType.INITIALIZE
