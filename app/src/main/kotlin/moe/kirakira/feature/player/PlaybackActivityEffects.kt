package moe.kirakira.feature.player

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import moe.kirakira.MainActivity
import moe.kirakira.data.settings.PlaybackSettings

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlaybackActivityEffects(
    playback: PlaybackViewModel,
    activity: MainActivity?,
    settings: PlaybackSettings?,
    videoPageActive: Boolean,
    imageViewerActive: Boolean,
    pip: Boolean,
) {
    val view = LocalView.current
    LaunchedEffect(playback, settings?.autoQuality, settings?.preferredVideoHeight) {
        settings?.let { playback.setQualityPreference(it.autoQuality, it.preferredVideoHeight) }
    }
    LaunchedEffect(activity, videoPageActive) {
        if (!videoPageActive) activity?.setVideoFullscreen(false)
    }
    DisposableEffect(activity, playback, view) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) {
                playback.flushHistory()
                if (activity?.isInPictureInPictureMode != true) playback.closeMiniPlayer()
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            activity?.updatePictureInPicture(false, null)
            view.keepScreenOn = false
        }
    }
    LaunchedEffect(pip) {
        if (!pip && activity != null && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            playback.closeMiniPlayer()
        }
    }
    DisposableEffect(playback.playing, view) {
        view.keepScreenOn = playback.playing
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(
        playback.player, playback.playing, playback.failed, playback.bounds, playback.actualVideoHeight,
        settings?.outsideAppMiniPlayer, videoPageActive, playback.miniPlayer, imageViewerActive, pip,
    ) {
        val size = playback.player?.videoSize
        val ratio = if (size != null && size.width > 0 && size.height > 0) {
            size.width.toFloat() * size.pixelWidthHeightRatio / size.height
        } else 16f / 9f
        activity?.updatePictureInPicture(
            eligible = !imageViewerActive && (videoPageActive || playback.miniPlayer) &&
                playback.player != null && !playback.failed,
            bounds = playback.bounds,
            aspectRatio = ratio,
            autoEnter = playback.playing && settings?.outsideAppMiniPlayer == true,
        )
    }
}
