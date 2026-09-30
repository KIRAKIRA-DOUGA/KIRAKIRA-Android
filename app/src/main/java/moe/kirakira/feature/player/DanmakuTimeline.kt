package moe.kirakira.feature.player

import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToLong
import kotlinx.coroutines.yield
import moe.kirakira.data.content.DanmakuEntry
import moe.kirakira.data.content.DanmakuMode
import moe.kirakira.feature.settings.DanmakuSettings

private const val FIXED_DURATION_MS = 4_000L

internal data class DanmakuFlight(
    val entry: DanmakuEntry,
    val startMs: Long,
    val durationMs: Long,
    val width: Float,
    val height: Float,
    val top: Float,
) {
    val endMs: Long get() = startMs + durationMs

    fun leftAt(timeMs: Long, viewportWidth: Float): Float {
        val progress = (timeMs - startMs).toDouble() / durationMs
        return when (entry.style.mode) {
            DanmakuMode.RTL -> (viewportWidth - (viewportWidth + width) * progress).toFloat()
            DanmakuMode.LTR -> (-width + (viewportWidth + width) * progress).toFloat()
            DanmakuMode.TOP, DanmakuMode.BOTTOM -> (viewportWidth - width) / 2f
        }
    }
}

/** Immutable, time-indexed layout. Seeking never replays earlier frames or queues missed comments. */
internal class DanmakuTimeline(private val flights: List<DanmakuFlight>) {
    private val maxDurationMs = flights.maxOfOrNull { it.durationMs } ?: 0L

    fun visibleAt(timeMs: Long): List<DanmakuFlight> {
        val first = firstAfter(timeMs - maxDurationMs)
        val end = firstAfter(timeMs)
        return (first until end).mapNotNull { index -> flights[index].takeIf { timeMs < it.endMs } }
    }

    fun nextStartAfter(timeMs: Long): Long? = flights.getOrNull(firstAfter(timeMs))?.startMs

    private fun firstAfter(timeMs: Long): Int {
        var low = 0
        var high = flights.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (flights[middle].startMs <= timeMs) low = middle + 1 else high = middle
        }
        return low
    }
}

/** The measurer stays on its owner thread; yield between batches so large pools remain cancellable. */
internal suspend fun buildDanmakuTimeline(
    entries: List<DanmakuEntry>,
    settings: DanmakuSettings,
    width: Float,
    height: Float,
    horizontalGap: Float,
    verticalGap: Float,
    baseScrollSpeedPxPerSecond: Float,
    measure: (DanmakuEntry) -> IntSize,
): DanmakuTimeline {
    if (height <= verticalGap) return DanmakuTimeline(emptyList())
    val active = mutableListOf<DanmakuFlight>()
    val result = ArrayList<DanmakuFlight>()
    // Match Cerasus: lifetime depends on viewport width, while travel includes the text width too.
    val scrollDurationMs = (width.toDouble() / baseScrollSpeedPxPerSecond * 1000 / (settings.speedTenths / 10.0))
        .roundToLong().coerceAtLeast(1L)
    val ordered = entries.sortedBy { it.timeSeconds }
    ordered.forEachIndexed { index, entry ->
        if (index % 32 == 0) yield()
        if (!settings.shows(entry.style.mode)) return@forEachIndexed
        if (!entry.timeSeconds.isFinite() || entry.timeSeconds < 0 || entry.text.isBlank()) return@forEachIndexed
        val start = (entry.timeSeconds * 1000).toLong()
        val mode = entry.style.mode
        val moving = mode == DanmakuMode.RTL || mode == DanmakuMode.LTR
        val duration = if (moving) scrollDurationMs else FIXED_DURATION_MS
        if (start > Long.MAX_VALUE - duration) return@forEachIndexed
        val size = measure(entry)
        val candidate = DanmakuFlight(entry, start, duration, size.width.toFloat(), size.height.toFloat(), 0f)
        active.removeAll { it.endMs <= start }
        val blocked = active.filterNot { canShareTrack(it, candidate, width, horizontalGap) }
        val top = findVerticalSpace(candidate, blocked, height, verticalGap) ?: return@forEachIndexed
        val flight = candidate.copy(top = top)
        active += flight
        result += flight
    }
    return DanmakuTimeline(result)
}

/** Pack measured text boxes rather than reserving large-font rows or stretching gaps to fill the viewport. */
private fun findVerticalSpace(
    candidate: DanmakuFlight,
    blocked: List<DanmakuFlight>,
    viewportHeight: Float,
    gap: Float,
): Float? {
    val edge = gap / 2f
    val fromBottom = candidate.entry.style.mode == DanmakuMode.BOTTOM
    var top = if (fromBottom) viewportHeight - edge - candidate.height else edge
    val ordered = if (fromBottom) blocked.sortedByDescending { it.top + it.height } else blocked.sortedBy { it.top }
    for (previous in ordered) {
        val above = top + candidate.height + gap <= previous.top
        val below = top >= previous.top + previous.height + gap
        if (!above && !below) {
            top = if (fromBottom) previous.top - gap - candidate.height else previous.top + previous.height + gap
        }
    }
    return top.takeIf { it >= edge && it + candidate.height <= viewportHeight - edge }
}

private fun canShareTrack(previous: DanmakuFlight, next: DanmakuFlight, width: Float, gap: Float): Boolean {
    val mode = next.entry.style.mode
    if (previous.entry.style.mode != mode || mode == DanmakuMode.TOP || mode == DanmakuMode.BOTTOM) return false
    // Relative motion is linear. Separation at both ends of the shared lifetime prevents catch-up.
    fun separated(time: Long): Boolean {
        val oldLeft = previous.leftAt(time, width)
        val newLeft = next.leftAt(time, width)
        return if (mode == DanmakuMode.RTL) oldLeft + previous.width + gap <= newLeft
        else newLeft + next.width + gap <= oldLeft
    }
    return separated(next.startMs) && separated(minOf(previous.endMs, next.endMs))
}
