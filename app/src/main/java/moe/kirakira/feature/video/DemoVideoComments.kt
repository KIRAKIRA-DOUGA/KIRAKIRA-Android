package moe.kirakira.feature.video

import androidx.annotation.StringRes
import java.time.LocalDateTime
import moe.kirakira.R

internal const val COMMENTS_PER_PAGE = 20

internal enum class CommentSort(@param:StringRes val labelRes: Int) {
    TIME(R.string.video_comments_by_time),
    SCORE(R.string.video_comments_by_score),
}

internal data class DemoVideoComment(
    val floor: Int,
    val author: Int,
    val createdAt: LocalDateTime,
    val score: Int,
    @param:StringRes val bodyRes: Int,
)

// 固定楼层作为身份；排序后的列表位置仅用于计算页码。
internal val demoVideoComments = List(120) { index ->
    DemoVideoComment(
        floor = index + 1,
        author = index % 8 + 1,
        createdAt = LocalDateTime.of(2026, 9, 25, 12, 0).plusMinutes(index.toLong() / 2),
        score = (index * 17 + 5) % 43 - 3,
        bodyRes = when (index % 5) {
            0 -> R.string.video_comment_one
            1 -> R.string.video_comment_two
            2 -> R.string.video_comment_three
            3 -> R.string.video_comment_four
            else -> R.string.video_comment_long
        },
    )
}
