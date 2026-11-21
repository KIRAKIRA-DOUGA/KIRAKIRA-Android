package moe.kirakira.feature.settings.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.kirakira.core.coroutines.AccountTasks
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.ui.components.status.messageRes

/** Jobs and drafts are owned by the currently published session revision. */
internal abstract class SettingsViewModel(protected val repository: AccountSettingsRepository) : ViewModel() {
    val session = repository.session
    private val tasks = AccountTasks(viewModelScope)
    protected var revision = session.value.revision
    protected val busyState = MutableStateFlow(false)
    val busy = busyState.asStateFlow()
    protected val messageState = MutableStateFlow<Int?>(null)
    val message = messageState.asStateFlow()
    protected val ready: Boolean
        get() = !session.value.isLoading && !session.value.isBusy && session.value.activeProfile != null

    protected fun observe(reset: () -> Unit, refresh: () -> Unit) {
        viewModelScope.launch {
            // Temporary session operations do not retire confirmed data or cancel account writes.
            session.filter { !it.isLoading && !it.isBusy }.map { it.revision }.distinctUntilChanged().collect {
                revision = it
                tasks.cancelCurrent()
                busyState.value = false
                messageState.value = null
                reset()
                if (ready) refresh()
            }
        }
    }

    protected fun task(block: suspend (Long) -> Unit): Job {
        val expected = revision
        return tasks.launch { block(expected) }
    }

    fun dismissMessage() { messageState.value = null }
    protected fun report(error: ApiException) { messageState.value = error.failure.messageRes() }
}
