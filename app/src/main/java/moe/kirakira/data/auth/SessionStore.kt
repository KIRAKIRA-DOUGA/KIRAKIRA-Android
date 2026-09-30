package moe.kirakira.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure

/** All account metadata and tokens are encrypted, atomically written, and excluded from backup. */
internal class SessionStore(
    context: Context,
    private val baseUrl: String,
    private val keyAlias: String = "kirakira.sessions.v1",
) : SessionPersistence {
    private val file = AtomicFile(File(context.noBackupFilesDir, "sessions.v1.enc"))
    private val cipher = SessionCipher()
    private val mutex = Mutex()
    private var writable = false

    override suspend fun read(): StoredSessions = mutex.withLock {
        withContext(Dispatchers.IO) {
            writable = false
            try {
                // openRead recovers a legacy .bak first, even when the base file is absent.
                val bytes = try {
                    file.openRead().use(::readBounded)
                } catch (error: FileNotFoundException) {
                    if (hasFiles()) throw error
                    writable = true
                    return@withContext StoredSessions(baseUrl = baseUrl)
                }
                // Decryption never creates or replaces a missing key.
                val state = cipher.decrypt(bytes, key(createIfMissing = false))
                writable = true
                // An authenticated vault for a different backend is never used for requests.
                if (state.baseUrl == baseUrl) state else StoredSessions(baseUrl = baseUrl)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the encrypted file and block writes until read succeeds or the user resets it.
                throw ApiException(ApiFailure.STORAGE)
            }
        }
    }

    override suspend fun write(state: StoredSessions) = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                check(writable)
                require(state.baseUrl == baseUrl)
                val bytes = cipher.encrypt(state, key(createIfMissing = !hasFiles()))
                val output = file.startWrite()
                try {
                    output.write(bytes)
                    file.finishWrite(output)
                } catch (error: Exception) {
                    file.failWrite(output)
                    throw error
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                throw ApiException(ApiFailure.STORAGE)
            }
        }
    }

    /** Only called after the user confirms discarding the local account vault. */
    override suspend fun reset() = mutex.withLock {
        withContext(Dispatchers.IO) {
            writable = false
            try {
                file.delete()
                // Older AtomicFile implementations do not know about the newer .new suffix.
                for (suffix in listOf(".bak", ".new")) {
                    val remainder = File(file.baseFile.path + suffix)
                    if (remainder.exists()) check(remainder.delete())
                }
                check(!hasFiles())
                keyStore().deleteEntry(keyAlias)
                writable = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                throw ApiException(ApiFailure.STORAGE)
            }
        }
    }

    private fun key(createIfMissing: Boolean): SecretKey {
        val store = keyStore()
        if (store.containsAlias(keyAlias)) return store.getKey(keyAlias, null) as SecretKey
        check(createIfMissing)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun hasFiles(): Boolean = listOf("", ".bak", ".new").any { File(file.baseFile.path + it).exists() }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= SessionCipher.MAX_BYTES)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
