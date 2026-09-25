package moe.kirakira.feature.video

import androidx.annotation.StringRes
import moe.kirakira.R

internal data class DemoDanmakuEntry(
    val id: Int,
    val seconds: Int,
    @param:StringRes val bodyRes: Int,
)

internal val demoVideoDanmaku = listOf(
    DemoDanmakuEntry(1, 0, R.string.video_danmaku_one),
    DemoDanmakuEntry(2, 97, R.string.video_danmaku_two),
    DemoDanmakuEntry(3, 123, R.string.video_danmaku_three),
    DemoDanmakuEntry(4, 62, R.string.video_danmaku_long),
    DemoDanmakuEntry(5, 37, R.string.video_danmaku_three),
    DemoDanmakuEntry(6, 29, R.string.video_danmaku_three),
)
