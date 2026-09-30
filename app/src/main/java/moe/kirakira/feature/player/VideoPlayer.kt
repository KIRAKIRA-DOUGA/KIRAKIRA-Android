package moe.kirakira.feature.player

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import androidx.media3.ui.compose.ContentFrame
import kotlinx.coroutines.delay
import moe.kirakira.R
import moe.kirakira.data.content.DanmakuEntry
import moe.kirakira.feature.settings.DanmakuSettings
import moe.kirakira.feature.video.VideoArtwork
import moe.kirakira.feature.video.durationText
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.theme.KIRAKIRATheme

internal data class PlayerUiState(
    val playing: Boolean = false,
    val showPauseIcon: Boolean = playing,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val available: Boolean = false,
    val qualityOptions: List<VideoQualityOption> = emptyList(),
    val selectedQualityHeight: Int? = null,
    val actualVideoHeight: Int? = null,
    val speed: Float = 1f,
    val continuousSpeed: Boolean = false,
    val preservesPitch: Boolean = true,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlayer(
    player: Player?,
    state: PlayerUiState,
    image: String?,
    fullscreen: Boolean,
    pictureInPicture: Boolean,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onFullscreen: () -> Unit,
    onPictureInPicture: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onQuality: (Int?) -> Unit = {},
    onSpeed: (Float) -> Unit = {},
    onContinuousSpeed: (Boolean) -> Unit = {},
    onPreservesPitch: (Boolean) -> Unit = {},
    danmaku: List<DanmakuEntry> = emptyList(),
    danmakuSettings: DanmakuSettings? = null,
    danmakuContentKey: Any = Unit,
    onDanmakuEnabled: (Boolean) -> Unit = {},
) {
    val playbackIconMotion = rememberPlaybackIconMotion(
        playing = state.showPauseIcon,
    )
    var settingsPanel by remember { mutableStateOf<PlayerSettingsPanel?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var dragged by remember(player) { mutableStateOf<Float?>(null) }
    val sliderInteraction = remember { MutableInteractionSource() }
    val dragging by sliderInteraction.collectIsDraggedAsState()
    val pressing by sliderInteraction.collectIsPressedAsState()
    LaunchedEffect(sliderInteraction) {
        sliderInteraction.interactions.collect { event ->
            if (event is DragInteraction.Cancel || event is PressInteraction.Cancel && !dragging) {
                dragged = null
            }
        }
    }
    val touchExploration = rememberTouchExplorationEnabled()
    val keepVisible = settingsPanel != null || !state.playing || state.buffering || state.failed || dragging || pressing || dragged != null || touchExploration
    fun interact() { controlsVisible = true; interaction++ }
    fun controlAction(action: () -> Unit) {
        if (controlsVisible) {
            interact()
            action()
        }
    }
    val focusManager = LocalFocusManager.current
    var controlsHaveFocus by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible && controlsHaveFocus) focusManager.clearFocus(force = true)
    }
    LaunchedEffect(keepVisible, interaction, controlsVisible, fullscreen, pictureInPicture) {
        if (keepVisible) controlsVisible = true
        else if (controlsVisible && !pictureInPicture) {
            delay(3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(fullscreen, pictureInPicture, active, state.failed) {
        settingsPanel = null
        interact()
    }
    if (active && !pictureInPicture && !state.failed) {
        settingsPanel?.let { panel ->
            PlayerSettingsSheet(
                panel, state,
                onDismiss = { settingsPanel = null; interact() },
                onQuality = onQuality,
                onSpeed = onSpeed,
                onContinuousSpeed = onContinuousSpeed,
                onPreservesPitch = onPreservesPitch,
            )
        }
    }
    Box(modifier.background(Color.Black)) {
        if (player != null) {
            ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            VideoArtwork(image, Modifier.fillMaxSize())
        }
        if (player != null && active && !pictureInPicture && !state.failed && danmakuSettings?.enabled == true) {
            DanmakuOverlay(player, danmaku, danmakuSettings, danmakuContentKey)
        }
        if (!pictureInPicture) {
            val controlsLabel = stringResource(if (controlsVisible) R.string.player_hide_controls else R.string.player_show_controls)
            // Keep the reveal target above exiting controls so a tap cannot reach them.
            Box(Modifier.fillMaxSize().zIndex(if (controlsVisible) 0f else 1f)
                .semantics { contentDescription = controlsLabel }.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = controlsLabel,
            ) {
                if (keepVisible) interact() else { controlsVisible = !controlsVisible; interaction++ }
            })
            AnimatedVisibility(
                visible = controlsVisible || state.buffering,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .then(if (!controlsVisible) Modifier.clearAndSetSemantics { } else Modifier)
                        .focusProperties {
                            onEnter = { if (!controlsVisible) cancelFocusChange() }
                        }
                        .onFocusChanged { controlsHaveFocus = it.hasFocus }
                        .focusGroup()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                        .padding(8.dp),
                ) {
                    val iconColors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.38f),
                    )
                    IconButton(
                        colors = iconColors,
                        onClick = { controlAction(onBack) },
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Icon(painterResource(R.drawable.ic_symbol_arrow_back), stringResource(
                            if (fullscreen) R.string.player_exit_fullscreen else R.string.navigate_back,
                        ))
                    }
                    if (state.failed) {
                        ContentUnavailableView(
                            state = ContentUnavailableState.ERROR,
                            title = stringResource(R.string.player_error),
                            description = null,
                            onRetry = { controlAction(onRetry) },
                            retryEnabled = state.available,
                            presentation = ContentUnavailablePresentation.MEDIA,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
                        )
                        IconButton(
                            onClick = { controlAction(onFullscreen) },
                            colors = iconColors,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(
                                painterResource(if (fullscreen) R.drawable.ic_symbol_fullscreen_exit else R.drawable.ic_symbol_fullscreen),
                                stringResource(if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen),
                            )
                        }
                    } else {
                        PlayerSettingsButtons(
                            state = state,
                            enabled = active,
                            onOpen = { panel -> controlAction { settingsPanel = panel } },
                            modifier = Modifier.align(Alignment.TopEnd).padding(start = 48.dp),
                        )
                        Box(
                            modifier = Modifier.align(Alignment.Center).size(64.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.buffering) {
                                LoadingIndicator(modifier = Modifier.size(48.dp), color = Color.White)
                            } else {
                                IconButton(
                                    onClick = { controlAction(onToggle) },
                                    enabled = state.available,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = Color.White,
                                        disabledContentColor = Color.White.copy(alpha = 0.38f),
                                    ),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    AnimatedPlaybackIcon(
                                        motion = playbackIconMotion,
                                        description = stringResource(
                                            if (state.showPauseIcon) R.string.player_pause else R.string.player_play,
                                        ),
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            }
                        }
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp)) {
                            val seekLabel = stringResource(R.string.player_seek)
                            val seekThumbSize = DpSize(16.dp, 16.dp)
                            val sliderColors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.Transparent,
                                disabledThumbColor = Color.White.copy(alpha = 0.38f),
                                disabledActiveTrackColor = Color.White.copy(alpha = 0.38f),
                                disabledInactiveTrackColor = Color.Transparent,
                            )
                            val bufferColors = SliderDefaults.colors(
                                activeTrackColor = Color.White.copy(alpha = 0.55f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
                                disabledInactiveTrackColor = Color.White.copy(alpha = 0.15f),
                            )
                            val slider = rememberSliderState()
                            val bufferSlider = rememberSliderState()
                            SideEffect {
                                bufferSlider.value = (state.bufferedPositionMs.toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                                slider.value = dragged ?: (state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${durationText(dragged?.let { (it * state.durationMs).toLong() } ?: state.positionMs)} / ${durationText(state.durationMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f).padding(start = seekThumbSize.width / 2),
                                )
                                val danmakuLabel = stringResource(R.string.danmaku_display)
                                Switch(
                                    checked = danmakuSettings?.enabled == true,
                                    onCheckedChange = { enabled -> controlAction { onDanmakuEnabled(enabled) } },
                                    enabled = active && danmakuSettings != null,
                                    modifier = Modifier.semantics { contentDescription = danmakuLabel },
                                    thumbContent = {
                                        Icon(
                                            painterResource(
                                                if (danmakuSettings?.enabled == true) R.drawable.ic_custom_danmaku
                                                else R.drawable.ic_custom_danmaku_off,
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    },
                                )
                                IconButton(
                                    onClick = { controlAction(onPictureInPicture) },
                                    enabled = active && player != null && state.available,
                                    colors = iconColors,
                                ) {
                                    Icon(painterResource(R.drawable.ic_symbol_picture_in_picture_alt), stringResource(R.string.player_pip))
                                }
                                IconButton(onClick = { controlAction(onFullscreen) }, colors = iconColors) {
                                    Icon(
                                        painterResource(if (fullscreen) R.drawable.ic_symbol_fullscreen_exit else R.drawable.ic_symbol_fullscreen),
                                        stringResource(if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen),
                                    )
                                }
                            }
                            Slider(
                                state = slider,
                                colors = sliderColors,
                                onValueChange = { value ->
                                    controlAction { slider.value = value; dragged = value }
                                },
                                onValueChangeFinished = {
                                    controlAction {
                                        dragged?.let { onSeek((it * state.durationMs).toLong()) }
                                    }
                                    dragged = null
                                },
                                enabled = state.durationMs > 0,
                                interactionSource = sliderInteraction,
                                modifier = Modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = seekLabel },
                                thumb = {
                                    SliderDefaults.Thumb(
                                        interactionSource = sliderInteraction,
                                        isVertical = false,
                                        colors = sliderColors,
                                        enabled = state.durationMs > 0,
                                        thumbSize = seekThumbSize,
                                    )
                                },
                                track = { sliderState ->
                                    // Both layers share the official geometry; only the upper layer is seekable.
                                    Box {
                                        SliderDefaults.Track(
                                            sliderState = bufferSlider,
                                            colors = bufferColors,
                                            modifier = Modifier.height(6.dp).clearAndSetSemantics { },
                                            enabled = state.durationMs > 0,
                                            trackCornerSize = 3.dp,
                                            trackInsideCornerSize = 3.dp,
                                            thumbTrackGapSize = 0.dp,
                                            drawStopIndicator = null,
                                        )
                                        SliderDefaults.Track(
                                            sliderState = sliderState,
                                            colors = sliderColors,
                                            modifier = Modifier.height(6.dp),
                                            enabled = state.durationMs > 0,
                                            trackCornerSize = 3.dp,
                                            trackInsideCornerSize = 3.dp,
                                            thumbTrackGapSize = 0.dp,
                                            drawStopIndicator = null,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        enabled = manager?.isTouchExplorationEnabled == true
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

@Preview(widthDp = 360, heightDp = 203)
@Composable
private fun VideoPlayerPreview() {
    KIRAKIRATheme {
        VideoPlayer(null, PlayerUiState(available = true, durationMs = 180_000, positionMs = 45_000, bufferedPositionMs = 120_000), null, false, false, {}, {}, {}, {}, {}, {})
    }
}
