package moe.kirakira.feature.player

import android.content.Context
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
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.kirakira.data.content.VideoPart
import okhttp3.OkHttpClient

/** Route-owned playback, paused on restoration; never stores URLs or credentials in SavedState. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackViewModel(private val context: Context, private val savedState: SavedStateHandle) : ViewModel() {
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
    var buffering by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
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
        if (this.parts.isNotEmpty() && this.parts != parts) release()
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
                        playing = player.isPlaying
                        buffering = player.playbackState == Player.STATE_BUFFERING
                        durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
                        positionMs = player.currentPosition.coerceAtLeast(0)
                        bufferedPositionMs = player.bufferedPosition.coerceIn(0, durationMs)
                    }
                    override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
                })
                setMediaItem(MediaItem.Builder().setUri(part.url).setMediaId(part.id.toString())
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(part.title).build()).build())
                seekTo(resumePosition)
                prepare()
            }
        player = next
        mediaSession = MediaSession.Builder(context, next).setId("video-${hashCode()}").build()
        next.play()
        progress = viewModelScope.launch {
            while (true) {
                positionMs = next.currentPosition.coerceAtLeast(0)
                bufferedPositionMs = next.bufferedPosition.coerceIn(0, durationMs)
                savedState["position"] = positionMs
                delay(500)
            }
        }
    }

    fun toggle() { if (player?.playWhenReady == true) player?.pause() else play() }
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
        player?.release()
        player = null
        bufferedPositionMs = 0
        playing = false
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
