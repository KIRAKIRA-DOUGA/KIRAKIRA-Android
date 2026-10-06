package moe.kirakira.feature.player

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.compose.ContentFrame
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import moe.kirakira.MainActivity
import moe.kirakira.R
import moe.kirakira.feature.settings.PlaybackSettings
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
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
    val initialLoadingAlpha by rememberPlayerInitialLoadingAlpha(
        visible = playback.initialLoading,
        immediatelyHidden = playback.failed || playback.player == null || !playback.miniPlayer || pip,
    )
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
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) playback.flushHistory()
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
        var controlsVisible by remember { mutableStateOf(true) }
        val controlsAlpha by animateFloatAsState(
            targetValue = if (controlsVisible) 1f else 0f,
            animationSpec = if (controlsVisible) MaterialTheme.motionScheme.defaultEffectsSpec()
            else MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "MiniPlayerControlsAlpha",
        )
        var interaction by remember { mutableIntStateOf(0) }
        var dragging by remember { mutableStateOf(false) }
        val restoreInteraction = remember { MutableInteractionSource() }
        val toggleInteraction = remember { MutableInteractionSource() }
        val closeInteraction = remember { MutableInteractionSource() }
        val restorePressed by restoreInteraction.collectIsPressedAsState()
        val togglePressed by toggleInteraction.collectIsPressedAsState()
        val closePressed by closeInteraction.collectIsPressedAsState()
        val holdingControls = dragging || restorePressed || togglePressed || closePressed
        val touchExploration = rememberTouchExplorationEnabled()
        val keepVisible = playback.buffering || playback.initialLoading || initialLoadingAlpha > 0f || touchExploration
        val focusManager = LocalFocusManager.current
        var controlsHaveFocus by remember { mutableStateOf(false) }

        fun interact() {
            controlsVisible = true
            interaction++
        }

        fun hideControls() {
            controlsVisible = false
            interaction++
        }

        fun controlAction(action: () -> Unit) {
            if (controlsVisible) {
                interact()
                action()
            }
        }

        fun finishDrag() {
            horizontal = if (horizontal < 0.5f) 0f else 1f
            dragging = false
            interaction++
        }

        LaunchedEffect(keepVisible, holdingControls, interaction, controlsVisible) {
            if (keepVisible) controlsVisible = true
            else if (controlsVisible && !holdingControls) {
                delay(3_000)
                hideControls()
            }
        }
        LaunchedEffect(controlsVisible) {
            if (!controlsVisible && controlsHaveFocus) focusManager.clearFocus(force = true)
        }
        val controlsLabel =
            stringResource(if (controlsVisible) R.string.player_hide_controls else R.string.player_show_controls)
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
            val buttonSize = minOf(48.dp, width / 3f, height)
            val iconSize = minOf(24.dp, buttonSize)
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
                        try {
                            detectDragGestures(
                                onDragStart = { dragging = true },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() },
                            ) { change, delta ->
                                change.consume()
                                horizontal = if (maxX > 0) (horizontal + delta.x / maxX).coerceIn(0f, 1f) else 1f
                                vertical = if (maxY > 0) (vertical + delta.y / maxY).coerceIn(0f, 1f) else 1f
                            }
                        } finally {
                            dragging = false
                        }
                    }
                    .semantics { contentDescription = controlsLabel }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = controlsLabel,
                    ) {
                        if (keepVisible || !controlsVisible) interact() else hideControls()
                    },
            ) {
                ContentFrame(player, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (initialLoadingAlpha > 0f) {
                    PlayerInitialLoadingOverlay(alpha = { initialLoadingAlpha })
                }
                if (!controlsVisible) {
                    // Intercept taps while controls fade out so revealing them cannot trigger an action.
                    Box(
                        Modifier.fillMaxSize().zIndex(1f).clearAndSetSemantics { }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { interact() },
                    )
                }
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides buttonSize,
                    LocalRippleConfiguration provides RippleConfiguration(color = Color.White),
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer { alpha = controlsAlpha }
                            .then(if (!controlsVisible) Modifier.clearAndSetSemantics { } else Modifier)
                            .focusProperties {
                                onEnter = { if (!controlsVisible) cancelFocusChange() }
                            }
                            .onFocusChanged { controlsHaveFocus = it.hasFocus }
                            .focusGroup()
                            .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        IconButton(
                            onClick = { controlAction(onRestore) },
                            colors = iconColors,
                            interactionSource = restoreInteraction,
                            modifier = Modifier.align(Alignment.TopStart).size(buttonSize),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_symbol_fullscreen),
                                restoreLabel,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                        IconButton(
                            onClick = { controlAction(playback::toggle) },
                            colors = iconColors,
                            interactionSource = toggleInteraction,
                            modifier = Modifier.align(Alignment.Center).size(buttonSize).semantics {
                                if (playback.buffering || playback.initialLoading) contentDescription = toggleLabel
                            },
                        ) {
                            if (playback.buffering || playback.initialLoading) {
                                IndeterminateCircularProgressIndicator(
                                    modifier = Modifier.size(iconSize),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                AnimatedPlaybackIcon(
                                    motion = iconMotion,
                                    description = toggleLabel,
                                    modifier = Modifier.size(iconSize),
                                )
                            }
                        }
                        IconButton(
                            onClick = { controlAction(playback::closeMiniPlayer) },
                            colors = iconColors,
                            interactionSource = closeInteraction,
                            modifier = Modifier.align(Alignment.TopEnd).size(buttonSize),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_symbol_close),
                                stringResource(R.string.player_mini_close),
                                modifier = Modifier.size(iconSize),
                            )
                        }
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
