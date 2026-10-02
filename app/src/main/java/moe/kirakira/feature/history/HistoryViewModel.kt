package moe.kirakira.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.kirakira.core.network.ApiClient
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.history.HistoryRepository

internal class HistoryHostViewModel(api: ApiClient, auth: AuthRepository) : ViewModel() {
    val repository = HistoryRepository(api, auth, viewModelScope)
}

internal data class HistoryQuery(val revision: Long, val text: String = "")

internal class HistoryViewModel(val repository: HistoryRepository) : ViewModel() {
    val history = repository.history
    val session = repository.session
    private var refreshTask: Job? = null
    private val _query = MutableStateFlow(HistoryQuery(session.value.revision))
    val query = _query.asStateFlow()
    private var revision = session.value.revision

    init {
        viewModelScope.launch {
            session.collect {
                if (it.revision != revision) {
                    revision = it.revision
                    refreshTask?.cancel()
                    _query.value = HistoryQuery(revision)
                }
            }
        }
    }

    fun updateQuery(value: String) { _query.value = HistoryQuery(session.value.revision, value) }

    fun refresh() {
        if (refreshTask?.isActive == true) return
        val expected = session.value.revision
        refreshTask = viewModelScope.launch {
            try {
                repository.refresh(expected)
            } catch (_: ApiException) {
                // Repository publishes the failure, preserving any previously loaded content.
            }
        }
    }
}
