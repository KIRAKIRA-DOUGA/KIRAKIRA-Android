package moe.kirakira.feature.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.data.settings.PrivacyItem
import moe.kirakira.data.settings.PrivacySaveResult
import moe.kirakira.data.settings.PrivacySettings
import moe.kirakira.data.settings.PrivacyVisibility

internal data class PrivacySettingsState(
    val revision: Long? = null,
    val original: PrivacySettings? = null,
    val values: Map<PrivacyItem, PrivacyVisibility> = emptyMap(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val confirmationPending: Boolean = false,
    val error: ApiFailure? = null,
    val message: Int? = null,
    val messageFailure: ApiFailure? = null,
    val sheetOpen: Boolean = false,
    val selectedItem: PrivacyItem? = null,
    val confirmReset: Boolean = false,
) {
    val busy: Boolean get() = loading || saving
    val editable: Boolean get() = original != null && !busy && !confirmationPending
    val canReset: Boolean get() = editable && values.values.any { it != PrivacyVisibility.PUBLIC }
}

internal class PrivacySettingsViewModel(private val repository: AccountSettingsRepository) : ViewModel() {
    val session = repository.session
    private val _state = MutableStateFlow(PrivacySettingsState())
    val state = _state.asStateFlow()
    private var revision = session.value.revision
    private var request: Job? = null

    init {
        viewModelScope.launch {
            session.map { Triple(it.revision, it.isLoading, it.isBusy) }.distinctUntilChanged().collect {
                request?.cancel()
                revision = session.value.revision
                _state.value = PrivacySettingsState(revision = revision)
                if (ready) refresh()
            }
        }
    }

    private val ready: Boolean
        get() = revision == session.value.revision && session.value.activeProfile != null &&
            !session.value.isLoading && !session.value.isBusy

    fun refresh() {
        if (!ready || _state.value.busy) return
        val expected = revision
        _state.update { it.copy(loading = true, error = null, message = null, messageFailure = null, sheetOpen = false) }
        request = viewModelScope.launch {
            try {
                val settings = repository.privacy(expected)
                _state.value = PrivacySettingsState(revision = expected, original = settings, values = settings.values)
            } catch (error: ApiException) {
                _state.update {
                    if (it.original == null || it.confirmationPending) it.copy(error = error.failure)
                    else it.copy(messageFailure = error.failure)
                }
            } finally {
                if (currentCoroutineContext().isActive && expected == session.value.revision) {
                    _state.update { it.copy(loading = false) }
                }
            }
        }
    }

    private fun apply(values: Map<PrivacyItem, PrivacyVisibility>) {
        val current = _state.value
        val original = current.original ?: return
        if (!ready || !current.editable) return
        if (values == original.values) {
            _state.update { it.copy(sheetOpen = false, confirmReset = false) }
            return
        }
        val expected = revision
        _state.update {
            it.copy(values = values, saving = true, message = null, messageFailure = null,
                sheetOpen = false, confirmReset = false)
        }
        request = viewModelScope.launch {
            try {
                when (val result = repository.savePrivacy(original, values, expected)) {
                    is PrivacySaveResult.Confirmed -> {
                        val settings = result.settings
                        _state.value = PrivacySettingsState(
                            revision = expected, original = settings, values = settings.values,
                            message = R.string.privacy_saved,
                        )
                    }
                    is PrivacySaveResult.Unconfirmed -> _state.update {
                        it.copy(confirmationPending = true, error = result.failure)
                    }
                }
            } catch (error: ApiException) {
                _state.update { it.copy(values = original.values, messageFailure = error.failure) }
            } finally {
                if (currentCoroutineContext().isActive && expected == session.value.revision) {
                    _state.update { it.copy(saving = false) }
                }
            }
        }
    }

    fun selectAll(visibility: PrivacyVisibility) {
        apply(PrivacyItem.entries.associateWith { visibility })
    }

    fun openSelector(item: PrivacyItem?) {
        if (ready && _state.value.editable) _state.update { it.copy(sheetOpen = true, selectedItem = item) }
    }

    fun closeSelector() { _state.update { it.copy(sheetOpen = false) } }

    fun select(visibility: PrivacyVisibility) {
        val current = _state.value
        if (!current.sheetOpen) return
        val item = current.selectedItem
        val values = if (item == null) PrivacyItem.entries.associateWith { visibility }
            else current.values + (item to visibility)
        apply(values)
    }

    fun requestReset() {
        if (ready && _state.value.canReset) _state.update { it.copy(confirmReset = true, sheetOpen = false) }
    }

    fun cancelReset() { _state.update { it.copy(confirmReset = false) } }

    fun reset() {
        if (!_state.value.confirmReset) return
        selectAll(PrivacyVisibility.PUBLIC)
    }

    val canNavigateBack: Boolean get() = !_state.value.busy

    fun requestBack(): Boolean = canNavigateBack

    fun dismissMessage() { _state.update { it.copy(message = null, messageFailure = null) } }
}
