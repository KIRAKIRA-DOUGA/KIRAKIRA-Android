package moe.kirakira.feature.follow

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.testing.AuthFixture
import moe.kirakira.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class FollowListResilienceTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun aFilteredPageCountsTowardTheTotalAndTheNextPageCanRetry() = runTest(main.dispatcher) {
        val fixture = AuthFixture(main.dispatcher)
        fixture.repository.initialize()
        val repository = ContentRepository(fixture.transport.client(main.dispatcher), fixture.repository)
        fixture.transport.respond("""{"success":true,"totalCount":3,"result":[null,{"uid":0}]}""")
        val model = FollowListViewModel(1, FollowListKind.FOLLOWERS, repository)
        try {
            runCurrent()
            assertTrue(checkNotNull(model.state.value.content.data).isEmpty())
            assertTrue(model.state.value.hasMore)

            fixture.transport.respond("{}", status = 503)
            model.loadMore()
            runCurrent()
            assertEquals(ApiFailure.SERVER, model.state.value.moreError)
            assertEquals(1, model.state.value.loadedPage)

            fixture.transport.respond("""{"success":true,"totalCount":3,"result":[{"uid":2,"username":"user2"}]}""")
            model.loadMore(retry = true)
            runCurrent()
            assertEquals(listOf(2L), model.state.value.content.data?.map { it.uid })
            assertFalse(model.state.value.hasMore)
            assertNull(model.state.value.moreError)
            assertEquals(listOf("1", "2", "2"), fixture.transport.requests.map { it.url.queryParameter("page") })
        } finally {
            model.viewModelScope.cancel()
        }
    }
}
