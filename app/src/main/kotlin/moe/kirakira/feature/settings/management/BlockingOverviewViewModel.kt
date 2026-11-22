package moe.kirakira.feature.settings.management

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.data.settings.RuleCategory

internal class BlockingOverviewViewModel(repository: AccountSettingsRepository) : SettingsViewModel(repository) {
    private val _counts = MutableStateFlow(SettingsLoad<Map<RuleCategory, Int>>())
    val counts = _counts.asStateFlow()
    private var read: Job? = null

    init {
        observe({ _counts.value = SettingsLoad() }, ::refresh)
        viewModelScope.launch { repository.ruleVersion.drop(1).collect { refresh() } }
    }

    fun refresh() {
        if (!ready) return
        read?.cancel()
        _counts.value = _counts.value.copy(loading = true, error = null)
        read = task { expected ->
            try {
                val counts = coroutineScope {
                    RuleCategory.entries.map { category ->
                        async { category to repository.rules(category, 1, expected, 1).total }
                    }.awaitAll().toMap()
                }
                _counts.value = SettingsLoad(counts)
            } catch (error: ApiException) {
                currentCoroutineContext().ensureActive()
                if (expected == session.value.revision) _counts.value = _counts.value.readFailed(error.failure)
            }
        }
    }
}
