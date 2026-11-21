package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.content.DanmakuFontSize
import moe.kirakira.data.content.DanmakuMode
import moe.kirakira.data.content.DanmakuStyle
import moe.kirakira.ui.components.button.FlatTextButton
import moe.kirakira.ui.components.list.ConnectedListGroup
import moe.kirakira.ui.components.list.connectedListItemShapes
import moe.kirakira.ui.components.selection.Segmented
import moe.kirakira.ui.components.selection.SegmentedItemHorizontalPadding
import moe.kirakira.ui.components.selection.ShadowRadioButton
import moe.kirakira.ui.components.video.DanmakuColors
import moe.kirakira.ui.theme.KIRAKIRATheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DanmakuComposer(
    draft: String,
    style: DanmakuStyle,
    sessionRevision: Long,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onStyle: (DanmakuStyle) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    composerState: ComposerState = remember { ComposerState() },
    composerActive: Boolean = true,
    availableHeight: Dp = 460.dp,
    contentPadding: PaddingValues = PaddingValues(),
    recentKaomoji: List<String> = emptyList(),
    onLogin: (() -> Unit)? = null,
    onKaomojiInserted: (String) -> Unit = {},
    sending: Boolean = false,
) {
    var open by rememberSaveable(sessionRevision, onLogin != null) { mutableStateOf(false) }
    LaunchedEffect(composerActive) {
        if (!composerActive) open = false
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    ContentComposer(
        draft, R.string.danmaku_write, onDraft, onSend, modifier, enabled, busy,
        sending = sending, state = composerState, active = composerActive, availableHeight = availableHeight,
        contentPadding = contentPadding,
        recent = recentKaomoji, onKaomojiInserted = onKaomojiInserted,
        onLogin = onLogin,
    ) { closeKaomoji ->
        IconButton(
            onClick = {
                closeKaomoji()
                focus.clearFocus()
                keyboard?.hide()
                open = true
            },
            enabled = enabled && !busy,
        ) {
            Icon(
                painterResource(R.drawable.ic_icon_text_format),
                stringResource(R.string.danmaku_style),
                modifier = Modifier.size(24.dp),
            )
        }
    }
    if (open && composerActive && onLogin == null && enabled && !busy) {
        var custom by rememberSaveable { mutableStateOf(false) }
        val sheetColor = if (custom) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
        ModalBottomSheet(
            onDismissRequest = { open = false },
            containerColor = sheetColor,
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            if (custom) {
                DanmakuColorEditor(
                    style.color,
                    onDismiss = {
                        focus.clearFocus()
                        keyboard?.hide()
                        custom = false
                    },
                    onConfirm = {
                        onStyle(style.copy(color = it))
                        focus.clearFocus()
                        keyboard?.hide()
                        custom = false
                    },
                )
            } else {
                DanmakuStyleContent(
                    draft, style, onStyle, { custom = true }, parentContainerColor = sheetColor,
                )
            }
        }
    }
}

@Composable
internal fun DanmakuStyleContent(
    draft: String,
    style: DanmakuStyle,
    onStyle: (DanmakuStyle) -> Unit,
    onCustomColor: () -> Unit,
    modifier: Modifier = Modifier,
    parentContainerColor: Color = MaterialTheme.colorScheme.surface,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.danmaku_style),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            FlatTextButton(
                onClick = { onStyle(DanmakuStyle()) },
                enabled = style != DanmakuStyle(),
            ) {
                Text(stringResource(R.string.danmaku_style_reset))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DanmakuStylePreview(draft, style)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.danmaku_style_color),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "#${style.colorHex}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DanmakuColorPalette(
                    color = style.color,
                    onColor = { onStyle(style.copy(color = it)) },
                    onCustomColor = onCustomColor,
                )
            }
            ConnectedListGroup {
                SegmentedListItem(
                    onClick = { onStyle(style.copy(enableRainbow = !style.enableRainbow)) },
                    shapes = connectedListItemShapes(0, 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            role = Role.Switch
                            toggleableState = ToggleableState(style.enableRainbow)
                        },
                    leadingContent = { CreatorGradientSample() },
                    trailingContent = { Switch(style.enableRainbow, onCheckedChange = null) },
                    content = { Text(stringResource(R.string.danmaku_style_rainbow)) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.danmaku_style_size), style = MaterialTheme.typography.titleSmall)
                Segmented(
                    items = DanmakuFontSize.entries,
                    selected = style.fontSize,
                    onSelectedChange = { onStyle(style.copy(fontSize = it)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    parentContainerColor = parentContainerColor,
                ) { size ->
                    Text(
                        stringResource(
                            when (size) {
                                DanmakuFontSize.SMALL -> R.string.danmaku_style_small
                                DanmakuFontSize.MEDIUM -> R.string.danmaku_style_medium
                                DanmakuFontSize.LARGE -> R.string.danmaku_style_large
                            },
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.danmaku_style_mode), style = MaterialTheme.typography.titleSmall)
                DanmakuModeSelector(
                    selectedMode = style.mode,
                    onModeChange = { onStyle(style.copy(mode = it)) },
                    parentContainerColor = parentContainerColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private data class DanmakuModeOption(
    val mode: DanmakuMode,
    val label: String,
    @DrawableRes val icon: Int,
)

@Composable
private fun DanmakuModeSelector(
    selectedMode: DanmakuMode,
    onModeChange: (DanmakuMode) -> Unit,
    parentContainerColor: Color,
    modifier: Modifier = Modifier,
) {
    val options = DanmakuMode.entries.map { mode ->
        when (mode) {
            DanmakuMode.RTL -> DanmakuModeOption(
                mode, stringResource(R.string.danmaku_style_rtl), R.drawable.ic_icon_danmaku_rtl,
            )
            DanmakuMode.TOP -> DanmakuModeOption(
                mode, stringResource(R.string.danmaku_style_top), R.drawable.ic_icon_danmaku_top,
            )
            DanmakuMode.BOTTOM -> DanmakuModeOption(
                mode, stringResource(R.string.danmaku_style_bottom), R.drawable.ic_icon_danmaku_bottom,
            )
            DanmakuMode.LTR -> DanmakuModeOption(
                mode, stringResource(R.string.danmaku_style_ltr), R.drawable.ic_icon_danmaku_ltr,
            )
        }
    }
    val labelStyle = MaterialTheme.typography.labelLarge.copy(textAlign = TextAlign.Center)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxLabelLines = 2
    BoxWithConstraints(modifier) {
        val segmentWidth = constraints.maxWidth / options.size
        val labelWidth = with(density) { segmentWidth - 2 * SegmentedItemHorizontalPadding.roundToPx() }
        val labelLayouts = options.map { option ->
            textMeasurer.measure(
                text = option.label,
                style = labelStyle,
                maxLines = maxLabelLines,
                constraints = Constraints(maxWidth = labelWidth.coerceAtLeast(0)),
            )
        }
        if (segmentWidth >= with(density) { 48.dp.roundToPx() } && labelLayouts.none { it.hasVisualOverflow }) {
            val labelLines = labelLayouts.maxOf { it.lineCount }
            Segmented(
                items = options,
                selected = options.first { it.mode == selectedMode },
                onSelectedChange = { onModeChange(it.mode) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                parentContainerColor = parentContainerColor,
            ) { option ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(painterResource(option.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(option.label, style = labelStyle, minLines = labelLines, maxLines = maxLabelLines)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                options.forEach { option ->
                    val isSelected = option.mode == selectedMode
                    val interactionSource = remember { MutableInteractionSource() }
                    ListItem(
                        onClick = { if (!isSelected) onModeChange(option.mode) },
                        modifier = Modifier.fillMaxWidth().semantics {
                            role = Role.RadioButton
                            selected = isSelected
                        },
                        interactionSource = interactionSource,
                        leadingContent = {
                            Icon(painterResource(option.icon), contentDescription = null, modifier = Modifier.size(24.dp))
                        },
                        trailingContent = {
                            ShadowRadioButton(
                                selected = isSelected,
                                onClick = null,
                                interactionSource = interactionSource,
                            )
                        },
                        content = { Text(option.label) },
                    )
                }
            }
        }
    }
}

/** Cerasus FlyoutDanmakuFormat's rainbow-example: a 20dp pink-to-blue gradient outline. */
@Composable
private fun CreatorGradientSample(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .padding(2.dp)
            .border(
                width = 3.dp,
                brush = Brush.horizontalGradient(DanmakuColors.rainbowOutline),
                shape = MaterialTheme.shapes.extraSmall,
            ),
    )
}

@Preview(name = "Style · Light", widthDp = 400, heightDp = 900)
@Preview(name = "Style · Dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Style · Narrow · Large text", widthDp = 320, heightDp = 1000, fontScale = 2f)
@Composable
private fun DanmakuStyleContentPreview() {
    KIRAKIRATheme { Surface { DanmakuStyleContent("", DanmakuStyle(), {}, {}) } }
}

@Preview(name = "Style · Custom", widthDp = 400, heightDp = 900)
@Composable
private fun CustomDanmakuStylePreview() {
    KIRAKIRATheme {
        Surface {
            DanmakuStyleContent(
                "",
                DanmakuStyle(0x80A7EF, DanmakuFontSize.LARGE, DanmakuMode.TOP, true),
                {},
                {},
            )
        }
    }
}
