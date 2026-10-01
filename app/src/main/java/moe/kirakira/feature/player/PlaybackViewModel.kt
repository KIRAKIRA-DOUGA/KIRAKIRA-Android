package moe.kirakira.feature.player

import android.content.Context
import android.graphics.Rect
import androidx.compose.runtime.getValue
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round
import moe.kirakira.data.content.VideoPart
import okhttp3.OkHttpClient

internal data class VideoQualityOption(val height: Int, val bitrate: Int?)

internal val playbackSpeeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)

/** Host-owned playback, paused on restoration; never stores URLs or credentials in SavedState. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackViewModel(private val context: Context, private val savedState: SavedStateHandle) : ViewModel() {
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

    fun syncSession(revision: Long): Boolean {
        val changed = sessionRevision != null && sessionRevision != revision
        sessionRevision = revision
        if (changed) clearSession()
        return changed
    }

    fun showVideo(id: Int) {
        if (videoId != id) {
            release()
            bounds = null
            parts = emptyList()
            title = ""
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
        if (!miniPlayer) release()
    }

    fun closeMiniPlayer() {
        miniPlayer = false
        bounds = null
        cancelAutoplay()
        release()
    }

    fun clearSession() {
        closeMiniPlayer()
        videoId = null
        pageVideoId = null
        parts = emptyList()
        savedState.remove<Int>("video_id")
    }

    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var selectedPart by mutableStateOf(savedState.get<Int>("part") ?: 0)
        private set
    var positionMs by mutableStateOf(savedState.get<Long>("position") ?: 0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var bufferedPositionMs by mutableStateOf(0L)
        private set
    var playing by mutableStateOf(false)
        private set
    var showPauseIcon by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var qualityOptions by mutableStateOf<List<VideoQualityOption>>(emptyList())
        private set
    var selectedQualityHeight by mutableStateOf<Int?>(null)
        private set
    var actualVideoHeight by mutableStateOf<Int?>(null)
        private set
    var speed by mutableStateOf(savedState.get<Float>("speed")?.takeIf { it.isFinite() && it in 0.25f..4f } ?: 1f)
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
    private var title = ""
    private var mediaSession: MediaSession? = null
    private var progress: Job? = null

    fun setContent(title: String, parts: List<VideoPart>) {
        // Refetching the same video can rotate media URLs; keep the current playback uninterrupted.
        if (this.parts.isNotEmpty() && this.parts.getOrNull(selectedPart)?.id != parts.getOrNull(selectedPart)?.id) {
            release()
        }
        this.title = title
        this.parts = parts
        if (selectedPart !in parts.indices) selectedPart = 0
    }

    fun play() {
        autoplayHandled = true
        val part = parts.getOrNull(selectedPart)
        if (part?.url == null) { failed = true; return }
        val current = player
        if (current != null && !failed) {
            if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
            current.play()
            return
        }
        release()
        failed = false
        val resumePosition = positionMs
        val next = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(publicMediaClient)))
            .build().apply {
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
                        durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
                        positionMs = player.currentPosition.coerceAtLeast(0)
                        bufferedPositionMs = player.bufferedPosition.coerceIn(0, durationMs)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        failed = true
                        buffering = false
                        showPauseIcon = false
                        if (miniPlayer) closeMiniPlayer()
                    }
                })
                setMediaItem(MediaItem.Builder().setUri(part.url).setMediaId(part.id.toString())
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(part.title).build()).build())
                seekTo(resumePosition)
                prepare()
            }
        player = next
        applySpeed()
        mediaSession = MediaSession.Builder(context, next).setId("video-${hashCode()}").build()
        next.play()
        progress = viewModelScope.launch {
            while (true) {
                actualVideoHeight = next.videoFormat?.height?.takeIf { it > 0 }
                positionMs = next.currentPosition.coerceAtLeast(0)
                bufferedPositionMs = next.bufferedPosition.coerceIn(0, durationMs)
                savedState["position"] = positionMs
                delay(500)
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
        selectedPart = index
        positionMs = 0
        durationMs = 0
        failed = false
        savedState["part"] = index
        savedState["position"] = 0L
        if (continuePlaying) play()
    }
    fun pause() { autoplayHandled = true; player?.pause() }
    fun release() {
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
    }
    override fun onCleared() { release() }
}

/** A separate public-media transport: no API cookie jar, headers or authentication interceptors. */
private val publicMediaClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
    .followSslRedirects(false)
    .addNetworkInterceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) throw IOException("Invalid media URL")
        chain.proceed(chain.request())
    }.build()
