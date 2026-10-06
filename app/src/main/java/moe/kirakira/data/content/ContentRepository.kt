package moe.kirakira.data.content

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moe.kirakira.core.image.publicMediaUrl
import moe.kirakira.core.network.ApiClient
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.AuthRepository

/** Every operation captures one account. Images and media never use this API client's credentials. */
internal class ContentRepository(private val api: ApiClient, private val auth: AuthRepository) {
    val session = auth.session

    private suspend fun <T> request(revision: Long, signedIn: Boolean = false, block: suspend (String?, String?) -> T): T {
        val snapshot = auth.requestSession()
        if (snapshot.revision != revision) throw CancellationException("Account changed")
        if (signedIn && snapshot.account == null) throw ApiException(ApiFailure.SESSION_EXPIRED)
        val result = try {
            block(snapshot.account?.cookie(), snapshot.account?.homeCookie())
        } catch (error: ApiException) {
            if (!auth.isCurrent(snapshot)) throw CancellationException("Account changed")
            if (error.failure == ApiFailure.SESSION_EXPIRED) auth.expireRequestSession(snapshot)
            throw error
        }
        currentCoroutineContext().ensureActive()
        if (!auth.isCurrent(snapshot)) throw CancellationException("Account changed")
        return result
    }

    suspend fun home(revision: Long): List<VideoSummary> = request(revision) { _, homeCookie ->
        api.get<VideosDto>("video/home", cookie = homeCookie).summaries()
    }

    suspend fun videos(uid: Long, revision: Long): List<VideoSummary> = request(revision) { cookie, _ ->
        api.get<VideosDto>("video/user", mapOf("uid" to uid.toString()), cookie).summaries()
    }

    suspend fun video(id: Int, revision: Long): VideoDetail = request(revision) { cookie, _ ->
        val response = api.get<VideoResponse>("video", mapOf("videoId" to id.toString()), cookie)
        response.check()
        val value = response.video ?: throw ApiException(if (response.isBlocked) ApiFailure.REJECTED else ApiFailure.INVALID_RESPONSE)
        if (value.videoId != id) throw ApiException(ApiFailure.INVALID_RESPONSE)
        val uid = value.uploaderInfo?.uid ?: value.uploaderId ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        if (uid <= 0) throw ApiException(ApiFailure.INVALID_RESPONSE)
        VideoDetail(
            summary = value.summary(),
            parts = value.videoPart.map { VideoPart(it.id, it.videoPartTitle, publicMediaUrl(it.link)) },
            author = (value.uploaderInfo ?: ProfileDto()).profile(uid).copy(blockedByOther = response.isBlockedByOther),
            description = value.description.orEmpty(),
            category = value.videoCategory.orEmpty(),
            upvotes = value.videoUpvoteCount?.coerceAtLeast(0),
            downvotes = value.videoDownvoteCount?.coerceAtLeast(0),
            reaction = reaction(value.userHasUpvoted, value.userHasDownvoted),
            blocked = response.isBlocked,
            blockedByOther = response.isBlockedByOther,
            tags = value.videoTagList.map { it.domain() }.distinctBy { it.id },
        )
    }

    suspend fun profile(uid: Long, revision: Long): PublicProfile = request(revision) { cookie, _ ->
        val response = api.get<ProfileResponse>("user/info", mapOf("uid" to uid.toString()), cookie)
        response.check()
        if (response.isBlocked) throw ApiException(ApiFailure.REJECTED)
        (response.result ?: throw ApiException(ApiFailure.INVALID_RESPONSE)).profile(uid)
            .copy(blockedByOther = response.isBlockedByOther)
    }

    suspend fun tag(id: Long, revision: Long): VideoTag? = request(revision) { _, _ ->
        val response = api.post<TagResponse>("video/tag/get", tagBody(id), cookie = null)
        response.check()
        val tags = response.result ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        val values = tags.map { it.domain() }
        if (values.any { it.id != id }) throw ApiException(ApiFailure.INVALID_RESPONSE)
        values.firstOrNull()
    }

    suspend fun searchVideos(keyword: String, revision: Long): List<VideoSummary> = request(revision) { _, _ ->
        val query = keyword.trim()
        if (query.isEmpty()) return@request emptyList()
        val response = api.get<SearchVideosDto>("video/search", mapOf("keyword" to query), cookie = null)
        response.summaries()
    }

    suspend fun searchTags(name: String, revision: Long): List<VideoTag> = request(revision) { _, _ ->
        val query = name.trim()
        if (query.isEmpty()) return@request emptyList()
        val response = api.get<TagResponse>("video/tag/search", mapOf("tagName" to query), cookie = null)
        response.check()
        (response.result ?: throw ApiException(ApiFailure.INVALID_RESPONSE)).map { it.domain() }.distinctBy { it.id }
    }

    suspend fun tagVideos(id: Long, revision: Long): List<VideoSummary> = tagVideos(listOf(id), revision)

    suspend fun tagVideos(ids: List<Long>, revision: Long): List<VideoSummary> = request(revision) { _, _ ->
        api.post<SearchVideosDto>("video/search/tag", tagBody(ids), cookie = null).summaries()
    }

    private fun tagBody(id: Long): String = tagBody(listOf(id))

    private fun tagBody(ids: List<Long>): String {
        if (ids.isEmpty() || ids.any { it <= 0 }) throw ApiException(ApiFailure.INVALID_RESPONSE)
        return buildJsonObject { put("tagId", buildJsonArray { ids.distinct().forEach { add(it) } }) }.toString()
    }

    suspend fun stats(uid: Long, revision: Long): FollowStats = request(revision) { cookie, _ ->
        val response = api.get<StatsDto>("feed/stats", mapOf("targetUid" to uid.toString()), cookie)
        response.check()
        FollowStats(response.followingCount, response.followerCount)
    }

    suspend fun followList(
        uid: Long,
        kind: FollowListKind,
        page: Int,
        revision: Long,
    ): FollowListPage = request(revision) { cookie, _ ->
        if (uid <= 0 || page <= 0) throw ApiException(ApiFailure.INVALID_RESPONSE)
        val path = when (kind) {
            FollowListKind.FOLLOWING -> "feed/following/list"
            FollowListKind.FOLLOWERS -> "feed/follower/list"
        }
        val response = api.get<FollowListDto>(path, mapOf(
            "targetUid" to uid.toString(), "page" to page.toString(), "pageSize" to "50",
        ), cookie)
        response.check()
        val total = response.totalCount?.takeIf { it >= 0 } ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        val users = response.result ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        FollowListPage(users.map { value ->
            val id = value.uid?.takeIf { it > 0 } ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
            val profile = value.profile(id)
            FollowListUser(id, profile.name, profile.username, profile.avatar)
        }, total)
    }

    suspend fun follow(uid: Long, following: Boolean, revision: Long) = request(revision, true) { cookie, _ ->
        val path = if (following) "feed/following" else "feed/unfollowing"
        val key = if (following) "followingUid" else "unfollowingUid"
        api.post<ResultDto>(path, buildJsonObject { put(key, uid) }.toString(), cookie).check()
    }

    suspend fun vote(id: Int, previous: Reaction, target: Reaction, revision: Long) = request(revision, true) { cookie, _ ->
        // Rosales makes opposite votes exclusive. Read the detail again after success (or uncertain failure).
        val direction = if ((if (target == Reaction.NONE) previous else target) == Reaction.LIKE) "upvote" else "downvote"
        val body = buildJsonObject { put("videoId", id) }.toString()
        val result = if (target == Reaction.NONE) api.delete<ResultDto>("video/$direction/cancel", body, cookie)
        else api.post<ResultDto>("video/$direction", body, cookie)
        result.check()
    }

    suspend fun comments(id: Int, page: Int, revision: Long): CommentPage = request(revision) { cookie, _ ->
        val response = api.get<CommentsDto>("video/comment", mapOf(
            "videoId" to id.toString(), "page" to page.toString(), "pageSize" to "20",
        ), cookie)
        response.check()
        val comments = response.videoCommentList.map { it.comment() }
        CommentPage(comments, response.videoCommentCount ?: comments.size, page)
    }

    suspend fun commentVote(id: Int, comment: VideoComment, target: Reaction, revision: Long) = request(revision, true) { cookie, _ ->
        val direction = if ((if (target == Reaction.NONE) comment.reaction else target) == Reaction.LIKE) "upvote" else "downvote"
        val body = buildJsonObject { put("videoId", id); put("id", comment.id) }.toString()
        val result = if (target == Reaction.NONE) api.delete<ResultDto>("video/comment/$direction/cancel", body, cookie)
        else api.post<ResultDto>("video/comment/$direction", body, cookie)
        result.check()
    }

    suspend fun postComment(id: Int, text: String, revision: Long): VideoComment = request(revision, true) { cookie, _ ->
        val response = api.post<PostedCommentDto>("video/comment/emit", buildJsonObject {
            put("videoId", id); put("text", text)
        }.toString(), cookie)
        response.check()
        (response.videoComment ?: throw ApiException(ApiFailure.INVALID_RESPONSE)).comment()
    }

    suspend fun danmaku(id: Int, revision: Long): List<DanmakuEntry> = request(revision) { _, _ ->
        // This endpoint's personalized filtering currently expects a bootstrap hint. Do not invent token auth.
        val response = api.get<DanmakuResponse>("video/danmaku", mapOf("videoId" to id.toString()), cookie = null)
        response.check()
        response.danmaku.orEmpty().filter {
            it.time.isFinite() && it.time >= 0 && it.time < Long.MAX_VALUE / 1000.0 && it.text.isNotBlank()
        }.map {
            DanmakuEntry(it.time, it.text, it.editDateTime, DanmakuStyle(
                color = it.color?.takeIf { value -> value.matches(Regex("[0-9a-fA-F]{6}")) }?.toIntOrNull(16) ?: 0xFFFFFF,
                fontSize = DanmakuFontSize.entries.firstOrNull { size -> size.wireValue == it.fontSize } ?: DanmakuFontSize.MEDIUM,
                mode = DanmakuMode.entries.firstOrNull { mode -> mode.wireValue == it.mode } ?: DanmakuMode.RTL,
                enableRainbow = it.enableRainbow ?: false,
            ))
        }.sortedBy { it.timeSeconds }
    }

    suspend fun postDanmaku(id: Int, text: String, time: Double, revision: Long, style: DanmakuStyle = DanmakuStyle()) = request(revision, true) { cookie, _ ->
        api.post<ResultDto>("video/danmaku/emit", buildJsonObject {
            put("videoId", id); put("text", text); put("time", time)
            put("color", style.colorHex); put("fontSize", style.fontSize.wireValue)
            put("mode", style.mode.wireValue); put("enableRainbow", style.enableRainbow)
        }.toString(), cookie).check()
    }
}

private interface ApiResult { val success: Boolean }
private fun ApiResult.check() { if (!success) throw ApiException(ApiFailure.REJECTED) }
private fun reaction(up: Boolean, down: Boolean) = when { up -> Reaction.LIKE; down -> Reaction.DISLIKE; else -> Reaction.NONE }

@Serializable
private data class ResultDto(override val success: Boolean) : ApiResult

@Serializable
private data class TagResponse(override val success: Boolean, val result: List<TagDto>? = null) : ApiResult

@Serializable
private data class SearchVideosDto(
    override val success: Boolean,
    val videos: List<VideoDto>? = null,
) : ApiResult {
    fun summaries(): List<VideoSummary> {
        check()
        return (videos ?: throw ApiException(ApiFailure.INVALID_RESPONSE)).map { it.summary() }.distinctBy { it.id }
    }
}

@Serializable
private data class VideosDto(
    override val success: Boolean,
    val videos: List<VideoDto> = emptyList(),
    val isBlocked: Boolean = false,
) : ApiResult {
    fun summaries(): List<VideoSummary> {
        check()
        if (isBlocked) throw ApiException(ApiFailure.REJECTED)
        return videos.map { it.summary() }.distinctBy { it.id }
    }
}

@Serializable
private data class VideoResponse(
    override val success: Boolean,
    val video: VideoDto? = null,
    val isBlocked: Boolean = false,
    val isBlockedByOther: Boolean = false,
) : ApiResult

@Serializable
private data class VideoDto(
    val videoId: Int,
    val title: String,
    val image: String? = null,
    val uploader: String? = null,
    val uploaderNickname: String? = null,
    val uploaderId: Long? = null,
    val watchedCount: Long? = null,
    val duration: Double? = null,
    val uploadDate: Long? = null,
    val description: String? = null,
    val videoCategory: String? = null,
    val videoTagList: List<TagDto> = emptyList(),
    val videoPart: List<PartDto> = emptyList(),
    val uploaderInfo: ProfileDto? = null,
    val videoUpvoteCount: Long? = null,
    val videoDownvoteCount: Long? = null,
    val userHasUpvoted: Boolean = false,
    val userHasDownvoted: Boolean = false,
) {
    fun summary(): VideoSummary {
        if (videoId <= 0 || title.isBlank()) throw ApiException(ApiFailure.INVALID_RESPONSE)
        val author = uploaderNickname?.takeIf { it.isNotBlank() }
            ?: uploaderInfo?.userNickname?.takeIf { it.isNotBlank() }
            ?: uploaderInfo?.username?.takeIf { it.isNotBlank() } ?: uploader.orEmpty()
        return VideoSummary(videoId, title, image, author,
            uploaderId ?: uploaderInfo?.uid, watchedCount?.coerceAtLeast(0),
            secondsToMilliseconds(duration), uploadDate)
    }
}

@Serializable
private data class PartDto(val id: Int, val videoPartTitle: String, val link: String)

@Serializable
private data class ProfileResponse(
    override val success: Boolean,
    val result: ProfileDto? = null,
    val isBlocked: Boolean = false,
    val isBlockedByOther: Boolean = false,
) : ApiResult

@Serializable
private data class ProfileDto(
    val uid: Long? = null,
    val username: String? = null,
    val userNickname: String? = null,
    val avatar: String? = null,
    val userBannerImage: String? = null,
    val signature: String? = null,
    val isFollowing: Boolean = false,
    val isSelf: Boolean = false,
) {
    fun profile(id: Long): PublicProfile {
        if (id <= 0 || (uid != null && uid != id)) throw ApiException(ApiFailure.INVALID_RESPONSE)
        return PublicProfile(
            id, userNickname?.takeIf { it.isNotBlank() } ?: username.orEmpty(),
            username.orEmpty(), avatar, userBannerImage, signature.orEmpty(), isFollowing, isSelf,
        )
    }
}

@Serializable
private data class FollowListDto(
    override val success: Boolean,
    val totalCount: Int? = null,
    val result: List<ProfileDto>? = null,
) : ApiResult

@Serializable
private data class StatsDto(
    override val success: Boolean,
    val followingCount: Int? = null,
    val followerCount: Int? = null,
) : ApiResult

@Serializable
private data class CommentsDto(
    override val success: Boolean,
    val videoCommentCount: Int? = null,
    val videoCommentList: List<CommentDto> = emptyList(),
) : ApiResult

@Serializable
private data class PostedCommentDto(override val success: Boolean, val videoComment: CommentDto? = null) : ApiResult

@Serializable
private data class CommentDto(
    @SerialName("_id") val id: String,
    val commentIndex: Int,
    val uid: Long,
    val userInfo: ProfileDto? = null,
    val text: String,
    val emitTime: Long? = null,
    val upvoteCount: Long = 0,
    val downvoteCount: Long = 0,
    val isUpvote: Boolean = false,
    val isDownvote: Boolean = false,
    val isBlockedByOther: Boolean = false,
) {
    fun comment(): VideoComment {
        if (id.isBlank() || uid <= 0 || commentIndex <= 0) throw ApiException(ApiFailure.INVALID_RESPONSE)
        return VideoComment(id, commentIndex, (userInfo ?: ProfileDto()).profile(uid), text, emitTime,
            upvoteCount.coerceAtLeast(0), downvoteCount.coerceAtLeast(0), reaction(isUpvote, isDownvote), isBlockedByOther)
    }
}

@Serializable
private data class DanmakuResponse(override val success: Boolean, val danmaku: List<DanmakuDto>? = null) : ApiResult
@Serializable
private data class DanmakuDto(
    val time: Double,
    val text: String,
    val editDateTime: Long? = null,
    val color: String? = null,
    val fontSize: String? = null,
    val mode: String? = null,
    val enableRainbow: Boolean? = null,
)
