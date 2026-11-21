package moe.kirakira.data.history

import moe.kirakira.data.content.VideoSummary

internal data class HistoryEntry(
    val video: VideoSummary,
    val updatedAt: Long,
    val positionMs: Long?,
) {
    val progress: Float?
        get() = video.durationMs?.takeIf { it > 0 }?.let { duration ->
            positionMs?.let { (it.toDouble() / duration).coerceIn(0.0, 1.0).toFloat() }
        }

    fun resumePosition(): Long? = positionMs?.takeIf { position ->
        position >= 3_000 && (video.durationMs == null || position < video.durationMs - 10_000)
    }
}
