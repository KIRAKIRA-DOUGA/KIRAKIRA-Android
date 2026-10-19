package moe.kirakira.data.auth

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import moe.kirakira.core.network.ApiFailure

/** Legacy v1 envelope, retained for migration and existing offline fixtures. */
internal class SessionCipher {
    private val codec = SessionCodec()

    /** Fixture support only; production account writes use SessionCodec directly. */
    fun encrypt(state: StoredSessions, key: SecretKey): ByteArray = sessionStorageOperation(ApiFailure.STORAGE_DATA) {
        val plaintext = codec.encode(state)
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

    fun decrypt(bytes: ByteArray, key: SecretKey): StoredSessions = sessionStorageOperation(ApiFailure.STORAGE_DATA) {
        require(bytes.size in MIN_BYTES..MAX_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, bytes.copyOfRange(0, NONCE_BYTES)))
        // Authenticate the complete ciphertext before any JSON is decoded or exposed.
        val plaintext = cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
        try {
            codec.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    companion object {
        const val MAX_BYTES = SessionCodec.MAX_BYTES
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val MIN_BYTES = NONCE_BYTES + TAG_BYTES + 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
