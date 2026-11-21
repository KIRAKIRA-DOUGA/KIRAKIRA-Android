package moe.kirakira.feature.video

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.testing.AuthFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentListResilienceTest {
    @Test
    fun aFilteredPageDoesNotEndAdjacentLoading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = AuthFixture(dispatcher)
        fixture.repository.initialize()
        val repository = ContentRepository(fixture.transport.client(dispatcher), fixture.repository)
        val loader = CommentListLoader(10, repository) { operation -> backgroundScope.launch { operation() } }

        fixture.transport.respond("""{"success":true,"videoCommentCount":42,"videoCommentList":[
            {"_id":"one","commentIndex":1,"uid":1,"text":"First"},{"_id":"invalid","commentIndex":0,"uid":1,"text":"Bad floor"}]}
        """)
        loader.jump(1)
        runCurrent()
        loader.consumeLocation(checkNotNull(loader.state.value.location).request)
        assertEquals(listOf("one"), loader.state.value.entries.map { it.comment.id })

        fixture.transport.respond("""{"success":true,"videoCommentCount":42,"videoCommentList":[null,{}]}""")
        loader.adjacent(before = false)
        runCurrent()
        assertTrue(checkNotNull(loader.state.value.pages[2]).comments.isEmpty())
        assertFalse(loader.state.value.endReached)
        assertNull(loader.state.value.next.error)

        fixture.transport.respond("""{"success":true,"videoCommentCount":42,"videoCommentList":[
            {"_id":"last","commentIndex":42,"uid":2,"text":"Last"}]}
        """)
        loader.adjacent(before = false)
        runCurrent()
        assertEquals(listOf("one", "last"), loader.state.value.entries.map { it.comment.id })
        assertEquals(listOf("1", "2", "3"), fixture.transport.requests.map { it.url.queryParameter("page") })
    }
}
