package moe.kirakira.feature.player

import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import moe.kirakira.data.history.HistoryRepository

internal data class PlaybackHistorySample(
    val videoId: Int,
    val revision: Long,
    val positionMs: Long,
    val durationMs: Long,
)

/** Sampling policy only; the host-owned repository manages serial, account-bound writes. */
internal class PlaybackHistoryRecorder(private val repository: HistoryRepository?) {
    private var lastPositionMs = 0L
    private var samples = 0

    fun resetPosition(positionMs: Long) {
        lastPositionMs = positionMs
    }

    fun startSampling() {
        samples = 0
    }

    fun sample(value: PlaybackHistorySample?) {
        if (++samples < 10) return
        samples = 0
        record(value, minimumChange = 15.seconds)
    }

    fun flush(value: PlaybackHistorySample?) = record(value, minimumChange = 1.seconds)

    private fun record(value: PlaybackHistorySample?, minimumChange: Duration) {
        if (value == null || abs(value.positionMs - lastPositionMs).milliseconds < minimumChange) return
        lastPositionMs = value.positionMs
        if (value.durationMs > 0) repository?.rememberDuration(value.videoId, value.durationMs, value.revision)
        repository?.enqueue(value.videoId, value.positionMs, value.revision)
    }
}
