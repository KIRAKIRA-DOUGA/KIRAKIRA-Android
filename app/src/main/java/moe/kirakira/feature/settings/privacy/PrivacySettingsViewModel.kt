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
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.data.settings.PrivacyItem
import moe.kirakira.data.settings.PrivacySettings
import moe.kirakira.data.settings.PrivacyVisibility

internal data class PrivacySettingsState(
    val revision: Long? = null,
    val original: PrivacySettings? = null,
    val draft: Map<PrivacyItem, PrivacyVisibility> = emptyMap(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: ApiFailure? = null,
    val message: Int? = null,
    val messageFailure: ApiFailure? = null,
    val sheetOpen: Boolean = false,
    val selectedItem: PrivacyItem? = null,
    val confirmDiscard: Boolean = false,
) {
    val dirty: Boolean get() = original != null && draft != original.values
    val busy: Boolean get() = loading || saving
    val editable: Boolean get() = original != null && !busy
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
                _state.value = PrivacySettingsState(revision = expected, original = settings, draft = settings.values)
            } catch (error: ApiException) {
                _state.update {
                    if (it.original == null) it.copy(error = error.failure)
                    else it.copy(messageFailure = error.failure)
                }
            } finally {
                if (currentCoroutineContext().isActive && expected == session.value.revision) {
                    _state.update { it.copy(loading = false) }
                }
            }
        }
    }

    fun save() {
        val current = _state.value
        val original = current.original ?: return
        if (!ready || !current.editable || !current.dirty) return
        val expected = revision
        _state.update { it.copy(saving = true, message = null, messageFailure = null, sheetOpen = false) }
        request = viewModelScope.launch {
            try {
                val settings = repository.savePrivacy(original, current.draft, expected)
                _state.value = PrivacySettingsState(revision = expected, original = settings, draft = settings.values,
                    message = R.string.privacy_saved)
            } catch (error: ApiException) {
                _state.update { it.copy(messageFailure = error.failure) }
            } finally {
                if (currentCoroutineContext().isActive && expected == session.value.revision) {
                    _state.update { it.copy(saving = false) }
                }
            }
        }
    }

    fun selectAll(visibility: PrivacyVisibility) {
        if (!ready || !_state.value.editable) return
        _state.update { it.copy(draft = PrivacyItem.entries.associateWith { visibility }) }
    }

    fun openSelector(item: PrivacyItem?) {
        if (ready && _state.value.editable) _state.update { it.copy(sheetOpen = true, selectedItem = item) }
    }

    fun closeSelector() { _state.update { it.copy(sheetOpen = false) } }

    fun select(visibility: PrivacyVisibility) {
        if (!ready || !_state.value.editable) return
        _state.update { current ->
            val item = current.selectedItem
            val draft = if (item == null) PrivacyItem.entries.associateWith { visibility }
                else current.draft + (item to visibility)
            current.copy(draft = draft, sheetOpen = false)
        }
    }

    val canNavigateBack: Boolean get() = !_state.value.busy && !_state.value.dirty

    fun requestBack(): Boolean {
        if (_state.value.busy) return false
        if (!_state.value.dirty) return true
        _state.update { it.copy(confirmDiscard = true, sheetOpen = false) }
        return false
    }

    fun cancelDiscard() { _state.update { it.copy(confirmDiscard = false) } }
    fun discard() {
        if (_state.value.busy) return
        _state.update { it.copy(draft = it.original?.values.orEmpty(), confirmDiscard = false) }
    }
    fun dismissMessage() { _state.update { it.copy(message = null, messageFailure = null) } }
}
