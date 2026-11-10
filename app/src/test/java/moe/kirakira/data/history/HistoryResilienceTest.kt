package moe.kirakira.data.history

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import moe.kirakira.data.auth.StoredSessions
import moe.kirakira.testing.AuthFixture
import moe.kirakira.testing.ScriptedApi
import moe.kirakira.testing.savedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryResilienceTest {
    @Test
    fun invalidHistoryRowsDoNotDiscardValidEntriesOrBlockAnEmptyRefresh() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = AuthFixture(dispatcher)
        val account = savedAccount(1)
        fixture.store.saved = StoredSessions(
            baseUrl = ScriptedApi.BASE_URL,
            accounts = listOf(account),
            activeUuid = account.profile.uuid,
        )
        fixture.repository.initialize()
        val repository = HistoryRepository(fixture.transport.client(dispatcher), fixture.repository, backgroundScope)
        val revision = repository.session.value.revision

        fixture.transport.respond("""{"success":true,"result":[
            {"category":"video","videoId":5,"title":"  ","lastUpdateDateTime":1},
            {"category":"video","videoId":6,"lastUpdateDateTime":-1},
            {"category":"video","videoId":7,"anchor":"1.5","lastUpdateDateTime":2},
            {"category":"video","videoId":8},null]}
        """)
        repository.refresh(revision)
        val entries = checkNotNull(repository.history.value.entries)
        assertEquals(listOf(7, 5), entries.map { it.video.id })
        assertEquals(listOf("", "  "), entries.map { it.video.title })
        assertEquals(1_500L, entries.first().positionMs)

        fixture.transport.respond("""{"success":true,"result":[{},null]}""")
        repository.refresh(revision)
        assertEquals(emptyList<HistoryEntry>(), repository.history.value.entries)
        assertNull(repository.history.value.error)
    }
}
