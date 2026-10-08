package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.R
import moe.kirakira.data.content.DanmakuFontSize
import moe.kirakira.data.content.DanmakuMode
import moe.kirakira.data.content.DanmakuStyle
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.theme.KIRAKIRATheme

private val danmakuColors =
    listOf(0xFFFFFF, 0xFF3225, 0xF06E8E, 0xFFA800, 0xFBFF34, 0x2CE73F, 0x39C5BB, 0x24C1F2, 0xDC1FED)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
) {
    var open by rememberSaveable(sessionRevision, onLogin != null) { mutableStateOf(false) }
    LaunchedEffect(composerActive) {
        if (!composerActive) open = false
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    ContentComposer(
        draft, R.string.danmaku_write, onDraft, onSend, modifier, enabled, busy,
        state = composerState, active = composerActive, availableHeight = availableHeight,
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
                painterResource(R.drawable.ic_symbol_text_format),
                stringResource(R.string.danmaku_style),
                modifier = Modifier.size(24.dp),
            )
        }
    }
    if (open && composerActive && onLogin == null && enabled && !busy) {
        var custom by rememberSaveable { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = { open = false },
            containerColor = if (custom) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
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
                DanmakuStyleContent(draft, style, onStyle, { custom = true }, { open = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DanmakuStyleContent(
    draft: String,
    style: DanmakuStyle,
    onStyle: (DanmakuStyle) -> Unit,
    onCustomColor: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.danmaku_style),
                Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = { onStyle(DanmakuStyle()) }) { Text(stringResource(R.string.danmaku_style_reset)) }
        }
        DanmakuStylePreview(draft, style)
        Text(stringResource(R.string.danmaku_style_color), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            danmakuColors.forEach { rgb ->
                val color = Color(0xFF000000.toInt() or rgb)
                val checked = style.color == rgb
                val description = stringResource(R.string.danmaku_style_color_value, DanmakuStyle(color = rgb).colorHex)
                Surface(
                    onClick = { onStyle(style.copy(color = rgb)) },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = description
                            selected = checked
                            role = Role.RadioButton
                        },
                    shape = if (checked) MaterialTheme.shapes.medium else MaterialTheme.shapes.extraLarge,
                    color = color,
                    border = BorderStroke(
                        if (checked) 3.dp else 1.dp,
                        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    if (checked) Icon(
                        painterResource(R.drawable.ic_symbol_check), null,
                        Modifier.padding(12.dp),
                        tint = if (color.luminance() > 0.45f) Color.Black else Color.White,
                    )
                }
            }
            FilterChip(
                selected = style.color !in danmakuColors,
                onClick = onCustomColor,
                label = { Text(stringResource(R.string.danmaku_style_custom)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
                leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_palette), null) },
            )
        }
        Text(stringResource(R.string.danmaku_style_size), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DanmakuFontSize.entries.forEachIndexed { index, size ->
                SegmentedButton(
                    selected = style.fontSize == size,
                    onClick = { onStyle(style.copy(fontSize = size)) },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            when (size) {
                                DanmakuFontSize.SMALL -> R.string.danmaku_style_small
                                DanmakuFontSize.MEDIUM -> R.string.danmaku_style_medium
                                DanmakuFontSize.LARGE -> R.string.danmaku_style_large
                            },
                        ),
                    )
                }
            }
        }
        Text(stringResource(R.string.danmaku_style_mode), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DanmakuMode.entries.forEach { mode ->
                FilterChip(
                    selected = style.mode == mode,
                    onClick = { onStyle(style.copy(mode = mode)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    DanmakuMode.RTL -> R.string.danmaku_style_rtl
                                    DanmakuMode.TOP -> R.string.danmaku_style_top
                                    DanmakuMode.BOTTOM -> R.string.danmaku_style_bottom
                                    DanmakuMode.LTR -> R.string.danmaku_style_ltr
                                },
                            ),
                        )
                    },
                )
            }
        }
        ConnectedListGroup {
            SegmentedListItem(
                checked = style.enableRainbow,
                onCheckedChange = { onStyle(style.copy(enableRainbow = it)) },
                shapes = connectedListItemShapes(0, 1),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.Switch },
                trailingContent = { Switch(style.enableRainbow, onCheckedChange = null) },
                content = { Text(stringResource(R.string.danmaku_style_rainbow)) },
            )
        }
        ShadowButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.danmaku_style_done))
        }
    }
}

@Preview(name = "Style · Light", widthDp = 400, heightDp = 900)
@Preview(name = "Style · Dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Style · Narrow · Large text", widthDp = 320, heightDp = 1000, fontScale = 2f)
@Composable
private fun DanmakuStyleContentPreview() {
    KIRAKIRATheme { Surface { DanmakuStyleContent("", DanmakuStyle(), {}, {}, {}) } }
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
                {},
            )
        }
    }
}
