package moe.kirakira.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import moe.kirakira.ui.components.ShadowFilledIconButton
import moe.kirakira.R
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.theme.KIRAKIRATheme

internal enum class PlayerSettingsPanel { QUALITY, SPEED }

internal fun speedNumber(speed: Float): String = BigDecimal(speed.toString()).stripTrailingZeros().toPlainString()

@Composable
internal fun speedLabel(speed: Float): String = stringResource(R.string.player_speed_value, speedNumber(speed))

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun PlayerSettingsButtons(
    state: PlayerUiState,
    enabled: Boolean,
    onOpen: (PlayerSettingsPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val quality = state.selectedQualityHeight?.let { stringResource(R.string.player_quality_value, it) }
        ?: stringResource(R.string.player_quality_auto)
    val speed = speedLabel(state.speed)
    val adjustedSpeed = state.speed != 1f
    val speedColor by animateColorAsState(
        targetValue = if (adjustedSpeed) MaterialTheme.colorScheme.primary else Color.White,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "PlayerSpeedColor",
    )
    val qualityDescription = stringResource(R.string.player_quality_description, quality)
    val speedDescription = stringResource(R.string.player_speed_description, speed)
    val colors = ButtonDefaults.textButtonColors(
        containerColor = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        disabledContainerColor = Color.Black.copy(alpha = 0.3f),
        disabledContentColor = Color.White.copy(alpha = 0.38f),
    )
    val buttonSize = ButtonDefaults.MinHeight
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onOpen(PlayerSettingsPanel.QUALITY) },
            enabled = enabled && state.qualityOptions.isNotEmpty() && !state.failed,
            colors = colors,
            shapes = ButtonDefaults.shapesFor(buttonSize),
            contentPadding = ButtonDefaults.contentPaddingFor(buttonSize),
            modifier = Modifier.heightIn(min = buttonSize).semantics { contentDescription = qualityDescription },
        ) { Text(quality, style = ButtonDefaults.textStyleFor(buttonSize)) }
        AnimatedContent(
            targetState = if (adjustedSpeed) speed else "",
            contentAlignment = Alignment.CenterEnd,
            transitionSpec = {
                (fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec)).using(
                    SizeTransform { _, _ -> sizeSpec },
                )
            },
            label = "PlayerSpeedButton",
        ) { displayedSpeed ->
            if (displayedSpeed.isEmpty()) {
                ShadowFilledIconButton(
                    onClick = { onOpen(PlayerSettingsPanel.SPEED) },
                    enabled = enabled && state.available && !state.failed,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = speedColor,
                        disabledContainerColor = Color.Black.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.38f),
                    ),
                    modifier = Modifier.semantics { contentDescription = speedDescription },
                ) {
                    Icon(painterResource(R.drawable.ic_symbol_speed), null,
                        Modifier.size(IconButtonDefaults.smallIconSize))
                }
            } else {
                TextButton(
                    onClick = { onOpen(PlayerSettingsPanel.SPEED) },
                    enabled = enabled && state.available && !state.failed,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = speedColor,
                        disabledContainerColor = Color.Black.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.38f),
                    ),
                    shapes = ButtonDefaults.shapesFor(buttonSize),
                    contentPadding = ButtonDefaults.contentPaddingFor(buttonSize, hasStartIcon = true),
                    modifier = Modifier.heightIn(min = buttonSize).semantics { contentDescription = speedDescription },
                ) {
                    Icon(painterResource(R.drawable.ic_symbol_speed), null,
                        Modifier.size(IconButtonDefaults.smallIconSize))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonSize)))
                    Text(displayedSpeed, style = ButtonDefaults.textStyleFor(buttonSize))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerSettingsSheet(
    panel: PlayerSettingsPanel,
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onQuality: (Int?) -> Unit,
    onSpeed: (Float) -> Unit,
    onContinuousSpeed: (Boolean) -> Unit,
    onPreservesPitch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        sheetMaxWidth = 640.dp,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(if (panel == PlayerSettingsPanel.QUALITY) R.string.player_quality else R.string.player_speed),
                style = MaterialTheme.typography.headlineSmall,
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
                val slider = rememberSliderState(trackRange = -2f..2f)
                SideEffect { slider.value = log2(state.speed) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    PlaybackSpeedGauge(speed = state.speed, playing = state.playing)
                    Slider(
                        state = slider,
                        onValueChange = { onSpeed(2f.pow(it)) },
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = sliderLabel
                            stateDescription = rateLabel
                            setProgress { target ->
                                val requested = 2f.pow(target.coerceIn(-2f, 2f))
                                val next = if (state.continuousSpeed) requested else {
                                    if (requested > state.speed) playbackSpeeds.firstOrNull { it > state.speed } ?: state.speed
                                    else if (requested < state.speed) playbackSpeeds.lastOrNull { it < state.speed } ?: state.speed
                                    else state.speed
                                }
                                onSpeed(next)
                                true
                            }
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(speedLabel(0.25f), style = MaterialTheme.typography.labelMedium)
                        Text(speedLabel(1f), style = MaterialTheme.typography.labelMedium)
                        Text(speedLabel(4f), style = MaterialTheme.typography.labelMedium)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    playbackSpeeds.forEach { speed ->
                        FilterChip(
                            selected = state.speed == speed,
                            onClick = { onSpeed(speed) },
                            label = { Text(speedLabel(speed)) },
                        )
                    }
                }
                ConnectedListGroup {
                    SpeedSwitch(R.string.player_continuous_speed, state.continuousSpeed, 0, onContinuousSpeed)
                    SpeedSwitch(R.string.player_preserves_pitch, state.preservesPitch, 1, onPreservesPitch)
                }
            }
        }
    }
}

@Composable
private fun SpeedSwitch(label: Int, checked: Boolean, index: Int, onChange: (Boolean) -> Unit) {
    SegmentedListItem(
        onClick = { onChange(!checked) },
        shapes = connectedListItemShapes(index, 2),
        modifier = Modifier.fillMaxWidth().semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
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
