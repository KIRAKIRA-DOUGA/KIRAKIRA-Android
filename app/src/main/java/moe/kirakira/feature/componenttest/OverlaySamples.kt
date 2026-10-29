package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.ExpressiveMenuPopup
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.connectedListItemShapes

@Composable
internal fun OverlaySamples(
    overlay: ComponentOverlay?,
    option: Int,
    onOpen: (ComponentOverlay?) -> Unit,
    onChoose: (Int) -> Unit,
    onFeedback: (String) -> Unit,
    onSnackbarAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SampleRow {
            listOf(ComponentOverlay.PLAIN to "普通 Dialog", ComponentOverlay.ICON to "图标 Dialog",
                ComponentOverlay.DANGER to "危险确认", ComponentOverlay.CHOICE to "选择 Dialog",
                ComponentOverlay.LONG_BUTTONS to "长按钮 Dialog",
                ComponentOverlay.THREE_ACTIONS to "三操作 Dialog",
                ComponentOverlay.SHEET to "底部弹层").forEach { (kind, label) ->
                TextButton(onClick = { onOpen(kind) }) { Text(label) }
            }
            Box {
                TextButton(onClick = { onOpen(ComponentOverlay.MENU) }) { Text("弹出菜单") }
                ExpressiveMenuPopup(expanded = overlay == ComponentOverlay.MENU,
                    onDismissRequest = {
                        onOpen(null)
                        onFeedback("已关闭菜单")
                    }) {
                    repeat(2) { group ->
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShape(index = group, count = 2),
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            repeat(2) { item ->
                                val index = group * 2 + item
                                SelectableDropdownMenuItem(
                                    selected = option == index,
                                    enabled = index != 3,
                                    text = { Text(if (index == 3) "禁用选项" else "选项 ${index + 1}") },
                                    onClick = { onChoose(index) },
                                    shapes = MenuDefaults.itemShape(index = item, count = 2),
                                    colors = MenuDefaults.selectableItemColors(
                                        containerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTrailingContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        SampleRow {
            TextButton(onClick = { onFeedback("这是一条 Snackbar") }) { Text("普通 Snackbar") }
            TextButton(onClick = onSnackbarAction) { Text("带操作的 Snackbar") }
        }
        Text("当前选项 ${option + 1}")
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
                icon = if (overlay == ComponentOverlay.ICON || danger) {
                    { Icon(painterResource(if (danger) R.drawable.ic_symbol_delete else R.drawable.ic_symbol_info),
                        contentDescription = null, tint = if (danger) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary) }
                } else null,
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
                        TextButton(
                            onClick = { onClose("已选择稍后处理") },
                            contentPadding = ButtonDefaults.ContentPadding,
                        ) { Text("稍后处理") }
                    }
                    TextButton(
                        onClick = { onClose("已取消") },
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(if (longButtons) "暂不执行并返回继续检查当前设置" else "取消") }
                },
            )
        }
        ComponentOverlay.MENU, null -> Unit
    }
}
