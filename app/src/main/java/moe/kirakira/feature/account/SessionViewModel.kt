package moe.kirakira.feature.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import moe.kirakira.BuildConfig
import moe.kirakira.core.credentials.PasswordCredentialGateway
import moe.kirakira.core.credentials.passwordCredentialGateway
import moe.kirakira.core.network.ApiClient
import moe.kirakira.core.network.ApiException
import moe.kirakira.data.auth.AuthApi
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.auth.SessionOperation
import moe.kirakira.data.auth.SessionOperationType
import moe.kirakira.data.auth.SessionStore

internal class SessionViewModel(
    application: Application,
    val repository: AuthRepository,
    private val credentials: PasswordCredentialGateway,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        AuthRepository(
            AuthApi(ApiClient(BuildConfig.API_BASE_URL)),
            SessionStore(application, BuildConfig.API_BASE_URL),
            BuildConfig.API_BASE_URL,
        ),
        passwordCredentialGateway(application),
    )
    val state = repository.session
    private var operation: Job? = null
    private var retryOperation: (suspend () -> Unit)? = null
    private var retryTarget = SessionOperation(SessionOperationType.INITIALIZE)

    init {
        retry()
    }

    fun select(uuid: String?) = runOperation(SessionOperation(SessionOperationType.SWITCH, uuid)) {
        repository.select(uuid)
        if (uuid == null) clearProviderSession()
    }

    fun remove(uuid: String) = runOperation(SessionOperation(SessionOperationType.REMOVE, uuid)) {
        val removingCurrent = state.value.activeUuid == uuid
        repository.remove(uuid)
        if (removingCurrent) clearProviderSession()
    }

    fun resetLocalAccounts() = runOperation(SessionOperation(SessionOperationType.RESET)) {
        repository.resetLocalAccounts()
        clearProviderSession()
    }

    fun logout() {
        state.value.activeUuid?.let(::remove)
    }

    fun dismissError() {
        repository.reportError(null)
    }

    fun retry() = runOperation(retryTarget, retryOperation ?: {
        repository.initialize()
        state.value.activeUuid?.let { uuid ->
            repository.setOperation(SessionOperation(SessionOperationType.INITIALIZE, uuid))
            // Keep startup validation retryable even if rejection removes the active identity.
            retryTarget = SessionOperation(SessionOperationType.SWITCH, uuid)
            retryOperation = { repository.select(uuid) }
            repository.select(uuid)
        }
        Unit
    })

    private suspend fun clearProviderSession() {
        try {
            credentials.clearSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Local account changes have committed; provider failure cannot undo them.
        }
    }

    private fun runOperation(target: SessionOperation, block: suspend () -> Unit) {
        if (operation?.isActive == true) return
        retryTarget = target
        retryOperation = block
        operation = viewModelScope.launch {
            repository.reportError(null)
            repository.setOperation(target)
            try {
                block()
            } catch (error: ApiException) {
                repository.reportError(error.failure, state.value.operation?.targetUuid ?: target.targetUuid)
            } finally {
                repository.setOperation(null)
            }
        }
    }
}
