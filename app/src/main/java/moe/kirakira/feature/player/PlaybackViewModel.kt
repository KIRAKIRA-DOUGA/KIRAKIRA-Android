package moe.kirakira.feature.player

import android.content.Context
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import java.io.IOException
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.kirakira.data.content.VideoPart
import moe.kirakira.data.history.HistoryRepository
import okhttp3.OkHttpClient

internal data class VideoQualityOption(val height: Int, val bitrate: Int?)

internal val playbackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)

/** Host-owned playback, paused on restoration; never stores URLs or credentials in SavedState. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackViewModel(
    context: Context,
    private val savedState: SavedStateHandle,
    history: HistoryRepository? = null,
) : ViewModel() {
    private val applicationContext = context.applicationContext

    var videoId by mutableStateOf<Int?>(null)
        private set
    var miniPlayer by mutableStateOf(false)
        private set
    private var pageVideoId: Int? = null
    var bounds by mutableStateOf<Rect?>(null)
        private set

    fun updateBounds(value: Rect) {
        if (bounds != value) bounds = value
    }

    private var sessionRevision: Long? = null
    private val resume = PlaybackResumeController()
    private val historyRecorder = PlaybackHistoryRecorder(history)
    private val initiallyReadyPartIds = mutableSetOf<Int>()

    fun syncSession(revision: Long): Boolean {
        val changed = sessionRevision != null && sessionRevision != revision
        if (changed) clearSession()
        sessionRevision = revision
        return changed
    }

    fun showVideo(id: Int) {
        if (videoId != id) {
            release()
            initiallyReadyPartIds.clear()
            resume.begin(restored = savedState.get<Int>("video_id") == id)
            bounds = null
            parts = emptyList()
            title = ""
            artwork = null
            if (savedState.get<Int>("video_id") != id) {
                selectedPart = 0
                positionMs = 0L
                durationMs = 0L
                speed = 1f
                continuousSpeed = false
                preservesPitch = true
                savedState["part"] = 0
                savedState["position"] = 0L
                savedState["speed"] = speed
                savedState["continuous_speed"] = false
                savedState["preserves_pitch"] = true
                autoplayHandled = false
            }
            failed = false
            historyRecorder.resetPosition(positionMs)
            videoId = id
            savedState["video_id"] = id
        }
        pageVideoId = id
        miniPlayer = false
    }

    fun leaveVideo(allowMiniPlayer: Boolean) {
        if (pageVideoId == null) return
        pageVideoId = null
        bounds = null
        cancelAutoplay()
        miniPlayer = allowMiniPlayer && showPauseIcon && player != null && !failed
        if (!miniPlayer) {
            release()
            initiallyReadyPartIds.clear()
        }
    }

    fun closeMiniPlayer() {
        miniPlayer = false
        bounds = null
        cancelAutoplay()
        release()
        initiallyReadyPartIds.clear()
    }

    fun clearSession() {
        closeMiniPlayer()
        videoId = null
        pageVideoId = null
        parts = emptyList()
        artwork = null
        savedState.remove<Int>("video_id")
    }

    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var selectedPart by mutableIntStateOf(savedState.get<Int>("part") ?: 0)
        private set
    var positionMs by mutableLongStateOf(savedState.get<Long>("position") ?: 0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var bufferedPositionMs by mutableLongStateOf(0L)
        private set
    var playing by mutableStateOf(false)
        private set
    var showPauseIcon by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var initialLoading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var qualityOptions by mutableStateOf<List<VideoQualityOption>>(emptyList())
        private set
    var selectedQualityHeight by mutableStateOf<Int?>(null)
        private set
    var actualVideoHeight by mutableStateOf<Int?>(null)
        private set
    var speed by mutableFloatStateOf(savedState.get<Float>("speed")?.takeIf { it.isFinite() && it in 0.25f..4f } ?: 1f)
        private set
    var continuousSpeed by mutableStateOf(savedState.get<Boolean>("continuous_speed") ?: false)
        private set
    var preservesPitch by mutableStateOf(savedState.get<Boolean>("preserves_pitch") ?: true)
        private set
    private var preferredHeight: Int? = null

    fun setQualityPreference(auto: Boolean, height: Int?) {
        preferredHeight = height?.takeIf { !auto && it > 0 }
        player?.let(::updateQuality)
    }

    private fun updateQuality(current: ExoPlayer) {
        val candidates = current.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) && group.getTrackFormat(it).height > 0 }
                .map { index -> group to index }
        }
        // Display the bitrate of the same representative track that manual selection will use.
        val representatives = candidates
            .sortedWith(compareByDescending<Pair<Tracks.Group, Int>> { it.first.isSelected }
                .thenByDescending { (group, index) -> group.getTrackFormat(index).bitrate })
            .distinctBy { (group, index) -> group.getTrackFormat(index).height }
        qualityOptions = representatives.map { (group, index) ->
            val format = group.getTrackFormat(index)
            VideoQualityOption(format.height, format.bitrate.takeIf { it > 0 })
        }.sortedByDescending { it.height }
        val target = representatives.firstOrNull { (group, index) -> group.getTrackFormat(index).height == preferredHeight }
        selectedQualityHeight = target?.let { (group, index) -> group.getTrackFormat(index).height }
        val parameters = current.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        target?.let { (group, index) -> parameters.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index)) }
        val next = parameters.build()
        if (next != current.trackSelectionParameters) current.trackSelectionParameters = next
    }

    fun changeSpeed(value: Float) {
        if (!value.isFinite()) return
        val bounded = value.coerceIn(0.25f, 4f)
        speed = if (continuousSpeed) round(bounded * 100) / 100
            else playbackSpeeds.minBy { abs(it - bounded) }
        savedState["speed"] = speed
        applySpeed()
    }

    fun changeContinuousSpeed(enabled: Boolean) {
        continuousSpeed = enabled
        savedState["continuous_speed"] = enabled
        changeSpeed(speed)
    }

    fun changePreservesPitch(enabled: Boolean) {
        preservesPitch = enabled
        savedState["preserves_pitch"] = enabled
        applySpeed()
    }

    private fun applySpeed() {
        player?.playbackParameters = PlaybackParameters(speed, if (preservesPitch) 1f else speed)
    }

    private var autoplayHandled = savedState.get<Boolean>("autoplay_handled") == true

    init {
        // A restored route must remain paused, including when it was killed during loading.
        savedState["autoplay_handled"] = true
    }

    fun maybeAutoplay(enabled: Boolean) {
        if (autoplayHandled || parts.getOrNull(selectedPart)?.url == null) return
        autoplayHandled = true
        if (enabled) play()
    }

    fun cancelAutoplay() { autoplayHandled = true }

    private var parts = emptyList<VideoPart>()
    var artwork by mutableStateOf<String?>(null)
        private set
    private var title = ""
    private var mediaSession: MediaSession? = null
    private var progress: Job? = null

    fun setContent(
        title: String,
        parts: List<VideoPart>,
        resumePosition: Long? = null,
        resumeReady: Boolean = true,
        artwork: String? = null,
    ) {
        this.artwork = artwork
        // Follow the part identity across reordering; refreshed URLs alone must not interrupt playback.
        val previousPart = this.parts.getOrNull(selectedPart)
        val matchingIndex = previousPart?.let { previous -> parts.indexOfFirst { it.id == previous.id } } ?: -1
        val nextIndex = if (previousPart != null) {
            matchingIndex.takeIf { it >= 0 } ?: 0
        } else {
            selectedPart.takeIf { it in parts.indices } ?: 0
        }
        val partRemoved = this.parts.isNotEmpty() && (previousPart == null || matchingIndex == -1)
        val restoredPartMissing = this.parts.isEmpty() && parts.isNotEmpty() && selectedPart !in parts.indices
        if (partRemoved || restoredPartMissing) {
            release()
            resume.onSeek()
            cancelAutoplay()
            positionMs = 0L
            durationMs = 0L
            failed = false
            historyRecorder.resetPosition(0L)
            savedState["position"] = 0L
        } else if (selectedPart != nextIndex) {
            // Flush under the old index before its first-part history eligibility changes.
            flushHistory()
            historyRecorder.resetPosition(positionMs)
        }
        this.title = title
        this.parts = parts
        selectedPart = nextIndex
        savedState["part"] = selectedPart
        if (resumeReady && parts.isNotEmpty()) {
            val candidate = resumePosition.takeIf { selectedPart == 0 && player == null && positionMs == 0L }
            resume.resolve(candidate)?.let { position ->
                positionMs = position
                savedState["position"] = position
                historyRecorder.resetPosition(position)
            }
        }
    }

    fun play() {
        autoplayHandled = true
        resume.onPlay()
        val part = parts.getOrNull(selectedPart)
        if (part?.url == null) { failed = true; initialLoading = false; return }
        val current = player
        if (current != null && !failed) {
            if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
            current.play()
            return
        }
        release()
        failed = false
        initialLoading = part.id !in initiallyReadyPartIds
        val resumePosition = positionMs
        val next = ExoPlayer.Builder(applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(publicMediaClient)))
            .build().apply playerSetup@ {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
                setHandleAudioBecomingNoisy(true)
                addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (player !== this@PlaybackViewModel.player) return
                        if (events.contains(Player.EVENT_TRACKS_CHANGED)) updateQuality(player)
                        actualVideoHeight = this@PlaybackViewModel.player?.videoFormat?.height?.takeIf { it > 0 }
                        playing = player.isPlaying
                        // isPlaying becomes false during buffering; the button still means pause.
                        showPauseIcon = player.playWhenReady && player.playbackState != Player.STATE_ENDED &&
                            player.playerError == null
                        buffering = player.playbackState == Player.STATE_BUFFERING
                        if (player.playbackState == Player.STATE_READY) {
                            initiallyReadyPartIds.add(part.id)
                            initialLoading = false
                        }
                        durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
                        if (resume.shouldRestart(durationMs)) {
                            historyRecorder.resetPosition(0L)
                            savedState["position"] = 0L
                            player.seekTo(0)
                        }
                        positionMs = player.currentPosition.coerceAtLeast(0)
                        bufferedPositionMs = player.bufferedPosition.coerceIn(0, durationMs)
                        if ((!player.playWhenReady && events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) ||
                            player.playbackState == Player.STATE_ENDED) flushHistory()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if (this@playerSetup !== this@PlaybackViewModel.player) return
                        failed = true
                        buffering = false
                        initialLoading = false
                        showPauseIcon = false
                        if (miniPlayer) closeMiniPlayer()
                    }
                })
                setMediaItem(MediaItem.Builder().setUri(part.url).setMediaId(part.id.toString())
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(part.title).build()).build())
                seekTo(resumePosition)
            }
        player = next
        applySpeed()
        mediaSession = MediaSession.Builder(applicationContext, next).setId("video-${hashCode()}").build()
        next.prepare()
        next.play()
        historyRecorder.startSampling()
        progress = viewModelScope.launch {
            while (true) {
                actualVideoHeight = next.videoFormat?.height?.takeIf { it > 0 }
                positionMs = next.currentPosition.coerceAtLeast(0)
                bufferedPositionMs = next.bufferedPosition.coerceIn(0, durationMs)
                savedState["position"] = positionMs
                delay(500.milliseconds)
                historyRecorder.sample(historySample())
            }
        }
    }

    fun toggle() {
        val current = player
        if (current?.playWhenReady == true && current.playbackState != Player.STATE_ENDED && !failed) {
            current.pause()
        } else {
            play()
        }
    }
    fun seek(position: Long) {
        resume.onSeek()
        positionMs = position.coerceIn(0, durationMs.coerceAtLeast(0))
        player?.seekTo(positionMs)
        bufferedPositionMs = player?.bufferedPosition?.coerceIn(0, durationMs) ?: 0L
        savedState["position"] = positionMs
    }
    fun selectPart(index: Int) {
        autoplayHandled = true
        if (index == selectedPart || index !in parts.indices) return
        val continuePlaying = player?.playWhenReady == true
        release()
        resume.onSeek()
        selectedPart = index
        positionMs = 0
        historyRecorder.resetPosition(0L)
        durationMs = 0
        failed = false
        savedState["part"] = index
        savedState["position"] = 0L
        if (continuePlaying) play()
    }
    fun pause() { autoplayHandled = true; player?.pause(); flushHistory() }
    fun flushHistory() = historyRecorder.flush(historySample())

    private fun historySample(): PlaybackHistorySample? {
        val current = player ?: return null
        val id = videoId ?: return null
        val revision = sessionRevision ?: return null
        if (selectedPart != 0 || !resume.canRecordHistory) return null
        return PlaybackHistorySample(id, revision, current.currentPosition.coerceAtLeast(0), durationMs)
    }

    fun release() {
        flushHistory()
        player?.let { positionMs = it.currentPosition.coerceAtLeast(0) }
        savedState["position"] = positionMs
        savedState["part"] = selectedPart
        progress?.cancel()
        progress = null
        mediaSession?.release()
        mediaSession = null
        val previous = player
        player = null
        previous?.release()
        qualityOptions = emptyList()
        selectedQualityHeight = null
        actualVideoHeight = null
        bufferedPositionMs = 0
        playing = false
        showPauseIcon = false
        buffering = false
        initialLoading = false
    }
    override fun onCleared() { release() }
}

/** A separate public-media transport: no API cookie jar, headers or authentication interceptors. */
private val publicMediaClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15.seconds).readTimeout(30.seconds)
    .followSslRedirects(false)
    .addNetworkInterceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) throw IOException("Invalid media URL")
        chain.proceed(chain.request())
    }.build()
