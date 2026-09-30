package moe.kirakira.data.auth

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Existing v1 format: a provider-generated 96-bit nonce, ciphertext, and a 128-bit GCM tag. */
internal class SessionCipher {
    private val json = Json { ignoreUnknownKeys = true }

    fun encrypt(state: StoredSessions, key: SecretKey): ByteArray = storageOperation {
        validate(state)
        val plaintext = json.encodeToString(state).toByteArray(Charsets.UTF_8)
        try {
            require(plaintext.size <= MAX_BYTES - NONCE_BYTES - TAG_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // Android Keystore generates the nonce; never reuse or derive one from account data.
            cipher.init(Cipher.ENCRYPT_MODE, key)
            require(cipher.iv.size == NONCE_BYTES)
            cipher.iv + cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    fun decrypt(bytes: ByteArray, key: SecretKey): StoredSessions = storageOperation {
        require(bytes.size in MIN_BYTES..MAX_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, bytes.copyOfRange(0, NONCE_BYTES)))
        // Authenticate the complete ciphertext before any JSON is decoded or exposed.
        val plaintext = cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
        try {
            json.decodeFromString<StoredSessions>(plaintext.decodeToString(throwOnInvalidSequence = true))
                .also(::validate)
        } finally {
            plaintext.fill(0)
        }
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

    private inline fun <T> storageOperation(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        // Neither plaintext nor provider error details belong in UI errors or logs.
        throw ApiException(ApiFailure.STORAGE)
    }

    companion object {
        const val MAX_BYTES = 1_048_576
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val MIN_BYTES = NONCE_BYTES + TAG_BYTES + 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
