package moe.kirakira.feature.player

/** One cloud resume decision per playback session, followed by validation against the actual first part. */
internal class PlaybackResumeController {
    private sealed interface State {
        data object Pending : State
        data object Handled : State
        data class AwaitingDuration(val positionMs: Long) : State
    }

    private var state: State = State.Handled
    val canRecordHistory: Boolean get() = state !is State.AwaitingDuration

    fun begin(restored: Boolean) {
        state = if (restored) State.Handled else State.Pending
    }

    fun resolve(positionMs: Long?): Long? {
        if (state != State.Pending) return null
        state = positionMs?.let { State.AwaitingDuration(it) } ?: State.Handled
        return positionMs
    }

    fun onPlay() {
        if (state == State.Pending) state = State.Handled
    }

    fun onSeek() {
        state = State.Handled
    }

    /** Returns the validated cloud position once, or zero when playback should restart. */
    fun resolveDuration(durationMs: Long): Long? {
        val pending = state
        if (pending !is State.AwaitingDuration || durationMs <= 0) return null
        state = State.Handled
        return if (pending.positionMs >= durationMs - 10_000) 0L else pending.positionMs
    }
}
