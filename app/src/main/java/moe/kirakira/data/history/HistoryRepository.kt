package moe.kirakira.data.history

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moe.kirakira.core.network.ApiClient
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.auth.SessionOperationType
import moe.kirakira.data.auth.SessionState
import moe.kirakira.data.auth.StoredAccount
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.data.content.secondsToMilliseconds

internal data class HistorySnapshot(
    val revision: Long,
    val entries: List<HistoryEntry>? = null,
    val loading: Boolean = true,
    val error: ApiFailure? = null,
)

/** Main-thread, host-owned memory cache and serial writes, always bound to one account revision. */
internal class HistoryRepository(
    private val api: ApiClient,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    val session = auth.session
    private val _history = MutableStateFlow(HistorySnapshot(session.value.revision))
    val history = _history.asStateFlow()
    private var read: Deferred<Unit>? = null
    private var writer: Job? = null
    private val pending = linkedMapOf<Int, Long>()
    private val videos = mutableMapOf<Int, VideoSummary>()
    private val committed = mutableMapOf<Int, HistoryEntry>()

    init {
        scope.launch { session.collect { syncSession(it) } }
    }

    private fun syncSession(value: SessionState) {
        if (_history.value.revision == value.revision) return
        read?.cancel()
        read = null
        writer?.cancel()
        writer = null
        pending.clear()
        videos.clear()
        committed.clear()
        _history.value = HistorySnapshot(value.revision)
    }

    private fun requireRevision(revision: Long) {
        syncSession(session.value)
        if (session.value.revision != revision || !session.value.readyForHistory) {
            throw CancellationException("Account changed")
        }
    }

    suspend fun loadIfNeeded(revision: Long) {
        requireRevision(revision)
        if (_history.value.entries != null) return
        fetch(revision)
    }

    suspend fun refresh(revision: Long) = fetch(revision)

    private suspend fun fetch(revision: Long) {
        requireRevision(revision)
        if (session.value.activeUuid == null) return
        val operation = read?.takeIf { it.isActive } ?: scope.async {
            _history.value = _history.value.copy(loading = true, error = null)
            try {
                awaitPending(revision)
                val writesAtStart = committed.toMap()
                val entries = request(revision) { account ->
                    val response = api.get<HistoryResponse>("history/filter", cookie = account.cookie())
                    if (!response.success) throw ApiException(ApiFailure.REJECTED)
                    (response.result ?: throw ApiException(ApiFailure.INVALID_RESPONSE)).map { it.domain() }
                }
                requireRevision(revision)
                val merged = entries.associateBy { it.video.id }.toMutableMap()
                committed.forEach { (id, entry) ->
                    if (writesAtStart[id] != entry) merged[id] = entry
                }
                committed.entries.removeAll { writesAtStart[it.key] == it.value }
                _history.value = HistorySnapshot(revision, merged.values.sortedByDescending { it.updatedAt }, loading = false)
            } catch (error: ApiException) {
                if (session.value.revision == revision) {
                    _history.value = _history.value.copy(loading = false, error = error.failure)
                }
                throw error
            }
        }.also { read = it }
        operation.await()
        requireRevision(revision)
    }

    fun rememberVideo(video: VideoSummary, revision: Long) {
        if (session.value.revision == revision && _history.value.revision == revision) {
            val duration = videos[video.id]?.durationMs
                ?: _history.value.entries?.firstOrNull { it.video.id == video.id }?.video?.durationMs
            videos[video.id] = video.copy(durationMs = duration)
        }
    }

    fun rememberDuration(videoId: Int, durationMs: Long, revision: Long) {
        if (session.value.revision == revision && _history.value.revision == revision) {
            videos[videoId]?.let { videos[videoId] = it.copy(durationMs = durationMs) }
        }
    }

    fun resumePosition(videoId: Int, revision: Long): Long? {
        if (_history.value.revision != revision || session.value.revision != revision) return null
        return _history.value.entries?.firstOrNull { it.video.id == videoId }?.resumePosition()
    }

    fun enqueue(videoId: Int, positionMs: Long, revision: Long) {
        syncSession(session.value)
        if (session.value.revision != revision || !session.value.readyForHistory ||
            session.value.activeUuid == null || videoId <= 0 || positionMs < 0) return
        pending[videoId] = positionMs / 1000 * 1000
        if (writer?.isActive == true) return
        writer = scope.launch {
            while (pending.isNotEmpty()) {
                val (id, position) = pending.entries.first().let { it.key to it.value }
                pending.remove(id)
                try {
                    request(revision) { account ->
                        val body = buildJsonObject {
                            put("uuid", account.profile.uuid)
                            put("category", "video")
                            put("id", id.toString())
                            put("anchor", (position / 1000).toString())
                        }.toString()
                        if (!api.post<MergeResponse>("history/merge", body, account.cookie()).success) {
                            throw ApiException(ApiFailure.REJECTED)
                        }
                    }
                    requireRevision(revision)
                    val current = _history.value
                    val video = videos[id] ?: current.entries?.firstOrNull { it.video.id == id }?.video
                    if (video != null) {
                        val entry = HistoryEntry(video, System.currentTimeMillis(), position)
                        committed[id] = entry
                        // A write must not turn an unrequested full history into a partial, loaded list.
                        if (current.entries != null) _history.value = current.copy(
                            entries = (current.entries.filterNot { it.video.id == id } + entry).sortedByDescending { it.updatedAt },
                        )
                    }
                } catch (_: ApiException) {
                    // No replay of failed writes; a later playback sample is a new update.
                }
            }
        }
    }

    suspend fun awaitPending(revision: Long) {
        requireRevision(revision)
        writer?.join()
        requireRevision(revision)
    }

    private suspend fun <T> request(revision: Long, block: suspend (StoredAccount) -> T): T {
        requireRevision(revision)
        val snapshot = auth.requestSession()
        if (snapshot.revision != revision) throw CancellationException("Account changed")
        val account = snapshot.account ?: throw ApiException(ApiFailure.SESSION_EXPIRED)
        val result = try {
            block(account)
        } catch (error: ApiException) {
            if (!auth.isCurrent(snapshot)) throw CancellationException("Account changed")
            if (error.failure == ApiFailure.SESSION_EXPIRED) auth.expireRequestSession(snapshot)
            throw error
        }
        currentCoroutineContext().ensureActive()
        if (!auth.isCurrent(snapshot)) throw CancellationException("Account changed")
        return result
    }
}

private val SessionState.readyForHistory: Boolean
    get() = !isLoading && operation?.type != SessionOperationType.INITIALIZE

@Serializable
private data class HistoryResponse(val success: Boolean, val result: List<HistoryDto>? = null)

@Serializable
private data class MergeResponse(val success: Boolean)

@Serializable
private data class HistoryDto(
    val category: String,
    val videoId: Int,
    val title: String,
    val image: String? = null,
    val uploader: String? = null,
    val uploaderId: Long? = null,
    val duration: Double? = null,
    val anchor: String? = null,
    val lastUpdateDateTime: Long,
) {
    fun domain(): HistoryEntry {
        if (category != "video" || videoId <= 0 || title.isBlank() || lastUpdateDateTime < 0) {
            throw ApiException(ApiFailure.INVALID_RESPONSE)
        }
        return HistoryEntry(
            video = VideoSummary(id = videoId, title = title, image = image, author = uploader.orEmpty(),
                authorUid = uploaderId?.takeIf { it > 0 }, durationMs = secondsToMilliseconds(duration)),
            updatedAt = lastUpdateDateTime,
            positionMs = secondsToMilliseconds(anchor?.toDoubleOrNull()),
        )
    }
}
