package moe.kirakira.ui.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.kirakira.core.coroutines.AccountTasks
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.isReadyForContent
import moe.kirakira.data.content.ContentRepository

internal enum class ContentMutation { OTHER, FOLLOW, SEND_COMMENT, SEND_DANMAKU }

/** In-flight reads, mutations and drafts belong to one published account revision. */
internal abstract class ContentViewModel(protected val repository: ContentRepository) : ViewModel() {
    protected var revision = repository.session.value.revision
    private val tasks = AccountTasks(viewModelScope)
    private val reads = mutableMapOf<Any, Job>()
    private val _actionError = MutableStateFlow<ApiFailure?>(null)
    val actionError = _actionError.asStateFlow()
    private val _mutation = MutableStateFlow<ContentMutation?>(null)
    val mutation = _mutation.asStateFlow()
    val session get() = repository.session

    protected fun observeAccount(reset: () -> Unit, reload: () -> Unit) {
        viewModelScope.launch {
            // Startup publishes the stored account before validating and publishing it again.
            // Wait for the final identity so a fast response is not cleared by that second revision.
            repository.session.filter { it.isReadyForContent }.map { it.revision }.distinctUntilChanged().collect {
                tasks.cancelCurrent()
                reads.clear()
                revision = it
                _mutation.value = null
                _actionError.value = null
                reset()
                reload()
            }
        }
    }

    protected fun <T> load(
        target: MutableStateFlow<ContentState<T>>,
        onContentUnavailable: (ApiFailure) -> Unit = {},
        onReadFailure: (ApiFailure) -> Unit = {},
        fetch: suspend (Long) -> T,
    ) {
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
                currentCoroutineContext().ensureActive()
                if (expected == repository.session.value.revision) {
                    target.value = target.value.readFailed(error.failure)
                    if (!error.failure.retainsContent) onContentUnavailable(error.failure)
                    onReadFailure(error.failure)
                }
            }
        }
    }

    protected fun mutate(operation: ContentMutation = ContentMutation.OTHER, block: suspend (Long) -> Unit) {
        if (_mutation.value != null) return
        val expected = revision
        _mutation.value = operation
        _actionError.value = null
        launchTask {
            try {
                block(expected)
            } catch (error: ApiException) {
                if (expected == repository.session.value.revision) _actionError.value = error.failure
            } finally {
                if (expected == revision) _mutation.value = null
            }
        }
    }

    fun dismissActionError() { _actionError.value = null }

    protected fun <T> cancelRead(target: MutableStateFlow<ContentState<T>>) {
        reads.remove(target)?.cancel()
    }

    protected fun launchTask(block: suspend () -> Unit): Job = tasks.launch(block)
}
