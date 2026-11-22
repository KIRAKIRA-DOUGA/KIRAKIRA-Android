package moe.kirakira.data.auth

import java.security.KeyStore
import javax.crypto.SecretKey

/** Reads existing v1 keys only; normal account persistence never calls Android Keystore. */
internal class LegacySessionReader(private val keyAlias: String) {
    fun read(bytes: ByteArray): StoredSessions {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(keyAlias, null) as SecretKey
        return SessionCipher().decrypt(bytes, key)
    }
}
