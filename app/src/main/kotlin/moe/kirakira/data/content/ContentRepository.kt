package moe.kirakira.data.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moe.kirakira.core.color.parseRgbHex
import moe.kirakira.core.image.publicMediaUrl
import moe.kirakira.core.network.ApiClient
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.AuthRepository

/** Every operation captures one account. Images and media never use this API client's credentials. */
internal class ContentRepository(private val api: ApiClient, private val auth: AuthRepository) {
    val session = auth.session

    private suspend fun <T> request(
        revision: Long,
        signedIn: Boolean = false,
        block: suspend (String?, String?) -> T,
    ): T = auth.withRequestSession(revision, signedIn) { snapshot ->
        block(snapshot.account?.cookie(), snapshot.account?.homeCookie())
    }

    suspend fun home(revision: Long): List<VideoSummary> = request(revision) { _, homeCookie ->
        summaries(api.get("video/home", cookie = homeCookie))
    }

    suspend fun videos(uid: Long, revision: Long): List<VideoSummary> = request(revision) { cookie, _ ->
        summaries(api.get("video/user", mapOf("uid" to uid.toString()), cookie))
    }

    suspend fun video(id: Int, revision: Long): VideoDetail = request(revision) { cookie, _ ->
        val response = api.get<VideoResponse>("video", mapOf("videoId" to id.toString()), cookie)
        response.check()
        if (response.isBlocked) throw ApiException(ApiFailure.CONTENT_UNAVAILABLE)
        val video = response.video ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        val value = api.decode<VideoSummaryDto>(video)
        if (value.videoId != id) throw ApiException(ApiFailure.INVALID_RESPONSE)
        val detail = api.decode<VideoDetailFieldsDto>(video)
        val uploader = detail.uploaderInfo?.takeUnless { it == JsonNull || it == JsonPrimitive(false) }
            ?.let { api.decode<ProfileDto>(it) }
        val uid = uploader?.uid ?: value.uploaderId ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        if (uid <= 0) throw ApiException(ApiFailure.INVALID_RESPONSE)
        VideoDetail(
            summary = value.summary(),
            parts = api.decodeRows<PartDto, VideoPart>((detail.videoPart as? JsonArray).orEmpty()) { part, index ->
                val link = (part.link as? JsonPrimitive)?.takeIf { it.isString }?.content
                VideoPart(part.id, part.videoPartTitle.orEmpty(), link?.let(::publicMediaUrl), index)
            }.distinctBy { it.id },
            author = (uploader ?: ProfileDto()).profile(uid).copy(blockedByOther = response.isBlockedByOther),
            description = detail.description.orEmpty(),
            category = detail.videoCategory.orEmpty(),
            upvotes = detail.videoUpvoteCount?.coerceAtLeast(0),
            downvotes = detail.videoDownvoteCount?.coerceAtLeast(0),
            reaction = reaction(detail.userHasUpvoted, detail.userHasDownvoted),
            blocked = response.isBlocked,
            blockedByOther = response.isBlockedByOther,
            tags = api.decodeTags((detail.videoTagList as? JsonArray).orEmpty()),
        )
    }

    suspend fun profile(uid: Long, revision: Long): PublicProfile {
        val details = profileDetails(uid, revision)
        if (details.isBlocked) throw ApiException(ApiFailure.CONTENT_UNAVAILABLE)
        return details.profile ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
    }

    suspend fun profileDetails(uid: Long, revision: Long): ProfileDetails = request(revision) { cookie, _ ->
        val response = api.get<ProfileResponse>("user/info", mapOf("uid" to uid.toString()), cookie)
        response.check()
        val profile = if (response.isBlocked) null else {
            (response.result ?: throw ApiException(ApiFailure.INVALID_RESPONSE)).profile(uid)
                .copy(blockedByOther = response.isBlockedByOther)
        }
        ProfileDetails(profile, response.isBlocked, response.isHidden, response.isBlockedByOther)
    }

    suspend fun tag(id: Long, revision: Long): VideoTag? = request(revision) { _, _ ->
        val response = api.post<TagResponse>("video/tag/get", tagBody(id), cookie = null)
        response.check()
        val tags = response.result ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        val values = api.decode<List<TagDto>>(tags).map { it.domain() }
        if (values.any { it.id != id }) throw ApiException(ApiFailure.INVALID_RESPONSE)
        values.firstOrNull()
    }

    suspend fun searchVideos(keyword: String, revision: Long): List<VideoSummary> = request(revision) { _, _ ->
        val query = keyword.trim()
        if (query.isEmpty()) return@request emptyList()
        summaries(api.get("video/search", mapOf("keyword" to query), cookie = null))
    }

    suspend fun searchTags(name: String, revision: Long): List<VideoTag> = request(revision) { _, _ ->
        val query = name.trim()
        if (query.isEmpty()) return@request emptyList()
        val response = api.get<TagResponse>("video/tag/search", mapOf("tagName" to query), cookie = null)
        response.check()
        val rows = response.result ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        api.decodeTags(rows)
    }

    suspend fun tagVideos(id: Long, revision: Long): List<VideoSummary> = tagVideos(listOf(id), revision)

    suspend fun tagVideos(ids: List<Long>, revision: Long): List<VideoSummary> = request(revision) { _, _ ->
        summaries(api.post("video/search/tag", tagBody(ids), cookie = null))
    }

    private suspend fun summaries(response: VideosDto): List<VideoSummary> {
        response.check()
        if (response.isBlocked) throw ApiException(ApiFailure.CONTENT_UNAVAILABLE)
        val rows = response.videos ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        return api.decodeRows<VideoSummaryDto, VideoSummary>(rows) { video, _ -> video.summary() }.distinctBy { it.id }
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
        val decoded = api.decodeRows<ProfileDto, FollowListUser>(users) { value, _ ->
            val id = value.uid?.takeIf { it > 0 } ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
            val profile = value.profile(id)
            FollowListUser(id, profile.name, profile.username, profile.avatar)
        }
        FollowListPage(decoded, total, users.size)
    }

    suspend fun follow(uid: Long, following: Boolean, revision: Long) = request(revision, true) { cookie, _ ->
        val path = if (following) "feed/following" else "feed/unfollowing"
        val key = if (following) "followingUid" else "unfollowingUid"
        api.post<ResultDto>(path, buildJsonObject { put(key, uid) }.toString(), cookie).check()
    }

    suspend fun vote(id: Int, previous: Reaction, target: Reaction, revision: Long) = request(revision, true) { cookie, _ ->
        // Rosales makes opposite votes exclusive. Read the detail again after success (or uncertain failure).
        submitVote("video", previous, target, buildJsonObject { put("videoId", id) }.toString(), cookie)
    }

    suspend fun comments(id: Int, page: Int, revision: Long): CommentPage = request(revision) { cookie, _ ->
        val response = api.get<CommentsDto>("video/comment", mapOf(
            "videoId" to id.toString(), "page" to page.toString(), "pageSize" to "20",
        ), cookie)
        response.check()
        val rows = response.videoCommentList ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        val comments = api.decodeRows<CommentDto, VideoComment>(rows) { comment, _ -> comment.comment() }
        CommentPage(comments, response.videoCommentCount ?: rows.size, page, rows.size)
    }

    suspend fun commentVote(id: Int, comment: VideoComment, target: Reaction, revision: Long) = request(revision, true) { cookie, _ ->
        val body = buildJsonObject { put("videoId", id); put("id", comment.id) }.toString()
        submitVote("video/comment", comment.reaction, target, body, cookie)
    }

    private suspend fun submitVote(path: String, previous: Reaction, target: Reaction, body: String, cookie: String?) {
        val reaction = if (target == Reaction.NONE) previous else target
        val direction = if (reaction == Reaction.LIKE) "upvote" else "downvote"
        val result = if (target == Reaction.NONE) api.delete<ResultDto>("$path/$direction/cancel", body, cookie)
        else api.post<ResultDto>("$path/$direction", body, cookie)
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
        val rows = response.danmaku ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        api.decodeRows<DanmakuDto, DanmakuEntry>(rows) { it, _ ->
            if (!it.time.isFinite() || it.time < 0 || it.time >= Long.MAX_VALUE / 1000.0 || it.text.isBlank()) {
                return@decodeRows null
            }
            DanmakuEntry(it.time, it.text, it.editDateTime, DanmakuStyle(
                color = it.color?.let(::parseRgbHex) ?: 0xFFFFFF,
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
private data class TagResponse(override val success: Boolean, val result: JsonArray? = null) : ApiResult

@Serializable
private data class VideosDto(
    override val success: Boolean,
    val videos: List<JsonElement>? = null,
    val isBlocked: Boolean = false,
) : ApiResult

@Serializable
private data class VideoResponse(
    override val success: Boolean,
    val video: JsonElement? = null,
    val isBlocked: Boolean = false,
    val isBlockedByOther: Boolean = false,
) : ApiResult

@Serializable
private data class VideoSummaryDto(
    val videoId: Int,
    val title: String? = null,
    val image: String? = null,
    val uploader: String? = null,
    val uploaderNickname: String? = null,
    val uploaderId: Long? = null,
    val watchedCount: Long? = null,
    val duration: Double? = null,
    val uploadDate: Long? = null,
    @Serializable(with = UploaderInfoSerializer::class)
    val uploaderInfo: UploaderSummaryDto? = null,
) {
    fun summary(): VideoSummary {
        if (videoId <= 0) throw ApiException(ApiFailure.INVALID_RESPONSE)
        val author = uploaderNickname?.takeIf { it.isNotBlank() }
            ?: uploaderInfo?.userNickname?.takeIf { it.isNotBlank() }
            ?: uploaderInfo?.username?.takeIf { it.isNotBlank() } ?: uploader.orEmpty()
        return VideoSummary(videoId, title.orEmpty(), image, author,
            uploaderId ?: uploaderInfo?.uid, watchedCount?.coerceAtLeast(0),
            secondsToMilliseconds(duration), uploadDate)
    }
}

@Serializable
private data class VideoDetailFieldsDto(
    val description: String? = null,
    val videoCategory: String? = null,
    val videoTagList: JsonElement? = null,
    val videoPart: JsonElement? = null,
    val uploaderInfo: JsonElement? = null,
    val videoUpvoteCount: Long? = null,
    val videoDownvoteCount: Long? = null,
    val userHasUpvoted: Boolean = false,
    val userHasDownvoted: Boolean = false,
)

@Serializable
private data class PartDto(val id: Int, val videoPartTitle: String? = null, val link: JsonElement? = null)

@Serializable
private data class UploaderSummaryDto(
    val uid: Long? = null,
    val username: String? = null,
    val userNickname: String? = null,
)

/** Rosales tag search returns false when the uploader lookup has no matching user. */
private object UploaderInfoSerializer : JsonTransformingSerializer<UploaderSummaryDto?>(UploaderSummaryDto.serializer().nullable) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        JsonPrimitive(false) -> JsonNull
        else -> element
    }
}

@Serializable
private data class ProfileResponse(
    override val success: Boolean,
    val result: ProfileDto? = null,
    val isHidden: Boolean = false,
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
    val result: List<JsonElement>? = null,
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
    val videoCommentList: List<JsonElement>? = null,
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
private data class DanmakuResponse(override val success: Boolean, val danmaku: List<JsonElement>? = null) : ApiResult
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
