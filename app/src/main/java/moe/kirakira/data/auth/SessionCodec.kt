package moe.kirakira.data.auth

import kotlinx.serialization.json.Json
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Versioned account JSON shared by the private file and the legacy migration decoder. */
internal class SessionCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: StoredSessions): ByteArray = sessionStorageOperation(ApiFailure.STORAGE_DATA) {
        validate(state)
        json.encodeToString(state).toByteArray(Charsets.UTF_8).also { bytes ->
            if (bytes.size > MAX_BYTES) {
                bytes.fill(0)
                throw ApiException(ApiFailure.STORAGE_DATA)
            }
        }
    }

    fun decode(bytes: ByteArray): StoredSessions = sessionStorageOperation(ApiFailure.STORAGE_DATA) {
        require(bytes.size in 1..MAX_BYTES)
        json.decodeFromString<StoredSessions>(bytes.decodeToString(throwOnInvalidSequence = true)).also(::validate)
    }

    private fun validate(state: StoredSessions) {
        require(state.version == 1)
        val url = state.baseUrl.toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
        require(state.accounts.map { it.profile.uuid }.distinct().size == state.accounts.size)
        state.accounts.forEach { account ->
            require(account.profile.uuid.matches(Regex("[a-zA-Z0-9-]+")))
            require(account.profile.uid >= 0 && account.profile.email.isNotBlank())
            account.userDataBootstrapHint?.let { require(it.matches(Regex("[a-zA-Z0-9_-]+"))) }
            account.token?.let { token ->
                require(token.isNotBlank() && token.all { it.code in 0x21..0x7E && it != ';' && it != ',' })
            }
        }
        require(state.activeUuid == null || state.accounts.any {
            it.profile.uuid == state.activeUuid && it.token != null && !it.requiresLogin
        })
    }

    companion object {
        const val MAX_BYTES = 1_048_576
    }
}
