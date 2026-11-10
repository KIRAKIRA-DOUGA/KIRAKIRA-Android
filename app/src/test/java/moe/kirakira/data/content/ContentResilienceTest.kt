package moe.kirakira.data.content

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.StoredSessions
import moe.kirakira.feature.player.PlaybackResumeController
import moe.kirakira.testing.AuthFixture
import moe.kirakira.testing.ScriptedApi
import moe.kirakira.testing.assertApiFailure
import moe.kirakira.testing.savedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentResilienceTest {
    @Test
    fun videoListsDecodeRowsIndependentlyAndIgnoreDetailFields() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = AuthFixture(dispatcher)
        fixture.repository.initialize()
        val repository = ContentRepository(fixture.transport.client(dispatcher), fixture.repository)
        val revision = repository.session.value.revision
        val reads = listOf<suspend () -> List<VideoSummary>>(
            { repository.home(revision) },
            { repository.videos(3, revision) },
            { repository.searchVideos("test", revision) },
            { repository.tagVideos(3, revision) },
        )

        for (read in reads) {
            fixture.transport.respond("""{
                "success":true,"videos":[null,{"title":"Missing ID"},{"videoId":0},
                {"videoId":10,"title":"  ","videoPart":[{"id":0}],"videoTagList":{}},
                {"videoId":11,"title":null,"uploaderInfo":false},
                {"videoId":12,"uploaderInfo":{"username":"uploader","avatar":{}}},
                {"videoId":10,"title":"Duplicate"}]
            }""")
            val videos = read()
            assertEquals(listOf(10, 11, 12), videos.map { it.id })
            assertEquals(listOf("  ", "", ""), videos.map { it.title })
            assertEquals("uploader", videos.last().author)
        }
    }

    @Test
    fun allInvalidRowsAreEmptyAndEnvelopeFailuresStillSurface() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = AuthFixture(dispatcher)
        fixture.repository.initialize()
        val repository = ContentRepository(fixture.transport.client(dispatcher), fixture.repository)
        val revision = repository.session.value.revision

        fixture.transport.respond("""{"success":true,"videos":[{},null,{"videoId":-1}]}""")
        assertTrue(repository.home(revision).isEmpty())

        val invalidEnvelopes = listOf(
            "{", """{"videos":[]}""", """{"success":true,"videos":{}}""", """{"success":true}""",
        )
        for (body in invalidEnvelopes) {
            fixture.transport.respond(body)
            assertApiFailure(ApiFailure.INVALID_RESPONSE) { repository.home(revision) }
        }
        fixture.transport.respond("""{"success":false,"videos":[{}]}""")
        assertApiFailure(ApiFailure.REJECTED) { repository.home(revision) }
        fixture.transport.respond("""{"success":true,"videos":[]}""", status = 503)
        assertApiFailure(ApiFailure.SERVER) { repository.home(revision) }
    }

    @Test
    fun detailKeepsUsablePartsAndTagsWithoutShiftingCloudResume() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = AuthFixture(dispatcher)
        fixture.repository.initialize()
        val repository = ContentRepository(fixture.transport.client(dispatcher), fixture.repository)
        val revision = repository.session.value.revision
        fixture.transport.respond("""{
            "success":true,"video":{"videoId":10,"uploaderId":3,"description":"Description",
            "videoPart":[{},
                {"id":1,"videoPartTitle":"Kept","link":"https://media.example.invalid/one"},
                {"id":1,"videoPartTitle":"Duplicate","link":"https://media.example.invalid/duplicate"},
                {"id":2,"link":{}},{"id":3},{"id":4,"link":"http://media.example.invalid/insecure"}],
            "videoTagList":[{"tagId":0},{"tagNameList":[]},null,{"tagId":8},{"tagId":8}]}
        }""")

        val detail = repository.video(10, revision)
        assertEquals("Description", detail.description)
        assertEquals("", detail.summary.title)
        assertEquals(listOf(1, 2, 3, 4), detail.parts.map { it.id })
        assertEquals(listOf(1, 3, 4, 5), detail.parts.map { it.sourceIndex })
        assertEquals("Kept", detail.parts.first().title)
        assertEquals(listOf("https://media.example.invalid/one", null, null, null), detail.parts.map { it.url })
        assertEquals(listOf(8L), detail.tags.map { it.id })

        val resume = PlaybackResumeController()
        resume.begin(restored = false)
        assertNull(resume.resolve(detail.parts.first(), 30_000))
        resume.begin(restored = false)
        assertEquals(30_000L, resume.resolve(detail.parts.first().copy(sourceIndex = 0), 30_000))
    }

    @Test
    fun videoAndTagLookupStillRequireTheRequestedIdentity() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = AuthFixture(dispatcher)
        fixture.repository.initialize()
        val repository = ContentRepository(fixture.transport.client(dispatcher), fixture.repository)
        val revision = repository.session.value.revision

        fixture.transport.respond("""{"success":true,"video":{"videoId":11,"uploaderId":3}}""")
        assertApiFailure(ApiFailure.INVALID_RESPONSE) { repository.video(10, revision) }
        fixture.transport.respond("""{"success":true,"video":{"videoId":10,"uploaderId":0}}""")
        assertApiFailure(ApiFailure.INVALID_RESPONSE) { repository.video(10, revision) }
        fixture.transport.respond("""{"success":true,"isBlocked":true}""")
        assertApiFailure(ApiFailure.CONTENT_UNAVAILABLE) { repository.video(10, revision) }
        fixture.transport.respond("""{"success":true,"result":[{"tagId":4}]}""")
        assertApiFailure(ApiFailure.INVALID_RESPONSE) { repository.tag(3, revision) }
        fixture.transport.respond("""{"success":true,"result":{"uid":4}}""")
        assertApiFailure(ApiFailure.INVALID_RESPONSE) { repository.profile(3, revision) }
    }

    @Test
    fun malformedOptionalCollectionsLeaveDetailAvailable() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = AuthFixture(dispatcher)
        fixture.repository.initialize()
        val repository = ContentRepository(fixture.transport.client(dispatcher), fixture.repository)
        fixture.transport.respond("""{"success":true,"video":{
            "videoId":10,"uploaderId":3,"uploaderInfo":null,"description":"Description",
            "videoPart":{},"videoTagList":null}}
        """)
        val detail = repository.video(10, repository.session.value.revision)
        assertEquals(3L, detail.author.uid)
        assertEquals("Description", detail.description)
        assertTrue(detail.parts.isEmpty())
        assertTrue(detail.tags.isEmpty())
    }

    @Test
    fun postingACommentStillRequiresAValidResult() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = AuthFixture(dispatcher)
        val account = savedAccount(1)
        fixture.store.saved = StoredSessions(
            baseUrl = ScriptedApi.BASE_URL,
            accounts = listOf(account),
            activeUuid = account.profile.uuid,
        )
        fixture.repository.initialize()
        val repository = ContentRepository(fixture.transport.client(dispatcher), fixture.repository)
        fixture.transport.respond("""{"success":true,"videoComment":{"_id":"one","commentIndex":0,"uid":1,"text":"Posted"}}""")
        assertApiFailure(ApiFailure.INVALID_RESPONSE) {
            repository.postComment(10, "Posted", repository.session.value.revision)
        }
    }

    @Test
    fun tagCandidatesAndDanmakuRetainValidRows() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fixture = AuthFixture(dispatcher)
        fixture.repository.initialize()
        val repository = ContentRepository(fixture.transport.client(dispatcher), fixture.repository)
        val revision = repository.session.value.revision

        fixture.transport.respond("""{"success":true,"result":[{"tagId":4},null,{}, {"tagId":0}]}""")
        assertEquals(listOf(4L), repository.searchTags("tag", revision).map { it.id })
        fixture.transport.respond("""{"success":true,"danmaku":[
            {"time":2,"text":"Later"},{"time":1},{"time":-1,"text":"Invalid time"},
            {"time":0,"text":" "},null,{"time":1,"text":"Earlier","color":"unknown"}]}
        """)
        assertEquals(listOf("Earlier", "Later"), repository.danmaku(10, revision).map { it.text })
    }
}
