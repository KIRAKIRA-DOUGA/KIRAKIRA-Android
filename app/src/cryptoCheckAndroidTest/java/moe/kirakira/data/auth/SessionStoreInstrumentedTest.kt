package moe.kirakira.data.auth

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.kirakira.R
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Runs only in the network-disabled .cryptocheck application, using disposable files and keys. */
@RunWith(AndroidJUnit4::class)
class SessionStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var alias: String
    private lateinit var file: File
    private lateinit var store: SessionStore
    private val baseUrl = "https://auth.example.invalid/"

    @Before
    fun setUp() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        check(target.packageName == "moe.kirakira.cryptocheck") { "Use -Pkirakira.cryptoCheck=true" }
        check(target.packageManager.checkPermission(Manifest.permission.INTERNET, target.packageName) ==
            PackageManager.PERMISSION_DENIED) { "Crypto checks must not have network permission" }
        val id = UUID.randomUUID().toString()
        alias = "kirakira.crypto.test.$id"
        directory = File(target.noBackupFilesDir, "crypto-test-$id").also { check(it.mkdir()) }
        context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        file = File(directory, "sessions.v1.enc")
        store = SessionStore(context, baseUrl, alias)
        store.read()
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        if (::store.isInitialized) store.reset()
        if (::directory.isInitialized) check(directory.deleteRecursively())
    }

    @Test
    fun write_keystoreKey_isNonExportableAndRestrictedToRandomizedAes256Gcm() = runBlocking {
        store.write(state())
        val key = keyStore().getKey(alias, null) as SecretKey
        assertNull(key.encoded)
        val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertEquals(256, info.keySize)
        assertEquals(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT, info.purposes)
        assertEquals(listOf(KeyProperties.BLOCK_MODE_GCM), info.blockModes.toList())
        assertEquals(listOf(KeyProperties.ENCRYPTION_PADDING_NONE), info.encryptionPaddings.toList())
        assertThrows(InvalidAlgorithmParameterException::class.java) {
            Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, ByteArray(12)))
        }
        if (Build.VERSION.SDK_INT >= 31) println("Crypto check API ${Build.VERSION.SDK_INT}: security level ${info.securityLevel}")
    }

    @Test
    fun read_newStoreInstance_decryptsExistingSessionWithoutPlaintextOnDisk() = runBlocking {
        store.write(state())
        val encrypted = file.readBytes()
        val restored = SessionStore(context, baseUrl, alias).read()
        assertEquals("fixture-1", restored.activeUuid)
        assertEquals("fixture-token-1", restored.accounts.single().token)
        val bytesAsText = encrypted.toString(Charsets.ISO_8859_1)
        assertFalse(bytesAsText.contains("fixture-token-1"))
        assertFalse(bytesAsText.contains("user1@example.invalid"))
        assertTrue(file.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
    }

    @Test
    fun write_repeatedState_usesDistinctPlatformNonces() = runBlocking {
        val nonces = mutableSetOf<List<Byte>>()
        repeat(32) {
            store.write(state())
            nonces += file.readBytes().take(SessionCipher.NONCE_BYTES)
        }
        assertEquals(32, nonces.size)
    }

    @Test
    fun read_tamperedCiphertext_preservesFileAndBlocksOverwriteUntilReset() = runBlocking {
        store.write(state())
        val tampered = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(tampered)
        assertStorageFailure { store.read() }
        assertStorageFailure { store.write(state(2)) }
        assertArrayEquals(tampered, file.readBytes())
        assertTrue(keyStore().containsAlias(alias))
        store.reset()
        assertFalse(file.exists())
        assertFalse(keyStore().containsAlias(alias))
        store.write(state(2))
        assertEquals("fixture-2", store.read().activeUuid)
    }

    @Test
    fun read_missingKey_failsWithoutGeneratingReplacementOrOverwritingCiphertext() = runBlocking {
        store.write(state())
        val encrypted = file.readBytes()
        keyStore().deleteEntry(alias)
        assertStorageFailure { store.read() }
        assertStorageFailure { store.write(state(2)) }
        assertFalse(keyStore().containsAlias(alias))
        assertArrayEquals(encrypted, file.readBytes())
    }

    @Test
    fun write_keyLostAfterSuccessfulRead_refusesToReplaceExistingVault() = runBlocking {
        store.write(state())
        val encrypted = file.readBytes()
        keyStore().deleteEntry(alias)
        assertStorageFailure { store.write(state(2)) }
        assertFalse(keyStore().containsAlias(alias))
        assertArrayEquals(encrypted, file.readBytes())
    }

    @Test
    fun read_backupOnly_recoversCommittedCiphertextInsteadOfReturningEmpty() = runBlocking {
        store.write(state())
        val backup = File(file.path + ".bak")
        assertTrue(file.renameTo(backup))
        assertEquals("fixture-1", SessionStore(context, baseUrl, alias).read().activeUuid)
        assertTrue(file.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun write_failedAtomicWrite_restoresPreviousEncryptedSession() = runBlocking {
        store.write(state())
        val original = file.readBytes()
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        stream.write(byteArrayOf(1, 2, 3))
        atomic.failWrite(stream)
        assertArrayEquals(original, file.readBytes())
        assertEquals("fixture-1", SessionStore(context, baseUrl, alias).read().activeUuid)
    }

    @Test
    fun read_abandonedFirstWrite_requiresExplicitReset() = runBlocking {
        File(file.path + ".new").writeBytes(byteArrayOf(1, 2, 3))
        assertStorageFailure { store.read() }
        assertStorageFailure { store.write(state()) }
        assertFalse(keyStore().containsAlias(alias))
        store.reset()
        assertFalse(File(file.path + ".new").exists())
    }

    @Test
    fun read_oversizedFile_rejectsWithoutReplacingItOrCreatingKey() = runBlocking {
        file.writeBytes(ByteArray(SessionCipher.MAX_BYTES + 1))
        assertStorageFailure { store.read() }
        assertStorageFailure { store.write(state()) }
        assertEquals(SessionCipher.MAX_BYTES.toLong() + 1, file.length())
        assertFalse(keyStore().containsAlias(alias))
    }

    @Test
    fun read_otherEnvironment_returnsNoAccountsAndPreservesOriginalCiphertext() = runBlocking {
        store.write(state())
        val original = file.readBytes()
        val other = SessionStore(context, "https://other.example.invalid/", alias)
        val restored = other.read()
        assertTrue(restored.accounts.isEmpty())
        assertNull(restored.activeUuid)
        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun write_wrongEnvironment_cannotReplaceExistingVault() = runBlocking {
        store.write(state())
        val original = file.readBytes()
        assertStorageFailure { store.write(StoredSessions(baseUrl = "https://other.example.invalid/")) }
        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun write_withoutReadingVault_doesNotCreateFilesOrKeys() = runBlocking {
        val unread = SessionStore(context, baseUrl, alias)
        assertStorageFailure { unread.write(state()) }
        assertFalse(file.exists())
        assertFalse(keyStore().containsAlias(alias))
    }

    @Test
    fun write_concurrentCalls_produceOneCompleteDecryptableVault() = runBlocking {
        coroutineScope {
            (1L..8L).forEach { id -> launch(Dispatchers.IO) { store.write(state(id)) } }
        }
        assertTrue(SessionStore(context, baseUrl, alias).read().accounts.single().profile.uid in 1L..8L)
        assertFalse(File(file.path + ".bak").exists())
        assertFalse(File(file.path + ".new").exists())
    }

    @Test
    fun backupRules_includeOnlyNonSensitiveAppearancePreferences() {
        for (resource in listOf(R.xml.backup_rules, R.xml.data_extraction_rules)) {
            context.resources.getXml(resource).use { xml ->
                var includes = 0
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG && xml.name == "include") {
                        includes++
                        assertEquals("sharedpref", xml.getAttributeValue(null, "domain"))
                        assertEquals("kirakira_settings.xml", xml.getAttributeValue(null, "path"))
                    }
                    xml.next()
                }
                assertEquals(if (resource == R.xml.backup_rules) 1 else 2, includes)
            }
        }
    }

    private fun state(id: Long = 1): StoredSessions = StoredSessions(
        baseUrl = baseUrl,
        accounts = listOf(StoredAccount(AccountProfile("fixture-$id", id, "user$id@example.invalid"), "fixture-token-$id")),
        activeUuid = "fixture-$id",
    )

    private suspend fun assertStorageFailure(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected STORAGE failure")
        } catch (error: ApiException) {
            assertEquals(ApiFailure.STORAGE, error.failure)
            assertEquals("STORAGE", error.message)
            assertNull(error.cause)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
