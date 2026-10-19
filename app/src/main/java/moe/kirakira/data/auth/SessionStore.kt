package moe.kirakira.data.auth

import android.content.Context
import androidx.core.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure

/** Account JSON is atomically written to app-private storage and excluded from backup. */
internal class SessionStore(
    context: Context,
    private val baseUrl: String,
    private val keyAlias: String = "kirakira.sessions.v1",
) : SessionPersistence {
    private val file = AtomicFile(File(context.noBackupFilesDir, "sessions.json"))
    private val legacyFile = AtomicFile(File(context.noBackupFilesDir, "sessions.v1.enc"))
    private val codec = SessionCodec()
    private val mutex = Mutex()
    private var writable = false

    override var migrationNotice: SessionNotice? = null
        private set

    override suspend fun read(): StoredSessions = mutex.withLock {
        withContext(Dispatchers.IO) {
            writable = false
            migrationNotice = null
            sessionStorageOperation {
                // Only a committed file or its backup marks migration as complete.
                // Never resurrect legacy sessions after a logout or a failed new-store read.
                if (hasCommittedFiles(file)) {
                    val bytes = readFile(file)
                    val state = try {
                        codec.decode(bytes)
                    } finally {
                        bytes.fill(0)
                    }
                    writable = true
                    return@withContext forCurrentBackend(state)
                }

                // A first write interrupted before rename leaves only .new. It has never committed;
                // discard it and retry migration without touching the original encrypted store.
                val pending = File(file.baseFile.path + ".new")
                if (pending.exists()) check(pending.delete())

                var notice: SessionNotice? = null
                var migrated = false
                val state = if (hasFiles(legacyFile)) {
                    try {
                        val bytes = readFile(legacyFile)
                        val legacy = try {
                            LegacySessionReader(keyAlias).read(bytes)
                        } finally {
                            bytes.fill(0)
                        }
                        migrated = true
                        legacy
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Preserve the old file, but do not let a broken legacy key block new sign-ins.
                        notice = SessionNotice.LEGACY_SIGN_IN_REQUIRED
                        StoredSessions(baseUrl = baseUrl)
                    }
                } else {
                    StoredSessions(baseUrl = baseUrl)
                }
                currentCoroutineContext().ensureActive()
                // Commit an empty store too: it records that migration is complete after a failure.
                writeFile(state)
                writable = true
                migrationNotice = notice
                if (migrated) {
                    try {
                        deleteFiles(legacyFile)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // The new store has committed. Leftover legacy files are never loaded again.
                    }
                }
                forCurrentBackend(state)
            }
        }
    }

    override suspend fun write(state: StoredSessions) = mutex.withLock {
        withContext(Dispatchers.IO) {
            sessionStorageOperation {
                check(writable)
                require(state.baseUrl == baseUrl)
                writeFile(state)
            }
        }
    }

    /** User-confirmed reset; no key-service access, including when migration failed. */
    override suspend fun reset() = mutex.withLock {
        withContext(Dispatchers.IO) {
            writable = false
            migrationNotice = null
            sessionStorageOperation {
                deleteFiles(legacyFile)
                deleteFiles(file)
                // Keep an empty new store so a leftover legacy key can never restore a session.
                writeFile(StoredSessions(baseUrl = baseUrl))
                writable = true
            }
        }
    }

    private fun forCurrentBackend(state: StoredSessions): StoredSessions =
        if (state.baseUrl == baseUrl) state else StoredSessions(baseUrl = baseUrl)

    private fun writeFile(state: StoredSessions) {
        val bytes = codec.encode(state)
        try {
            val output = file.startWrite()
            try {
                output.write(bytes)
                // Surface sync failure before asking AtomicFile to commit.
                output.fd.sync()
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            // finishWrite closes the transaction. A subsequent verification failure must not roll it back.
            file.finishWrite(output)
            // AtomicFile may only log a rename failure. Verify via the same recovery path as startup.
            val committed = readFile(file)
            try {
                check(committed.contentEquals(bytes))
            } finally {
                committed.fill(0)
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun readFile(source: AtomicFile): ByteArray {
        val bytes = source.openRead().use(::readBounded)
        try {
            // If backup recovery failed silently, this is not yet the durable readable state.
            check(!File(source.baseFile.path + ".bak").exists())
            return bytes
        } catch (error: Exception) {
            bytes.fill(0)
            throw error
        }
    }

    private fun deleteFiles(source: AtomicFile) {
        source.delete()
        // Verify deletion of every artifact even when AtomicFile only logs a failure.
        for (suffix in listOf(".bak", ".new")) {
            val remainder = File(source.baseFile.path + suffix)
            if (remainder.exists()) check(remainder.delete())
        }
        check(!hasFiles(source))
    }

    private fun hasFiles(source: AtomicFile): Boolean =
        listOf("", ".bak", ".new").any { File(source.baseFile.path + it).exists() }

    private fun hasCommittedFiles(source: AtomicFile): Boolean =
        listOf("", ".bak").any { File(source.baseFile.path + it).exists() }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > SessionCodec.MAX_BYTES) throw ApiException(ApiFailure.STORAGE_DATA)
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            buffer.fill(0)
        }
    }
}
