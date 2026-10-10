package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.content.DanmakuFontSize
import moe.kirakira.data.content.DanmakuMode
import moe.kirakira.data.content.DanmakuStyle
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.theme.KIRAKIRATheme

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
            TextButton(
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
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DanmakuFontSize.entries.forEachIndexed { index, size ->
                        SegmentedButton(
                            selected = style.fontSize == size,
                            onClick = { onStyle(style.copy(fontSize = size)) },
                            shape = SegmentedButtonDefaults.itemShape(index, DanmakuFontSize.entries.size),
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
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.danmaku_style_mode), style = MaterialTheme.typography.titleSmall)
                val modes = listOf(DanmakuMode.RTL, DanmakuMode.LTR, DanmakuMode.TOP, DanmakuMode.BOTTOM)
                modes.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { mode ->
                            FilterChip(
                                selected = style.mode == mode,
                                onClick = { onStyle(style.copy(mode = mode)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                leadingIcon = {
                                    Icon(
                                        painterResource(
                                            when (mode) {
                                                DanmakuMode.RTL -> R.drawable.ic_symbol_west
                                                DanmakuMode.LTR -> R.drawable.ic_symbol_east
                                                DanmakuMode.TOP -> R.drawable.ic_symbol_vertical_align_top
                                                DanmakuMode.BOTTOM -> R.drawable.ic_symbol_vertical_align_bottom
                                            },
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            when (mode) {
                                                DanmakuMode.RTL -> R.string.danmaku_style_rtl
                                                DanmakuMode.LTR -> R.string.danmaku_style_ltr
                                                DanmakuMode.TOP -> R.string.danmaku_style_top
                                                DanmakuMode.BOTTOM -> R.string.danmaku_style_bottom
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            val buttonHeight = ButtonDefaults.MediumContainerHeight
            ShadowButton(
                onClick = onDone,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = buttonHeight),
                shapes = ButtonDefaults.shapesFor(buttonHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            ) {
                Text(
                    stringResource(R.string.danmaku_style_done),
                    style = ButtonDefaults.textStyleFor(buttonHeight),
                    maxLines = 1,
                    softWrap = false,
                )
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
                brush = Brush.horizontalGradient(listOf(Color(0xFFF2509E), Color(0xFF308BCD))),
                shape = MaterialTheme.shapes.extraSmall,
            ),
    )
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
