package moe.kirakira.feature.player

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scrim
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import java.math.BigDecimal
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.ui.components.list.ConnectedListGroup
import moe.kirakira.ui.components.list.connectedListItemShapes
import moe.kirakira.ui.components.selection.AppChipDefaults
import moe.kirakira.ui.components.selection.ShadowRadioButton
import moe.kirakira.ui.components.selection.SmoothSlider
import moe.kirakira.ui.theme.KIRAKIRATheme

internal enum class PlayerSettingsPanel { QUALITY, SPEED, GENERAL }

internal fun speedNumber(speed: Float): String = BigDecimal(speed.toString()).stripTrailingZeros().toPlainString()

@Composable
internal fun speedLabel(speed: Float): String = stringResource(R.string.player_speed_value, speedNumber(speed))

@Composable
internal fun PlayerSettingsButtons(
    state: PlayerUiState,
    enabled: Boolean,
    onOpen: (PlayerSettingsPanel) -> Unit,
    modifier: Modifier = Modifier,
    settingsOpen: Boolean = false,
) {
    val quality = state.selectedQualityHeight?.let { stringResource(R.string.player_quality_value, it) }
        ?: stringResource(R.string.player_quality_auto)
    val speed = speedLabel(state.speed)
    val adjustedSpeed = state.speed != 1f
    val speedAngle = rememberPlaybackSpeedAngle(state.speed, state.playing)
    val settingsRotation = animateFloatAsState(
        targetValue = if (settingsOpen) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "PlayerSettingsRotation",
    )
    val speedColor by animateColorAsState(
        targetValue = if (adjustedSpeed) MaterialTheme.colorScheme.primary else Color.White,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "PlayerSpeedColor",
    )
    val qualityDescription = stringResource(R.string.player_quality_description, quality)
    val speedDescription = stringResource(R.string.player_speed_description, speed)
    val textColors = ButtonDefaults.textButtonColors(
        containerColor = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        disabledContainerColor = Color.Black.copy(alpha = 0.3f),
        disabledContentColor = Color.White.copy(alpha = 0.38f),
    )
    val iconColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = textColors.containerColor,
        contentColor = textColors.contentColor,
        disabledContainerColor = textColors.disabledContainerColor,
        disabledContentColor = textColors.disabledContentColor,
    )
    val buttonSize = ButtonDefaults.MinHeight
    val iconContainerSize = IconButtonDefaults.smallContainerSize()
    // Match the icon buttons' native layout inset without constraining their touch targets.
    val textButtonOuterPadding = (
        (LocalMinimumInteractiveComponentSize.current.takeOrElse { 0.dp } - iconContainerSize.width) / 2f
    ).coerceAtLeast(0.dp)
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onOpen(PlayerSettingsPanel.QUALITY) },
            enabled = enabled && state.qualityOptions.isNotEmpty() && !state.failed,
            colors = textColors,
            shape = ButtonDefaults.shapesFor(buttonSize).shape,
            contentPadding = ButtonDefaults.contentPaddingFor(buttonSize),
            modifier = Modifier
                .padding(horizontal = textButtonOuterPadding)
                .heightIn(min = buttonSize)
                .semantics { contentDescription = qualityDescription },
        ) { Text(quality, style = ButtonDefaults.textStyleFor(buttonSize)) }
        AnimatedContent(
            targetState = state.speed,
            contentKey = { it != 1f },
            contentAlignment = Alignment.CenterEnd,
            transitionSpec = {
                (fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec)).using(
                    SizeTransform { _, _ -> sizeSpec },
                )
            },
            label = "PlayerSpeedButton",
        ) { displayedSpeed ->
            if (displayedSpeed == 1f) {
                FilledIconButton(
                    onClick = { onOpen(PlayerSettingsPanel.SPEED) },
                    enabled = enabled && state.available && !state.failed,
                    colors = iconColors.copy(contentColor = speedColor),
                    modifier = Modifier.semantics { contentDescription = speedDescription },
                ) {
                    PlaybackSpeedIcon(
                        angle = { speedAngle.value },
                        modifier = Modifier.size(IconButtonDefaults.smallIconSize),
                    )
                }
            } else {
                TextButton(
                    onClick = { onOpen(PlayerSettingsPanel.SPEED) },
                    enabled = enabled && state.available && !state.failed,
                    colors = textColors.copy(contentColor = speedColor),
                    shape = ButtonDefaults.shapesFor(buttonSize).shape,
                    contentPadding = ButtonDefaults.contentPaddingFor(buttonSize, hasStartIcon = true),
                    modifier = Modifier
                        .padding(horizontal = textButtonOuterPadding)
                        .heightIn(min = buttonSize)
                        .semantics { contentDescription = speedDescription },
                ) {
                    PlaybackSpeedIcon(
                        angle = { speedAngle.value },
                        modifier = Modifier.size(IconButtonDefaults.smallIconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonSize)))
                    PlaybackSpeedText(displayedSpeed, style = ButtonDefaults.textStyleFor(buttonSize))
                }
            }
        }
        FilledIconButton(
            onClick = { onOpen(PlayerSettingsPanel.GENERAL) },
            enabled = enabled,
            colors = iconColors,
        ) {
            Icon(
                painterResource(R.drawable.ic_icon_settings),
                stringResource(R.string.player_settings),
                Modifier
                    .size(IconButtonDefaults.smallIconSize)
                    .graphicsLayer { rotationZ = settingsRotation.value },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSettingsSheet(
    panel: PlayerSettingsPanel,
    state: PlayerUiState,
    fullscreen: Boolean,
    inlinePlayerBottom: Dp,
    onDismiss: () -> Unit,
    onQuality: (Int?) -> Unit,
    onSpeed: (Float) -> Unit,
    onContinuousSpeed: (Boolean) -> Unit,
    onPreservesPitch: (Boolean) -> Unit,
    playbackSettings: PlaybackSettings?,
    danmakuSettings: DanmakuSettings?,
    onOutsideAppMiniPlayerChange: (Boolean) -> Unit,
    onInAppMiniPlayerChange: (Boolean) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onDanmakuSettingsChange: (DanmakuSettings) -> Unit,
    modifier: Modifier = Modifier,
    confinedToPane: Boolean = false,
) {
    val preferencesPager = rememberPagerState(pageCount = { 2 })
    val draggingPreferences by preferencesPager.interactionSource.collectIsDraggedAsState()
    var handlingPagerGesture by remember(preferencesPager) { mutableStateOf(false) }
    LaunchedEffect(preferencesPager) {
        snapshotFlow { draggingPreferences to preferencesPager.isScrollInProgress }
            .collect { (dragging, scrolling) ->
                if (dragging) handlingPagerGesture = true
                else if (!scrolling) handlingPagerGesture = false
            }
    }
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    val safeDrawing = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // Reserve equal space before the sheet's width cap so its content stays centered.
    // The fold viewport already excludes the system and hinge insets.
    val sideInset = if (confinedToPane) 0 else {
        maxOf(safeDrawing.getLeft(density, layoutDirection), safeDrawing.getRight(density, layoutDirection))
    }
    val horizontalInsets = WindowInsets(left = sideInset, right = sideInset)
    val contentWindowInsets: @Composable () -> WindowInsets = {
        if (!confinedToPane && panel == PlayerSettingsPanel.GENERAL) WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        else WindowInsets(0, 0, 0, 0)
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    // Material 3's sheet post-fling handler also consumes the child's horizontal velocity.
    // Keep it out of the Pager's gesture and fling, then restore ordinary sheet dragging.
    val gesturesEnabled = panel != PlayerSettingsPanel.GENERAL || !(draggingPreferences || handlingPagerGesture)
    val content: @Composable ColumnScope.() -> Unit = {
        when (panel) {
            PlayerSettingsPanel.GENERAL -> BoxWithConstraints {
                // Keep the sheet's full height constraints for its bottom anchor.
                // Leave room for the fixed header and scrollable options in short windows.
                val minimumHeight = minOf(maxHeight, 192.dp)
                val contentHeight = if (fullscreen || confinedToPane) maxHeight
                else (maxHeight - inlinePlayerBottom).coerceAtLeast(minimumHeight)
                PlayerPreferencesContent(
                    pagerState = preferencesPager,
                    playbackSettings = playbackSettings,
                    danmakuSettings = danmakuSettings,
                    onOutsideAppMiniPlayerChange = onOutsideAppMiniPlayerChange,
                    onInAppMiniPlayerChange = onInAppMiniPlayerChange,
                    onAutoplayChange = onAutoplayChange,
                    onDanmakuSettingsChange = onDanmakuSettingsChange,
                    modifier = Modifier.height(contentHeight),
                )
            }
            PlayerSettingsPanel.QUALITY, PlayerSettingsPanel.SPEED -> PlaybackOptionsContent(
                parentContainerColor = sheetColor,
                panel = panel,
                state = state,
                onDismiss = onDismiss,
                onQuality = onQuality,
                onSpeed = onSpeed,
                onContinuousSpeed = onContinuousSpeed,
                onPreservesPitch = onPreservesPitch,
                applyBottomInset = !confinedToPane,
            )
        }
    }
    if (fullscreen || confinedToPane) {
        InPlayerSettingsSheet(
            sheetState = sheetState,
            gesturesEnabled = gesturesEnabled,
            containerColor = sheetColor,
            horizontalInsets = horizontalInsets,
            contentWindowInsets = contentWindowInsets,
            onDismiss = onDismiss,
            modifier = modifier,
            content = content,
        )
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = modifier.windowInsetsPadding(horizontalInsets),
            containerColor = sheetColor,
            sheetState = sheetState,
            sheetGesturesEnabled = gesturesEnabled,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InPlayerSettingsSheet(
    sheetState: SheetState,
    gesturesEnabled: Boolean,
    containerColor: Color,
    horizontalInsets: WindowInsets,
    contentWindowInsets: @Composable () -> WindowInsets,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dismiss: () -> Unit = remember(sheetState, scope) {
        {
            scope.launch {
                sheetState.hide()
                currentOnDismiss()
            }
        }
    }
    val hasExpandedState = sheetState.hasExpandedState
    LaunchedEffect(sheetState, hasExpandedState) {
        if (hasExpandedState && sheetState.targetValue == SheetValue.Hidden) sheetState.show()
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetState.targetValue == SheetValue.Hidden) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "PlayerSettingsScrimAlpha",
    )

    // BottomSheet handles predictive back once visible. Cover the opening frames as well.
    BackHandler(enabled = !sheetState.isVisible, onBack = dismiss)
    Box(
        modifier.clipToBounds().semantics {
            dialog()
            isTraversalGroup = true
        },
    ) {
        Scrim(
            contentDescription = stringResource(R.string.player_settings_sheet_close),
            onClick = dismiss,
            alpha = { scrimAlpha },
        )
        BottomSheet(
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(horizontalInsets),
            state = sheetState,
            onDismissRequest = onDismiss,
            gesturesEnabled = gesturesEnabled,
            containerColor = containerColor,
            tonalElevation = 0.dp,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}

@Composable
private fun PlaybackOptionsContent(
    parentContainerColor: Color,
    panel: PlayerSettingsPanel,
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onQuality: (Int?) -> Unit,
    onSpeed: (Float) -> Unit,
    onContinuousSpeed: (Boolean) -> Unit,
    onPreservesPitch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    applyBottomInset: Boolean = true,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .then(if (applyBottomInset) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                else Modifier)
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(if (panel == PlayerSettingsPanel.QUALITY) R.string.player_quality else R.string.player_speed),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (panel == PlayerSettingsPanel.QUALITY) {
            val options = listOf<VideoQualityOption?>(null) + state.qualityOptions
            ConnectedListGroup(Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val height = option?.height
                    val isSelected = state.selectedQualityHeight == height
                    SegmentedListItem(
                        onClick = { onQuality(height); onDismiss() },
                        interactionSource = interactionSource,
                        shapes = connectedListItemShapes(index, options.size),
                        modifier = Modifier.fillMaxWidth().semantics {
                            role = Role.RadioButton
                            selected = isSelected
                        },
                        leadingContent = {
                            ShadowRadioButton(
                                selected = isSelected,
                                onClick = null,
                                interactionSource = interactionSource,
                            )
                        },
                        trailingContent = option?.bitrate?.let { bitrate ->
                            {
                                Text(
                                    stringResource(R.string.player_quality_bitrate, (bitrate / 1000.0).roundToInt()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        supportingContent = if (height == null && state.actualVideoHeight != null) {
                            { Text(stringResource(R.string.player_quality_current, state.actualVideoHeight)) }
                        } else null,
                        content = {
                            Text(if (height == null) stringResource(R.string.player_quality_auto)
                                else stringResource(R.string.player_quality_value, height))
                        },
                    )
                }
            }
        } else {
            val rateLabel = speedLabel(state.speed)
            val sliderLabel = stringResource(R.string.player_speed)
            val slider = rememberSliderState(trackRange = PlaybackSpeedRules.progressRange)
            SideEffect { slider.value = PlaybackSpeedRules.progress(state.speed) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                PlaybackSpeedGauge(
                    speed = state.speed,
                    playing = state.playing,
                    continuousSpeed = state.continuousSpeed,
                    onSpeed = onSpeed,
                    sliderDragging = slider.isDragging,
                    showPitchShift = !state.preservesPitch,
                )
                SmoothSlider(
                    state = slider,
                    onValueChange = { onSpeed(PlaybackSpeedRules.speedAtProgress(it)) },
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = sliderLabel
                        stateDescription = rateLabel
                        setProgress { target ->
                            val next = PlaybackSpeedRules.adjustedSpeed(state.speed, target, state.continuousSpeed)
                            if (next != state.speed) onSpeed(next)
                            next != state.speed
                        }
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(speedLabel(PlaybackSpeedRules.range.start), style = MaterialTheme.typography.labelMedium)
                    Text(speedLabel(1f), style = MaterialTheme.typography.labelMedium)
                    Text(speedLabel(PlaybackSpeedRules.range.endInclusive), style = MaterialTheme.typography.labelMedium)
                }
            }
            val speedChipColors = AppChipDefaults.filterColors(parentContainerColor)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                playbackSpeeds.forEach { speed ->
                    FilterChip(
                        selected = state.speed == speed,
                        onClick = { onSpeed(speed) },
                        label = { Text(speedLabel(speed)) },
                        shape = CircleShape,
                        border = null,
                        colors = speedChipColors,
                    )
                }
            }
            ConnectedListGroup {
                SpeedSwitch(
                    label = R.string.player_continuous_speed,
                    icon = R.drawable.ic_icon_slider_3,
                    checked = state.continuousSpeed,
                    index = 0,
                    onChange = onContinuousSpeed,
                )
                SpeedSwitch(
                    label = R.string.player_preserves_pitch,
                    icon = R.drawable.ic_icon_tunning,
                    checked = state.preservesPitch,
                    index = 1,
                    onChange = onPreservesPitch,
                )
            }
        }
    }
}

@Composable
private fun SpeedSwitch(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    checked: Boolean,
    index: Int,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = { onChange(!checked) },
        shapes = connectedListItemShapes(index, 2),
        modifier = Modifier.fillMaxWidth().semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        content = { Text(stringResource(label)) },
    )
}

@Preview(locale = "zh", widthDp = 360)
@Preview(locale = "en", widthDp = 360)
@Composable
private fun PlayerSettingsButtonsPreview() {
    KIRAKIRATheme {
        PlayerSettingsButtons(PlayerUiState(), enabled = false, onOpen = {})
    }
}
