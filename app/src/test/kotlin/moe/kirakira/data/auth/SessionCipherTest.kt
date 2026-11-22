package moe.kirakira.data.auth

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.testing.ScriptedApi
import moe.kirakira.testing.savedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCipherTest {
    private val cipher = SessionCipher()
    private val key = newKey()
    private val state = StoredSessions(
        baseUrl = ScriptedApi.BASE_URL,
        accounts = listOf(savedAccount(1), savedAccount(2, token = null)),
        activeUuid = "fixture-1",
    )

    @Test
    fun encrypt_roundTrip_preservesActiveAndExpiredAccountsWithoutPlaintext() {
        val encrypted = cipher.encrypt(state, key)
        val restored = cipher.decrypt(encrypted, key)
        assertEquals(state.baseUrl, restored.baseUrl)
        assertEquals(state.activeUuid, restored.activeUuid)
        assertEquals(2, restored.accounts.size)
        assertEquals("fixture-token-1", restored.accounts.first().token)
        assertNull(restored.accounts.last().token)
        val storedText = encrypted.toString(Charsets.ISO_8859_1)
        for (secret in listOf("fixture-token-1", "user1@example.invalid", "auth.example.invalid")) {
            assertFalse(storedText.contains(secret))
        }
    }

    @Test
    fun encrypt_sameState_usesDifferentNoncesAndCiphertexts() {
        val nonces = (1..128).map {
            cipher.encrypt(state, key).take(SessionCipher.NONCE_BYTES)
        }
        assertEquals(128, nonces.distinct().size)
    }

    @Test
    fun decrypt_modifiedNonceCiphertextOrTag_alwaysRejects() {
        val encrypted = cipher.encrypt(state, key)
        for (index in encrypted.indices) {
            val modified = encrypted.copyOf()
            modified[index] = (modified[index].toInt() xor 1).toByte()
            assertStorageFailure { cipher.decrypt(modified, key) }
        }
    }

    @Test
    fun decrypt_wrongKey_rejectsAndDoesNotExposeDetails() {
        val encrypted = cipher.encrypt(state, key)
        assertStorageFailure { cipher.decrypt(encrypted, newKey()) }
    }

    @Test
    fun decrypt_truncatedOrExtendedEnvelope_rejects() {
        val encrypted = cipher.encrypt(state, key)
        for (length in 0 until encrypted.size) {
            assertStorageFailure { cipher.decrypt(encrypted.copyOf(length), key) }
        }
        assertStorageFailure { cipher.decrypt(encrypted + byteArrayOf(1), key) }
        assertStorageFailure { cipher.decrypt(ByteArray(SessionCipher.MAX_BYTES + 1), key) }
    }

    @Test
    fun decrypt_existingV1Envelope_remainsCompatible() {
        val fixture = """{"version":1,"baseUrl":"https://auth.example.invalid/","accounts":[{"profile":{"uuid":"fixture-1","uid":1,"email":"user1@example.invalid"},"token":"fixture-token-1"}],"activeUuid":"fixture-1"}"""
        val restored = cipher.decrypt(authenticatedFixture(fixture.toByteArray()), key)
        assertEquals("fixture-1", restored.activeUuid)
        assertEquals("fixture-token-1", restored.accounts.single().token)
    }

    @Test
    fun decrypt_authenticatedButInvalidPayload_rejectsSchemaAndIdentityErrors() {
        val invalid = listOf(
            "not json",
            """{"version":2,"baseUrl":"https://auth.example.invalid/"}""",
            """{"baseUrl":"http://auth.example.invalid/"}""",
            """{"baseUrl":"https://user:password@auth.example.invalid/"}""",
            """{"baseUrl":"https://auth.example.invalid/","activeUuid":"missing"}""",
            """{"baseUrl":"https://auth.example.invalid/","accounts":[{"profile":{"uuid":"bad;cookie=1","uid":1,"email":"user1@example.invalid"}}]}""",
            """{"baseUrl":"https://auth.example.invalid/","accounts":[{"profile":{"uuid":"fixture-1","uid":1,"email":"user1@example.invalid"},"token":"bad;cookie=1"}]}""",
        )
        for (json in invalid) assertStorageFailure { cipher.decrypt(authenticatedFixture(json.toByteArray()), key) }
        assertStorageFailure { cipher.decrypt(authenticatedFixture(byteArrayOf(0xC3.toByte(), 0x28)), key) }
    }

    @Test
    fun encrypt_invalidAccountState_refusesToPersist() {
        val invalid = listOf(
            StoredSessions(baseUrl = ScriptedApi.BASE_URL, accounts = listOf(savedAccount(1), savedAccount(1))),
            StoredSessions(baseUrl = ScriptedApi.BASE_URL, accounts = listOf(savedAccount(1, null)), activeUuid = "fixture-1"),
            StoredSessions(baseUrl = ScriptedApi.BASE_URL, activeUuid = "missing"),
            StoredSessions(baseUrl = ScriptedApi.BASE_URL, accounts = listOf(savedAccount(-1))),
        )
        invalid.forEach { assertStorageFailure { cipher.encrypt(it, key) } }
    }

    @Test
    fun encrypt_oversizedPayload_refusesToPersist() {
        val oversized = StoredSessions(
            baseUrl = ScriptedApi.BASE_URL,
            accounts = listOf(savedAccount(1, "x".repeat(SessionCipher.MAX_BYTES))),
        )
        assertStorageFailure { cipher.encrypt(oversized, key) }
    }

    private fun authenticatedFixture(plaintext: ByteArray): ByteArray {
        val reference = Cipher.getInstance("AES/GCM/NoPadding")
        reference.init(Cipher.ENCRYPT_MODE, key)
        assertEquals(12, reference.iv.size)
        assertEquals(128, reference.parameters.getParameterSpec(GCMParameterSpec::class.java).tLen)
        return reference.iv + reference.doFinal(plaintext)
    }

    private fun assertStorageFailure(action: () -> Unit) {
        val error = assertThrows(ApiException::class.java, action)
        assertEquals(ApiFailure.STORAGE, error.failure)
        assertEquals("STORAGE", error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
