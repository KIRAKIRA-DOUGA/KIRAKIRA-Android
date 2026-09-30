package moe.kirakira.testing

import kotlinx.coroutines.CoroutineDispatcher
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.data.auth.AuthApi
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.auth.SessionPersistence
import moe.kirakira.data.auth.StoredAccount
import moe.kirakira.data.auth.StoredSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

internal class MemorySessionStore(
    var saved: StoredSessions = StoredSessions(baseUrl = ScriptedApi.BASE_URL),
) : SessionPersistence {
    var readFailure: ApiFailure? = null
    var writeFailure: ApiFailure? = null
    var writes = 0
    var resets = 0
    var beforeWrite: suspend () -> Unit = {}

    override suspend fun read(): StoredSessions {
        readFailure?.let { throw ApiException(it) }
        return saved
    }

    override suspend fun write(state: StoredSessions) {
        writes++
        beforeWrite()
        writeFailure?.let { throw ApiException(it) }
        saved = state
    }

    override suspend fun reset() {
        resets++
        saved = StoredSessions(baseUrl = ScriptedApi.BASE_URL)
    }
}

internal class AuthFixture(dispatcher: CoroutineDispatcher) {
    val transport = ScriptedApi()
    val api = AuthApi(transport.client(dispatcher))
    val store = MemorySessionStore()
    var timeMillis = 0L
    val repository = AuthRepository(api, store, ScriptedApi.BASE_URL, { timeMillis }, dispatcher)
}

internal suspend fun assertApiFailure(expected: ApiFailure, action: suspend () -> Unit) {
    try {
        action()
        fail("Expected $expected")
    } catch (error: ApiException) {
        assertEquals(expected, error.failure)
        assertEquals(expected.name, error.message)
    }
}

internal fun savedAccount(id: Long, token: String? = "fixture-token-$id") = StoredAccount(
    AccountProfile(uuid = "fixture-$id", uid = id, email = "user$id@example.invalid"),
    token,
)

internal fun loginResponse(id: Long = 1): String =
    """{"success":true,"UUID":"fixture-$id","uid":$id,"token":"fixture-token-$id"}"""

internal fun profileResponse(id: Long = 1): String =
    """{"success":true,"result":{"uuid":"fixture-$id","uid":$id,"username":"user$id","userNickname":"Local $id"}}"""
