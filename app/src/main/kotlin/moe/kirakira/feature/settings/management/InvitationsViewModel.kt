package moe.kirakira.feature.settings.management

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.data.settings.Invitation
import moe.kirakira.data.settings.InvitationCreation
import moe.kirakira.ui.components.status.messageRes

internal class InvitationsViewModel(repository: AccountSettingsRepository) : SettingsViewModel(repository) {
    private val _invitations = MutableStateFlow(SettingsLoad<List<Invitation>>())
    val invitations = _invitations.asStateFlow()
    private val _confirmCreation = MutableStateFlow(false)
    val creationConfirmation = _confirmCreation.asStateFlow()
    private var unconfirmedCodes: Set<String>? = null
    private var read: Job? = null
    // Preserve a confirmed generation if the following read fails or is briefly stale.
    private val generated = mutableMapOf<String, Invitation>()

    init {
        observe({
            generated.clear()
            unconfirmedCodes = null
            _confirmCreation.value = false
            _invitations.value = SettingsLoad()
        }, ::refresh)
    }

    fun refresh() {
        if (!ready || busyState.value) return
        read?.cancel()
        _invitations.value = _invitations.value.copy(loading = true, error = null)
        read = task { expected ->
            try {
                val result = repository.invitations(expected)
                unconfirmedCodes?.let { before ->
                    if (result.any { it.code !in before }) unconfirmedCodes = null
                }
                result.forEach { generated.remove(it.code) }
                _invitations.value = SettingsLoad((result + generated.values).distinctBy { it.code })
            } catch (error: ApiException) {
                currentCoroutineContext().ensureActive()
                if (expected == session.value.revision) {
                    if (!error.failure.retainsContent) generated.clear()
                    _invitations.value = _invitations.value.readFailed(error.failure)
                }
            }
        }
    }

    fun create() {
        if (!ready || busyState.value) return
        if (unconfirmedCodes != null) {
            _confirmCreation.value = true
            return
        }
        createInvitation()
    }

    fun dismissCreation() { _confirmCreation.value = false }

    fun confirmCreation() {
        if (!_confirmCreation.value || !ready || busyState.value) return
        dismissCreation()
        createInvitation()
    }

    private fun createInvitation() {
        if (!ready || busyState.value) return
        if (_invitations.value.loading) return
        if (_invitations.value.error != null) {
            refresh()
            return
        }
        read?.cancel()
        _invitations.value = _invitations.value.copy(loading = false)
        busyState.value = true
        val previousCodes = _invitations.value.data.orEmpty().map { it.code }.toSet()
        task { expected ->
            var created = false
            var uncertain = false
            try {
                when (val result = repository.createInvitation(expected)) {
                    InvitationCreation.CoolingDown -> messageState.value = R.string.invitation_cooling_down
                    is InvitationCreation.Created -> {
                        unconfirmedCodes = null
                        generated[result.invitation.code] = result.invitation
                        _invitations.value = SettingsLoad(
                            (_invitations.value.data.orEmpty() + result.invitation).distinctBy { it.code },
                        )
                        messageState.value = R.string.invitation_created
                        created = true
                    }
                }
            } catch (error: ApiException) {
                uncertain = error.failure.isUncertainMutation
                if (uncertain) unconfirmedCodes = previousCodes
                messageState.value = if (uncertain) R.string.invitation_creation_unconfirmed else error.failure.messageRes()
            } finally {
                if (expected == revision) {
                    busyState.value = false
                    if (created || uncertain) refresh()
                }
            }
        }
    }
}
