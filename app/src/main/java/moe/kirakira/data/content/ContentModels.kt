package moe.kirakira.data.content

internal enum class Reaction { NONE, LIKE, DISLIKE }

internal data class VideoSummary(
    val id: Int,
    val title: String,
    val image: String? = null,
    val author: String = "",
    val authorUid: Long? = null,
    val views: Long? = null,
    val durationMs: Long? = null,
    val uploadedAt: Long? = null,
)

internal data class PublicProfile(
    val uid: Long,
    val name: String,
    val username: String = "",
    val avatar: String? = null,
    val banner: String? = null,
    val signature: String = "",
    val following: Boolean = false,
    val isSelf: Boolean = false,
    val blocked: Boolean = false,
    val blockedByOther: Boolean = false,
)

internal data class FollowStats(val following: Int? = null, val followers: Int? = null)
internal data class VideoPart(val id: Int, val title: String, val url: String?)
internal data class VideoDetail(
    val summary: VideoSummary,
    val parts: List<VideoPart>,
    val author: PublicProfile,
    val description: String,
    val category: String,
    val upvotes: Long?,
    val downvotes: Long?,
    val reaction: Reaction,
    val blocked: Boolean = false,
    val blockedByOther: Boolean = false,
)

internal data class VideoComment(
    val id: String,
    val floor: Int,
    val author: PublicProfile,
    val text: String,
    val createdAt: Long?,
    val upvotes: Long,
    val downvotes: Long,
    val reaction: Reaction,
    val blockedByOther: Boolean = false,
) {
    val score: Long get() = upvotes - downvotes
}

internal data class CommentPage(val comments: List<VideoComment>, val total: Int, val page: Int) {
    val pages: Int get() = ((total + 19) / 20).coerceAtLeast(1)
}

internal data class DanmakuEntry(
    val timeSeconds: Double,
    val text: String,
    val editedAt: Long?,
    val style: DanmakuStyle = DanmakuStyle(),
)
