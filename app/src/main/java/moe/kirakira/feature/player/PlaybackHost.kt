package moe.kirakira.feature.player

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.compose.ContentFrame
import kotlin.math.roundToInt
import moe.kirakira.MainActivity
import moe.kirakira.R
import moe.kirakira.feature.settings.PlaybackSettings

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaybackHost(
    playback: PlaybackViewModel,
    settings: PlaybackSettings?,
    videoPageActive: Boolean,
    bottomBarHeightPx: Int,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.playerActivity() }
    val view = LocalView.current
    val pip = activity?.pictureInPicture == true
    var horizontal by rememberSaveable { mutableStateOf(1f) }
    var vertical by rememberSaveable { mutableStateOf(1f) }
    LaunchedEffect(settings?.autoQuality, settings?.preferredVideoHeight) {
        settings?.let { playback.setQualityPreference(it.autoQuality, it.preferredVideoHeight) }
    }

    LaunchedEffect(videoPageActive) {
        if (!videoPageActive) activity?.setVideoFullscreen(false)
    }
    DisposableEffect(activity, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true &&
                activity?.isInPictureInPictureMode != true) {
                playback.closeMiniPlayer()
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
        settings?.outsideAppMiniPlayer, videoPageActive, playback.miniPlayer, pip,
    ) {
        val size = playback.player?.videoSize
        val ratio = if (size != null && size.width > 0 && size.height > 0) {
            size.width.toFloat() * size.pixelWidthHeightRatio / size.height
        } else 16f / 9f
        activity?.updatePictureInPicture(
            eligible = (videoPageActive || playback.miniPlayer) && playback.player != null && !playback.failed,
            bounds = playback.bounds,
            aspectRatio = ratio,
            autoEnter = playback.playing && settings?.outsideAppMiniPlayer == true,
        )
    }
    val player = playback.player ?: return
    if (pip && !videoPageActive) {
        Box(modifier.fillMaxSize().background(Color.Black)) {
            ContentFrame(player, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    } else if (playback.miniPlayer && !videoPageActive) {
        val iconMotion = rememberPlaybackIconMotion(playback.showPauseIcon)
        val iconColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
        val restoreLabel = stringResource(R.string.player_mini_restore)
        val toggleLabel = stringResource(if (playback.showPauseIcon) R.string.player_pause else R.string.player_play)
        val density = LocalDensity.current
        val safeBottom = WindowInsets.safeDrawing.getBottom(density)
        val keyboardBottom = WindowInsets.ime.getBottom(density)
        val extraBottom = with(density) {
            if (keyboardBottom == 0) (bottomBarHeightPx - safeBottom).coerceAtLeast(0).toDp() else 0.dp
        }
        BoxWithConstraints(
            modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = extraBottom + 8.dp),
        ) {
            val width = minOf(192.dp, maxWidth, maxHeight * 16f / 9f)
            val height = width * 9f / 16f
            val buttonSize = minOf(48.dp, width / 2f, height)
            val maxX = with(density) { (maxWidth - width).toPx().coerceAtLeast(0f) }
            val maxY = with(density) { (maxHeight - height).toPx().coerceAtLeast(0f) }
            Box(
                Modifier.offset { IntOffset((horizontal * maxX).roundToInt(), (vertical * maxY).roundToInt()) }
                    .size(width, height).clip(RoundedCornerShape(8.dp)).background(Color.Black)
                    .onGloballyPositioned {
                        val bounds = it.boundsInWindow()
                        playback.updateBounds(
                            Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()),
                        )
                    }
                    .pointerInput(maxX, maxY) {
                        detectDragGestures(
                            onDragEnd = { horizontal = if (horizontal < 0.5f) 0f else 1f },
                            onDragCancel = { horizontal = if (horizontal < 0.5f) 0f else 1f },
                        ) { change, delta ->
                            change.consume()
                            horizontal = if (maxX > 0) (horizontal + delta.x / maxX).coerceIn(0f, 1f) else 1f
                            vertical = if (maxY > 0) (vertical + delta.y / maxY).coerceIn(0f, 1f) else 1f
                        }
                    },
            ) {
                ContentFrame(player, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Box(
                    Modifier.fillMaxSize().semantics { contentDescription = restoreLabel }
                        .clickable(onClickLabel = restoreLabel, onClick = onRestore),
                )
                Row(Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.55f))) {
                    IconButton(
                        onClick = playback::toggle,
                        colors = iconColors,
                        modifier = Modifier.size(buttonSize).semantics {
                            if (playback.buffering) contentDescription = toggleLabel
                        },
                    ) {
                        if (playback.buffering) {
                            LoadingIndicator(Modifier.size(24.dp), color = Color.White)
                        } else {
                            AnimatedPlaybackIcon(
                                motion = iconMotion,
                                description = toggleLabel,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = playback::closeMiniPlayer,
                        colors = iconColors,
                        modifier = Modifier.size(buttonSize),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_symbol_close),
                            stringResource(R.string.player_mini_close),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun Context.playerActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.playerActivity()
    else -> null
}
