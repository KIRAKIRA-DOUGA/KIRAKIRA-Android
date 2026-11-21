package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.DestructiveActionIcon
import moe.kirakira.ui.components.FlatTextButton
import moe.kirakira.ui.components.MenuDivider
import moe.kirakira.ui.components.MenuPopup
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.menuItemColors
import moe.kirakira.ui.components.menuItemShapes

internal enum class ComponentOverlay { PLAIN, ICON, DANGER, CHOICE, LONG_BUTTONS, THREE_ACTIONS, MENU, SHEET }

@Composable
internal fun DialogSamples(option: Int, onOpen: (ComponentOverlay) -> Unit) {
    SampleSection(R.string.component_gallery_variant_standard_dialogs) {
        SampleRow {
            FlatTextButton(onClick = { onOpen(ComponentOverlay.PLAIN) }) { Text("普通 Dialog") }
            FlatTextButton(onClick = { onOpen(ComponentOverlay.ICON) }) { Text("图标 Dialog") }
            FlatTextButton(onClick = { onOpen(ComponentOverlay.DANGER) }) { Text("危险确认") }
            FlatTextButton(onClick = { onOpen(ComponentOverlay.CHOICE) }) { Text("选择 Dialog") }
        }
        Text("当前选项 ${option + 1}")
    }
    SampleSection(R.string.component_gallery_variant_dialog_actions) {
        SampleRow {
            FlatTextButton(onClick = { onOpen(ComponentOverlay.LONG_BUTTONS) }) { Text("长按钮 Dialog") }
            FlatTextButton(onClick = { onOpen(ComponentOverlay.THREE_ACTIONS) }) { Text("三操作 Dialog") }
        }
    }
}

@Composable
internal fun MenuSample(
    expanded: Boolean,
    option: Int,
    onExpandedChange: (Boolean) -> Unit,
    onChoose: (Int) -> Unit,
    onFeedback: (String) -> Unit,
) {
    Box {
        FlatTextButton(onClick = { onExpandedChange(true) }) { Text("弹出菜单") }
        MenuPopup(
            expanded = expanded,
            onDismissRequest = {
                onExpandedChange(false)
                onFeedback("已关闭菜单")
            },
        ) {
            val colors = menuItemColors()
            val shapes = menuItemShapes()
            repeat(4) { index ->
                if (index == 2) MenuDivider()
                SelectableDropdownMenuItem(
                    selected = option == index,
                    enabled = index != 3,
                    text = { Text(if (index == 3) "禁用选项" else "选项 ${index + 1}") },
                    onClick = { onChoose(index) },
                    shapes = shapes,
                    colors = colors,
                )
            }
        }
    }
    Text("当前选项 ${option + 1}")
}

@Composable
internal fun SheetSample(onOpen: () -> Unit) {
    FlatTextButton(onClick = onOpen) { Text("底部弹层") }
}

@Composable
internal fun SnackbarSamples(onFeedback: (String) -> Unit, onSnackbarAction: () -> Unit) {
    SampleRow {
        FlatTextButton(onClick = { onFeedback("这是一条 Snackbar") }) { Text("普通 Snackbar") }
        FlatTextButton(onClick = onSnackbarAction) { Text("带操作的 Snackbar") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComponentTestOverlay(
    overlay: ComponentOverlay?,
    option: Int,
    onOptionChange: (Int) -> Unit,
    onClose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftOption by remember(overlay) { mutableIntStateOf(option) }
    when (overlay) {
        ComponentOverlay.SHEET -> ModalBottomSheet(
            onDismissRequest = { onClose("已关闭底部弹层") },
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("底部弹层", style = MaterialTheme.typography.titleLarge)
                ConnectedListGroup {
                    repeat(3) { index ->
                        SegmentedListItem(shapes = connectedListItemShapes(index, 3),
                            leadingContent = { SampleIcon() }, content = { Text("条目 ${index + 1}") })
                    }
                }
                ShadowButton(onClick = { onClose("已完成底部弹层操作") }, modifier = Modifier.fillMaxWidth()) {
                    Text("完成")
                }
            }
        }
        ComponentOverlay.PLAIN, ComponentOverlay.ICON, ComponentOverlay.DANGER, ComponentOverlay.CHOICE,
        ComponentOverlay.LONG_BUTTONS, ComponentOverlay.THREE_ACTIONS -> {
            val danger = overlay == ComponentOverlay.DANGER
            val choice = overlay == ComponentOverlay.CHOICE
            val longButtons = overlay == ComponentOverlay.LONG_BUTTONS
            ActionDialog(
                onDismissRequest = { onClose("已取消") },
                modifier = modifier,
                title = { Text(when (overlay) {
                    ComponentOverlay.ICON -> "带图标的 Dialog"
                    ComponentOverlay.DANGER -> "确认危险操作？"
                    ComponentOverlay.CHOICE -> "选择选项"
                    ComponentOverlay.LONG_BUTTONS -> "长按钮 Dialog"
                    ComponentOverlay.THREE_ACTIONS -> "三操作 Dialog"
                    else -> "普通 Dialog"
                }) },
                icon = when (overlay) {
                    ComponentOverlay.DANGER -> ({ DestructiveActionIcon(R.drawable.ic_icon_delete) })
                    ComponentOverlay.ICON -> ({
                        Icon(
                            painter = painterResource(R.drawable.ic_icon_info),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    })
                    else -> null
                },
                text = {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        if (choice) {
                            Column(Modifier.selectableGroup()) {
                                repeat(3) { index ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().selectable(
                                            selected = draftOption == index,
                                            role = Role.RadioButton,
                                            onClick = { draftOption = index },
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ShadowRadioButton(draftOption == index, onClick = null)
                                        Text("选项 ${index + 1}")
                                    }
                                }
                            }
                        } else Text(if (danger) "确认后显示本地操作反馈" else "Dialog 内容示例")
                    }
                },
                actions = {
                    ShadowButton(
                        onClick = {
                            if (choice) onOptionChange(draftOption)
                            onClose(if (choice) "已选择选项 ${draftOption + 1}" else "已确认")
                        },
                        destructive = danger,
                    ) { Text(if (longButtons) "确认并继续执行当前选择的全部操作" else "确认") }
                    if (overlay == ComponentOverlay.THREE_ACTIONS) {
                        FlatTextButton(
                            onClick = { onClose("已选择稍后处理") },
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) { Text("稍后处理") }
                    }
                    FlatTextButton(
                        onClick = { onClose("已取消") },
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(if (longButtons) "暂不执行并返回继续检查当前设置" else "取消") }
                },
            )
        }
        ComponentOverlay.MENU, null -> Unit
    }
}
