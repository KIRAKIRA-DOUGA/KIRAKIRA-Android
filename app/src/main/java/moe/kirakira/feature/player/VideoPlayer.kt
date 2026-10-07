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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import moe.kirakira.ui.components.ShadowFilledIconButton
import moe.kirakira.R
import moe.kirakira.data.content.DanmakuEntry
import moe.kirakira.feature.settings.DanmakuSettings
import moe.kirakira.feature.video.VideoArtwork
import moe.kirakira.feature.video.durationText
import moe.kirakira.ui.components.AnimatedSlashIcon
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.SlashIconType
import moe.kirakira.ui.components.rememberSlashIconProgress
import moe.kirakira.ui.theme.KIRAKIRATheme

private val PlayerSeekThumbSize = DpSize(16.dp, 16.dp)
private val PlayerSeekVisualOffset = 8.dp

internal data class PlayerUiState(
    val playing: Boolean = false,
    val showPauseIcon: Boolean = playing,
    val buffering: Boolean = false,
    val initialLoading: Boolean = false,
    val artworkPending: Boolean = false,
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
    var artworkLoading by remember(image) { mutableStateOf(!image.isNullOrBlank()) }
    val waitingForArtwork = player == null && (state.artworkPending || artworkLoading)
    val initialLoadingAlpha by rememberPlayerInitialLoadingAlpha(
        visible = state.initialLoading,
        immediatelyHidden = state.failed || player == null || !active || pictureInPicture,
    )
    val showingInitialLoading =
        initialLoadingAlpha > 0f && player != null && active && !pictureInPicture && !state.failed
    val playbackIconMotion = rememberPlaybackIconMotion(
        playing = state.showPauseIcon,
    )
    val danmakuIconProgress = rememberSlashIconProgress(
        slashed = danmakuSettings?.enabled != true,
        ready = danmakuSettings != null,
    )
    var settingsPanel by remember { mutableStateOf<PlayerSettingsPanel?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var dragged by remember(player) { mutableStateOf<Float?>(null) }
    var showRemainingTime by remember(player) { mutableStateOf(false) }
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
    val keepVisible =
        settingsPanel != null || state.buffering || waitingForArtwork || showingInitialLoading || state.failed ||
            dragging || pressing || dragged != null || touchExploration

    fun interact() {
        controlsVisible = true; interaction++
    }

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
    LaunchedEffect(fullscreen, pictureInPicture, active, state.failed, state.initialLoading, waitingForArtwork) {
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
    val controlsLabel =
        stringResource(if (controlsVisible) R.string.player_hide_controls else R.string.player_show_controls)
    // The parent receives unconsumed taps even when the controls' scroll viewport wins hit testing.
    val screenInteraction = if (pictureInPicture) Modifier else Modifier
        .semantics { contentDescription = controlsLabel }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = controlsLabel,
        ) {
            if (keepVisible) interact() else {
                controlsVisible = !controlsVisible; interaction++
            }
        }
    Box(
        modifier
            .background(Color.Black)
            .then(screenInteraction),
    ) {
        if (player != null) {
            PlayerContentFrame(player = player, modifier = Modifier.fillMaxSize())
        } else {
            VideoArtwork(
                image = image,
                modifier = Modifier.fillMaxSize(),
                onLoadingChange = { artworkLoading = it },
            )
        }
        if (player != null && active && !pictureInPicture && !state.failed && danmakuSettings?.enabled == true) {
            DanmakuOverlay(player, danmaku, danmakuSettings, danmakuContentKey)
        }
        if (showingInitialLoading) {
            PlayerInitialLoadingOverlay(
                alpha = { initialLoadingAlpha },
                showBranding = true,
                fullscreen = fullscreen,
            )
        }
        if (!pictureInPicture) {
            // Keep the reveal target above exiting controls so a tap cannot reach them.
            if (!controlsVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .clearAndSetSemantics { }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { interact() },
                )
            }
            CompositionLocalProvider(
                LocalRippleConfiguration provides RippleConfiguration(color = Color.White),
            ) {
                AnimatedVisibility(
                    visible = controlsVisible || state.buffering,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (!controlsVisible) Modifier.clearAndSetSemantics { } else Modifier)
                            .focusProperties {
                                onEnter = { if (!controlsVisible) cancelFocusChange() }
                            }
                            .onFocusChanged { controlsHaveFocus = it.hasFocus }
                            .focusGroup()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.55f),
                                    0.3f to Color.Transparent,
                                    0.6f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.75f),
                                ),
                            ),
                    ) {
                        val iconColors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.Black.copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.38f),
                        )
                        val contentModifier = Modifier
                            .fillMaxSize()
                            .then(if (fullscreen) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                        val backButton: @Composable () -> Unit = {
                            ShadowFilledIconButton(colors = iconColors, onClick = { controlAction(onBack) }) {
                                Icon(
                                    painterResource(R.drawable.ic_symbol_arrow_back),
                                    stringResource(
                                        if (fullscreen) R.string.player_exit_fullscreen else R.string.navigate_back,
                                    ),
                                    Modifier.size(IconButtonDefaults.smallIconSize),
                                )
                            }
                        }
                        val topBar: @Composable () -> Unit = {
                            TopAppBar(
                                title = {},
                                navigationIcon = { backButton() },
                                actions = {
                                    if (state.failed) {
                                        ShadowFilledIconButton(
                                            onClick = { controlAction(onFullscreen) },
                                            colors = iconColors,
                                        ) {
                                            Icon(
                                                painterResource(if (fullscreen) R.drawable.ic_symbol_fullscreen_exit else R.drawable.ic_symbol_fullscreen),
                                                stringResource(if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen),
                                                Modifier.size(IconButtonDefaults.smallIconSize),
                                            )
                                        }
                                    } else if (!showingInitialLoading) {
                                        PlayerSettingsButtons(
                                            state = state,
                                            enabled = active,
                                            onOpen = { panel -> controlAction { settingsPanel = panel } },
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = Color.Transparent,
                                    navigationIconContentColor = Color.White,
                                    actionIconContentColor = Color.White,
                                ),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                            )
                        }
                        if (state.failed) {
                            Column(contentModifier) {
                                topBar()
                                ContentUnavailableView(
                                    state = ContentUnavailableState.ERROR,
                                    title = stringResource(R.string.player_error),
                                    description = null,
                                    onRetry = { controlAction(onRetry) },
                                    retryEnabled = state.available,
                                    presentation = ContentUnavailablePresentation.MEDIA,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            }
                        } else {
                            val seekLabel = stringResource(R.string.player_seek)
                            val seekThumbSize = PlayerSeekThumbSize
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
                                bufferSlider.value =
                                    (state.bufferedPositionMs.toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(
                                        0f,
                                        1f,
                                    )
                                slider.value = dragged
                                    ?: (state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                            }
                            val displayedPositionMs = dragged?.let { (it * state.durationMs).toLong() }
                                ?: state.positionMs
                            val displayedTime = if (showRemainingTime && state.durationMs > 0) {
                                "-${durationText((state.durationMs - displayedPositionMs).coerceAtLeast(0))}"
                            } else {
                                durationText(displayedPositionMs)
                            }
                            PlayerControlsLayout(
                                modifier = contentModifier,
                                fullscreen = fullscreen,
                                topBar = topBar,
                                playback = { buttonSize ->
                                    PlayerPlaybackControl(
                                        state = state,
                                        motion = playbackIconMotion,
                                        artworkLoading = waitingForArtwork,
                                        buttonSize = buttonSize,
                                        onToggle = { controlAction(onToggle) },
                                    )
                                },
                                time = {
                                    Box(
                                        Modifier
                                            .heightIn(min = 48.dp)
                                            .padding(start = 16.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClickLabel = stringResource(R.string.player_toggle_time_format),
                                            ) {
                                                controlAction { showRemainingTime = !showRemainingTime }
                                            },
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(
                                            "$displayedTime / ${durationText(state.durationMs)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                        )
                                    }
                                },
                                actions = {
                                    FlowRow(
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .padding(end = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                                        itemVerticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val danmakuLabel = stringResource(R.string.danmaku_display)
                                        Switch(
                                            checked = danmakuSettings?.enabled == true,
                                            onCheckedChange = { enabled -> controlAction { onDanmakuEnabled(enabled) } },
                                            enabled = active && danmakuSettings != null,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color.Black.copy(alpha = 0.6f),
                                                checkedBorderColor = Color.White.copy(alpha = 0.45f),
                                                checkedIconColor = Color.Black,
                                                uncheckedThumbColor = Color.White.copy(alpha = 0.65f),
                                                uncheckedTrackColor = Color.Black.copy(alpha = 0.6f),
                                                uncheckedBorderColor = Color.White.copy(alpha = 0.45f),
                                                uncheckedIconColor = Color.Black,
                                                disabledCheckedThumbColor = Color.White.copy(alpha = 0.38f),
                                                disabledCheckedTrackColor = Color.Black.copy(alpha = 0.3f),
                                                disabledCheckedBorderColor = Color.White.copy(alpha = 0.12f),
                                                disabledCheckedIconColor = Color.Black.copy(alpha = 0.38f),
                                                disabledUncheckedThumbColor = Color.White.copy(alpha = 0.38f),
                                                disabledUncheckedTrackColor = Color.Black.copy(alpha = 0.3f),
                                                disabledUncheckedBorderColor = Color.White.copy(alpha = 0.12f),
                                                disabledUncheckedIconColor = Color.Black.copy(alpha = 0.38f),
                                            ),
                                            modifier = Modifier.semantics { contentDescription = danmakuLabel },
                                            thumbContent = {
                                                AnimatedSlashIcon(
                                                    type = SlashIconType.DANMAKU,
                                                    progress = { danmakuIconProgress.value },
                                                    description = null,
                                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                                )
                                            },
                                        )
                                        ShadowFilledIconButton(
                                            onClick = { controlAction(onFullscreen) },
                                            colors = iconColors,
                                        ) {
                                            Icon(
                                                painterResource(if (fullscreen) R.drawable.ic_symbol_fullscreen_exit else R.drawable.ic_symbol_fullscreen),
                                                stringResource(if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen),
                                                Modifier.size(IconButtonDefaults.smallIconSize),
                                            )
                                        }
                                    }
                                },
                                progress = {
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
                                        enabled = state.durationMs > 0 && !state.initialLoading,
                                        interactionSource = sliderInteraction,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp - seekThumbSize.width / 2)
                                            .height(36.dp)
                                            .semantics { contentDescription = seekLabel },
                                        thumb = {
                                            SliderDefaults.Thumb(
                                                interactionSource = sliderInteraction,
                                                isVertical = false,
                                                colors = sliderColors,
                                                enabled = state.durationMs > 0 && !state.initialLoading,
                                                thumbSize = seekThumbSize,
                                                modifier = Modifier.offset(y = PlayerSeekVisualOffset),
                                            )
                                        },
                                        track = { sliderState ->
                                            // Both layers share the official geometry; only the upper layer is seekable.
                                            Box(Modifier.offset(y = PlayerSeekVisualOffset)) {
                                                SliderDefaults.Track(
                                                    sliderState = bufferSlider,
                                                    colors = bufferColors,
                                                    modifier = Modifier
                                                        .height(4.dp)
                                                        .clearAndSetSemantics { },
                                                    enabled = state.durationMs > 0 && !state.initialLoading,
                                                    trackCornerSize = 2.dp,
                                                    trackInsideCornerSize = 2.dp,
                                                    thumbTrackGapSize = 0.dp,
                                                    drawStopIndicator = null,
                                                )
                                                SliderDefaults.Track(
                                                    sliderState = sliderState,
                                                    colors = sliderColors,
                                                    modifier = Modifier.height(4.dp),
                                                    enabled = state.durationMs > 0 && !state.initialLoading,
                                                    trackCornerSize = 2.dp,
                                                    trackInsideCornerSize = 2.dp,
                                                    thumbTrackGapSize = 0.dp,
                                                    drawStopIndicator = null,
                                                )
                                            }
                                        },
                                    )
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
private fun PlayerPlaybackControl(
    state: PlayerUiState,
    motion: PlaybackIconMotion,
    artworkLoading: Boolean,
    buttonSize: Dp,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(buttonSize)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (state.buffering || state.initialLoading) {
            IndeterminateCircularProgressIndicator(modifier = Modifier.size(40.dp), color = Color.White)
        } else {
            IconButton(
                onClick = onToggle,
                enabled = state.available,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.38f),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (artworkLoading) {
                    val playLabel = stringResource(R.string.player_play)
                    IndeterminateCircularProgressIndicator(
                        modifier = Modifier.size(40.dp).semantics { contentDescription = playLabel },
                        color = Color.White,
                    )
                } else {
                    AnimatedPlaybackIcon(
                        motion = motion,
                        description = stringResource(if (state.showPauseIcon) R.string.player_pause else R.string.player_play),
                        modifier = Modifier.size(if (buttonSize < 64.dp) 28.dp else 48.dp),
                    )
                }
            }
        }
    }
}

private enum class PlayerControlSlot { TOP, TIME, ACTIONS, PROGRESS, PLAYBACK }

@Composable
private fun PlayerControlsLayout(
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    time: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    progress: @Composable () -> Unit,
    playback: @Composable (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val horizontalOffset = (insets.getRight(density, layoutDirection) - insets.getLeft(density, layoutDirection)) / 2
    val centerOffset = if (fullscreen) IntOffset(
        if (layoutDirection == LayoutDirection.Ltr) horizontalOffset else -horizontalOffset,
        (insets.getBottom(density) - insets.getTop(density)) / 2,
    ) else IntOffset.Zero
    BoxWithConstraints(modifier) {
        val viewportHeight = with(density) { maxHeight.roundToPx() }
        SubcomposeLayout(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) { constraints ->
            val width = constraints.maxWidth
            val loose = Constraints(maxWidth = width)
            val gap = 8.dp.roundToPx()
            val rowGap = 4.dp.roundToPx()
            val bottomPadding = 4.dp.roundToPx()
            fun measure(slot: PlayerControlSlot, content: @Composable () -> Unit): Placeable =
                subcompose(slot) { Box { content() } }.single().measure(loose)

            val top = measure(PlayerControlSlot.TOP, topBar)
            val timestamp = measure(PlayerControlSlot.TIME, time)
            val trailing = measure(PlayerControlSlot.ACTIONS, actions)
            val seek = measure(PlayerControlSlot.PROGRESS, progress)
            fun rows(items: List<Placeable>): List<List<Placeable>> {
                val result = mutableListOf<List<Placeable>>()
                var row = mutableListOf<Placeable>()
                var usedWidth = 0
                items.forEach { item ->
                    if (row.isNotEmpty() && usedWidth + gap + item.width > width) {
                        result.add(row)
                        row = mutableListOf()
                        usedWidth = 0
                    }
                    if (row.isNotEmpty()) usedWidth += gap
                    row.add(item)
                    usedWidth += item.width
                }
                if (row.isNotEmpty()) result.add(row)
                return result
            }

            fun rowsHeight(rows: List<List<Placeable>>): Int =
                rows.sumOf { row -> row.maxOf { it.height } } + rowGap * (rows.size - 1)

            fun arrangeRows(rows: List<List<Placeable>>, startY: Int, place: (Placeable, Int, Int) -> Unit) {
                var y = startY
                rows.forEach { row ->
                    val rowHeight = row.maxOf { it.height }
                    val rowWidth = row.sumOf { it.width } + gap * (row.size - 1)
                    var x = if (row.first() == timestamp) 0 else width - rowWidth
                    row.forEachIndexed { index, item ->
                        if (index == 1 && row.first() == timestamp) x += width - rowWidth
                        place(item, x, y + (rowHeight - item.height) / 2)
                        x += item.width + gap
                    }
                    y += rowHeight + rowGap
                }
            }

            // Center on the video, compensating for asymmetric fullscreen safe Insets.
            val regularRows = rows(listOf(timestamp, trailing))
            val centerSize = 64.dp.roundToPx()
            val centerX = (width - centerSize) / 2 + centerOffset.x
            val centerY = (viewportHeight - centerSize) / 2 + centerOffset.y
            val centerGap = 2.dp.roundToPx()
            val regularRowsY = viewportHeight - bottomPadding - rowsHeight(regularRows)
            val regularSeekY = regularRowsY - seek.height
            val seekVisibleTop = regularSeekY +
                (seek.height - PlayerSeekThumbSize.height.roundToPx()) / 2 + PlayerSeekVisualOffset.roundToPx()
            val seekVisibleBottom = seekVisibleTop + PlayerSeekThumbSize.height.roundToPx()
            var compact = centerX < 0 || centerX + centerSize > width || centerY < top.height + centerGap ||
                centerY + centerSize + centerGap > seekVisibleTop && centerY < seekVisibleBottom + centerGap
            arrangeRows(regularRows, regularRowsY) { item, x, y ->
                if (x < centerX + centerSize + centerGap && x + item.width + centerGap > centerX &&
                    y < centerY + centerSize + centerGap && y + item.height + centerGap > centerY
                ) {
                    compact = true
                }
            }
            val play = measure(PlayerControlSlot.PLAYBACK) { playback(if (compact) 48.dp else 64.dp) }
            val footerRows = if (compact) rows(listOf(timestamp, play, trailing)) else regularRows
            val footerHeight = rowsHeight(footerRows) + seek.height + bottomPadding
            val minimumHeight = top.height + footerHeight + if (compact) gap else 0
            val height = maxOf(viewportHeight, minimumHeight)
            layout(width, height) {
                top.placeRelative(0, 0)
                if (!compact) {
                    play.placeRelative(centerX, centerY)
                }
                seek.placeRelative(0, height - footerHeight)
                arrangeRows(footerRows, height - bottomPadding - rowsHeight(footerRows)) { item, x, y ->
                    item.placeRelative(x, y)
                }
            }
        }
    }
}

@Composable
internal fun rememberTouchExplorationEnabled(): Boolean {
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
        VideoPlayer(
            null,
            PlayerUiState(available = true, durationMs = 180_000, positionMs = 45_000, bufferedPositionMs = 120_000),
            null,
            false,
            false,
            {},
            {},
            {},
            {},
            {},
        )
    }
}
