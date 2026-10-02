package moe.kirakira.data.auth

import android.os.SystemClock
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure

/** Identity of one in-memory authentication flow; never persisted or sent to the backend. */
internal class AuthFlowOwner

internal class AuthRepository(
    private val api: AuthApi,
    private val store: SessionPersistence,
    private val baseUrl: String,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private val codeMutex = Mutex()
    private val codeDeadlines = ConcurrentHashMap<String, Long>()
    private var stored: StoredSessions? = null
    private var pendingAuthentication: PendingAuthentication? = null
    private var pendingPasswordReset: PendingPasswordReset? = null
    private val _session = MutableStateFlow(SessionState())
    val session = _session.asStateFlow()

    fun hasPendingAuthentication(owner: AuthFlowOwner): Boolean = pendingAuthentication?.owner === owner
    fun hasPendingPasswordReset(owner: AuthFlowOwner): Boolean = pendingPasswordReset?.owner === owner

    suspend fun initialize() = mutex.withLock {
        if (stored != null) return@withLock
        try {
            stored = store.read()
            publish()
        } catch (error: ApiException) {
            _session.value = SessionState(isLoading = false, error = error.failure)
        }
    }

    /** Data-layer snapshot; credentials must never be passed to a ViewModel or composable. */
    internal suspend fun requestSession(): RequestSession = mutex.withLock {
        val account = stored?.accounts?.find {
            it.profile.uuid == _session.value.activeUuid && it.token != null && !it.requiresLogin
        }
        RequestSession(_session.value.revision, account)
    }

    internal fun isCurrent(request: RequestSession): Boolean = request.revision == _session.value.revision

    internal suspend fun fetchProfile(request: RequestSession): AccountProfile {
        val account = request.account ?: throw ApiException(ApiFailure.SESSION_EXPIRED)
        if (!isCurrent(request)) throw kotlinx.coroutines.CancellationException("Account changed")
        val profile = api.profile(account)
        if (!isCurrent(request)) throw kotlinx.coroutines.CancellationException("Account changed")
        return profile
    }

    internal suspend fun publishProfile(
        request: RequestSession,
        profile: AccountProfile,
        onPublishing: (Long) -> Unit = {},
    ) = mutex.withLock {
        if (!isCurrent(request)) throw kotlinx.coroutines.CancellationException("Account changed")
        val previous = stored ?: throw ApiException(ApiFailure.STORAGE)
        val account = request.account ?: throw ApiException(ApiFailure.SESSION_EXPIRED)
        if (profile.uuid != account.profile.uuid || profile.uid != account.profile.uid) {
            throw ApiException(ApiFailure.INVALID_RESPONSE)
        }
        commit(StoredSessions(baseUrl = baseUrl, activeUuid = previous.activeUuid,
            accounts = previous.accounts.map {
                if (it.profile.uuid == profile.uuid) StoredAccount(profile, it.token, it.requiresLogin,
                    it.userDataBootstrapHint) else it
            }), onPublishing)
    }

    internal suspend fun expireRequestSession(request: RequestSession) = mutex.withLock {
        if (!isCurrent(request)) return@withLock
        val previous = stored ?: return@withLock
        val uuid = request.account?.profile?.uuid ?: return@withLock
        commit(StoredSessions(baseUrl = baseUrl,
            accounts = previous.accounts.map {
                if (it.profile.uuid == uuid) StoredAccount(it.profile, requiresLogin = true) else it
            }, activeUuid = previous.activeUuid.takeUnless { it == uuid }))
        reportError(ApiFailure.SESSION_EXPIRED, uuid)
    }

    internal class RequestSession(val revision: Long, val account: StoredAccount?)

    suspend fun secondFactor(email: String): SecondFactor = api.secondFactor(email)
    suspend fun checkUsername(username: String): Boolean = api.checkUsername(username)
    suspend fun emailExists(email: String): Boolean = api.emailExists(email)
    suspend fun checkInvitation(code: String): Boolean = api.checkInvitation(code)

    fun resendSeconds(email: String): Int {
        val remaining = (codeDeadlines[email.lowercase()] ?: 0L) - elapsedRealtime()
        return ((remaining.coerceAtLeast(0) + 999) / 1_000).toInt()
    }

    suspend fun sendCode(email: String, purpose: VerificationPurpose, language: String) =
        withVerificationCooldown(email) { api.sendCode(email, purpose, language) }

    internal suspend fun withVerificationCooldown(email: String, send: suspend () -> Unit) = codeMutex.withLock {
        // Rosales applies the cooldown per email, across all verification purposes.
        if (resendSeconds(email) > 0) throw ApiException(ApiFailure.RATE_LIMITED)
        try {
            send()
            codeDeadlines[email.lowercase()] = elapsedRealtime() + 60_000
        } catch (error: ApiException) {
            if (error.failure == ApiFailure.RATE_LIMITED) {
                codeDeadlines[email.lowercase()] = elapsedRealtime() + 60_000
            }
            throw error
        }
    }

    suspend fun login(email: String, password: String, code: String, factor: SecondFactor, owner: AuthFlowOwner) {
        requireStorage()
        pendingAuthentication = PendingAuthentication(api.login(email, hashPassword(password), code, factor), owner)
        finishAuthentication(owner)
    }

    suspend fun register(
        email: String,
        password: String,
        code: String,
        invitation: String,
        username: String,
        nickname: String,
        passwordHint: String,
        owner: AuthFlowOwner,
    ) {
        requireStorage()
        pendingAuthentication = PendingAuthentication(
            api.register(email, hashPassword(password), code, invitation, username, nickname, passwordHint),
            owner,
        )
        finishAuthentication(owner)
    }

    suspend fun finishAuthentication(owner: AuthFlowOwner) {
        val pending = pendingAuthentication?.takeIf { it.owner === owner } ?: throw ApiException(ApiFailure.REJECTED)
        try {
            saveAuthenticated(pending)
        } catch (error: ApiException) {
            if (error.failure in setOf(ApiFailure.INVALID_RESPONSE, ApiFailure.SESSION_EXPIRED) &&
                pendingAuthentication === pending
            ) pendingAuthentication = null
            throw error
        }
        // A canceled non-cancellable disk commit may finish after a newer login has started.
        if (pendingAuthentication === pending) pendingAuthentication = null
    }

    fun discardPendingAuthentication(owner: AuthFlowOwner) {
        if (pendingAuthentication?.owner === owner) pendingAuthentication = null
        if (pendingPasswordReset?.owner === owner) pendingPasswordReset = null
    }

    private fun requireStorage() {
        if (stored == null) throw ApiException(ApiFailure.STORAGE)
    }

    private suspend fun saveAuthenticated(pending: PendingAuthentication) = mutex.withLock {
        val previous = stored ?: throw ApiException(ApiFailure.STORAGE)
        // Resolve at most once: retrying a failed local write must work without another API request.
        val account = pending.resolvedAccount ?: run {
            val resolved = try {
                api.refreshAccount(pending.account)
            } catch (error: ApiException) {
                if (error.failure == ApiFailure.SESSION_EXPIRED || error.failure == ApiFailure.INVALID_RESPONSE) throw error
                pending.account
            }
            resolved.also { pending.resolvedAccount = it }
        }
        commit(
            StoredSessions(
                baseUrl = baseUrl,
                accounts = previous.accounts.filterNot { it.profile.uuid == account.profile.uuid } + account,
                activeUuid = account.profile.uuid,
            ),
        )
    }

    suspend fun select(uuid: String?) = mutex.withLock {
        val previous = stored ?: throw ApiException(ApiFailure.STORAGE)
        if (uuid == null) {
            commit(StoredSessions(baseUrl = baseUrl, accounts = previous.accounts))
            return@withLock
        }
        val account = previous.accounts.find { it.profile.uuid == uuid } ?: return@withLock
        val ownsOperation = _session.value.operation == null
        if (ownsOperation) setOperation(SessionOperation(SessionOperationType.SWITCH, uuid))
        try {
            val refreshed = api.refreshAccount(account)
            commit(
                StoredSessions(
                    baseUrl = baseUrl,
                    accounts = previous.accounts.map { if (it.profile.uuid == uuid) refreshed else it },
                    activeUuid = uuid,
                ),
            )
        } catch (error: ApiException) {
            if (error.failure == ApiFailure.SESSION_EXPIRED || error.failure == ApiFailure.REJECTED) {
                commit(
                    StoredSessions(
                        baseUrl = baseUrl,
                        accounts = previous.accounts.map {
                            if (it.profile.uuid == uuid) StoredAccount(
                                it.profile,
                                token = it.token.takeUnless { error.failure == ApiFailure.SESSION_EXPIRED },
                                requiresLogin = true,
                            ) else it
                        },
                        activeUuid = previous.activeUuid.takeUnless { it == uuid },
                    ),
                )
            }
            throw error
        } finally {
            if (ownsOperation) setOperation(null)
        }
    }

    suspend fun remove(uuid: String) = mutex.withLock {
        val previous = stored ?: throw ApiException(ApiFailure.STORAGE)
        // /user/logout only clears browser cookies; local removal is the native equivalent, also offline.
        commit(
            StoredSessions(
                baseUrl = baseUrl,
                accounts = previous.accounts.filterNot { it.profile.uuid == uuid },
                activeUuid = previous.activeUuid.takeUnless { it == uuid },
            ),
        )
    }

    internal suspend fun removeRequestSession(request: RequestSession, onPublishing: (Long) -> Unit) = mutex.withLock {
        if (!isCurrent(request)) throw kotlinx.coroutines.CancellationException("Account changed")
        val previous = stored ?: throw ApiException(ApiFailure.STORAGE)
        val uuid = request.account?.profile?.uuid ?: throw ApiException(ApiFailure.SESSION_EXPIRED)
        commit(StoredSessions(baseUrl = baseUrl, accounts = previous.accounts.filterNot { it.profile.uuid == uuid },
            activeUuid = previous.activeUuid.takeUnless { it == uuid }), onPublishing)
    }

    suspend fun resetPassword(email: String, password: String, code: String, owner: AuthFlowOwner) {
        requireStorage()
        api.resetPassword(email, hashPassword(password), code)
        pendingPasswordReset = PendingPasswordReset(email, owner)
        finishPasswordReset(owner)
    }

    suspend fun finishPasswordReset(owner: AuthFlowOwner) {
        val pending = pendingPasswordReset?.takeIf { it.owner === owner } ?: throw ApiException(ApiFailure.REJECTED)
        val email = pending.email
        mutex.withLock {
            val previous = stored ?: throw ApiException(ApiFailure.STORAGE)
            val affected = previous.accounts.filter { it.profile.email.equals(email, ignoreCase = true) }
            if (affected.isEmpty()) return@withLock
            commit(
                StoredSessions(
                    baseUrl = baseUrl,
                    accounts = previous.accounts.map { if (it in affected) StoredAccount(it.profile) else it },
                    activeUuid = previous.activeUuid.takeUnless { uuid -> affected.any { it.profile.uuid == uuid } },
                ),
            )
        }
        if (pendingPasswordReset === pending) pendingPasswordReset = null
    }

    fun reportError(failure: ApiFailure?, uuid: String? = null) =
        _session.update { it.copy(error = failure, failedAccountUuid = uuid) }

    fun setOperation(operation: SessionOperation?) = _session.update { it.copy(operation = operation) }

    suspend fun resetLocalAccounts() = mutex.withLock {
        withContext(NonCancellable) {
            store.reset()
            stored = StoredSessions(baseUrl = baseUrl)
            pendingAuthentication = null
            pendingPasswordReset = null
            publish()
        }
    }

    private suspend fun commit(next: StoredSessions, onPublishing: (Long) -> Unit = {}) {
        currentCoroutineContext().ensureActive()
        // Keep disk and the published active identity consistent even if an entry is popped during the write.
        withContext(NonCancellable) {
            store.write(next)
            stored = next
            publish(onPublishing)
        }
    }

    private fun publish(onPublishing: (Long) -> Unit = {}) {
        val state = stored ?: return
        val nextRevision = _session.value.revision + 1
        onPublishing(nextRevision)
        _session.value = SessionState(
            accounts = state.accounts.map { SavedAccount(it.profile, it.token == null || it.requiresLogin) },
            activeUuid = state.activeUuid.takeIf { id ->
                state.accounts.any { it.profile.uuid == id && it.token != null && !it.requiresLogin }
            },
            isLoading = false,
            revision = nextRevision,
            operation = _session.value.operation,
        )
    }

    internal suspend fun hashPassword(password: String): String = withContext(hashDispatcher) {
        val input = password.toByteArray(Charsets.UTF_8)
        try {
            val digest = MessageDigest.getInstance("SHA-256").digest(input)
            try {
                digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            } finally {
                digest.fill(0)
            }
        } finally {
            input.fill(0)
        }
    }

    private class PendingAuthentication(
        val account: StoredAccount,
        val owner: AuthFlowOwner,
        var resolvedAccount: StoredAccount? = null,
    )
    private class PendingPasswordReset(val email: String, val owner: AuthFlowOwner)
}
