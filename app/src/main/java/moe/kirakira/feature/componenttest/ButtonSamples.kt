package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.kirakira.ui.components.DestructiveTextButton
import moe.kirakira.ui.components.FollowButton
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.ShadowFilledIconButton
import moe.kirakira.ui.components.ShadowFilledTonalButton
import moe.kirakira.ui.components.ShadowFilledTonalIconButton
import moe.kirakira.ui.components.ShadowFloatingActionButton
import moe.kirakira.ui.components.ShadowToggleButton

@Composable
internal fun ButtonSamples(
    state: ComponentSelection,
    onChange: (ComponentSelection) -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val click = { onFeedback("已点击按钮") }
        Text("独立按钮：按压变形")
        SampleRow {
            ShadowButton(onClick = click) { Text("实心按钮") }
            ShadowFilledTonalButton(onClick = click) { Text("浅色按钮") }
            OutlinedButton(onClick = click, shapes = ButtonDefaults.shapes()) { Text("描边按钮") }
            ShadowButton(onClick = click, destructive = true) { Text("危险操作") }
        }
        SampleRow {
            ShadowFilledIconButton(onClick = click) { SampleIcon(description = "实心图标按钮") }
            ShadowFilledTonalIconButton(onClick = click) { SampleIcon(description = "浅色图标按钮") }
            OutlinedIconButton(onClick = click, shapes = IconButtonDefaults.shapes()) {
                SampleIcon(description = "描边图标按钮")
            }
        }
        Text("平面操作：固定轮廓")
        SampleRow {
            TextButton(onClick = click) { Text("文字按钮") }
            DestructiveTextButton(onClick = click) { Text("危险文字按钮") }
            IconButton(onClick = click) { SampleIcon(description = "普通图标按钮") }
        }
        Text("结构与工具区例外：固定轮廓")
        SampleRow {
            ShadowFilledTonalIconButton(
                onClick = click,
                shapes = IconButtonShapes(IconButtonDefaults.filledShape, IconButtonDefaults.filledShape),
            ) { SampleIcon(description = "固定轮廓工具按钮") }
            ShadowFloatingActionButton(onClick = { onFeedback("已点击添加") }) { SampleIcon(description = "添加") }
        }
        Text("禁用与加载")
        SampleRow {
            ShadowButton(onClick = {}, enabled = false) { Text("禁用按钮") }
            ShadowFilledTonalButton(onClick = {}, enabled = false) { Text("禁用浅色按钮") }
            OutlinedButton(onClick = {}, enabled = false) { Text("禁用描边按钮") }
            ShadowButton(onClick = {}, enabled = false, destructive = true) { Text("禁用危险操作") }
            TextButton(onClick = {}, enabled = false) { Text("禁用文字按钮") }
            // A static loading sample retains its label and disables repeat actions.
            ShadowButton(onClick = {}, enabled = false) {
                IndeterminateCircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
                Text("加载中")
            }
            TextButton(onClick = {}, enabled = false) {
                IndeterminateCircularProgressIndicator(
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
                Text("加载中")
            }
        }
        SampleRow {
            ShadowFilledIconButton(onClick = {}, enabled = false) { SampleIcon(description = "禁用实心图标按钮") }
            ShadowFilledTonalIconButton(onClick = {}, enabled = false) { SampleIcon(description = "禁用浅色图标按钮") }
            IconButton(onClick = {}, enabled = false) { SampleIcon(description = "禁用裸图标按钮") }
            ShadowToggleButton(checked = false, onCheckedChange = {}, enabled = false) { Text("禁用未选中") }
            ShadowToggleButton(checked = true, onCheckedChange = {}, enabled = false) { Text("禁用已选中") }
        }
        Text("独立选择按钮：按压与选中形变")
        SampleRow {
            ShadowToggleButton(checked = state.toggled, onCheckedChange = { onChange(state.copy(toggled = it)) }) { checked ->
                Text(if (checked) "已切换" else "切换按钮")
            }
            FollowButton(following = state.following, onFollowingChange = { onChange(state.copy(following = it)) })
            FollowButton(following = true, onFollowingChange = {}, enabled = false)
            FollowButton(following = false, onFollowingChange = {}, enabled = false)
        }
        Text("模拟加载：完成后更新状态")
        SimulatedLoadingButtons(updateDuringLoading = false)
        Text("模拟加载：加载中先更新状态")
        SimulatedLoadingButtons(updateDuringLoading = true)
        Text("Small / Medium / Large：实心变形，文字固定")
        SampleRow {
            listOf(ButtonDefaults.MinHeight, ButtonDefaults.MediumContainerHeight, ButtonDefaults.LargeContainerHeight)
                .zip(listOf("Small", "Medium", "Large"))
                .forEach { (height, label) ->
                    ShadowButton(
                        onClick = click,
                        modifier = Modifier.heightIn(min = height),
                        shapes = ButtonDefaults.shapesFor(height),
                        contentPadding = ButtonDefaults.contentPaddingFor(height),
                    ) {
                        Text(label, style = ButtonDefaults.textStyleFor(height))
                    }
                    TextButton(
                        onClick = click,
                        modifier = Modifier.heightIn(min = height),
                        shape = ButtonDefaults.shapesFor(height).shape,
                        contentPadding = ButtonDefaults.contentPaddingFor(height),
                    ) {
                        Text(label, style = ButtonDefaults.textStyleFor(height))
                    }
                }
        }
    }
}

@Composable
private fun SimulatedLoadingButtons(updateDuringLoading: Boolean) {
    val scope = rememberCoroutineScope()
    var toggled by rememberSaveable { mutableStateOf(false) }
    var following by rememberSaveable { mutableStateOf(false) }
    var toggleLoading by remember { mutableStateOf(false) }
    var followLoading by remember { mutableStateOf(false) }
    SampleRow {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShadowToggleButton(
                checked = toggled,
                enabled = !toggleLoading,
                onCheckedChange = { target ->
                    if (!toggleLoading) {
                        toggleLoading = true
                        scope.launch {
                            try {
                                delay(600)
                                if (updateDuringLoading) toggled = target
                                delay(600)
                                if (!updateDuringLoading) toggled = target
                            } finally {
                                toggleLoading = false
                            }
                        }
                    }
                },
            ) { checked ->
                Text(if (checked) "已切换" else "切换按钮")
            }
            if (toggleLoading) SimulatedLoadingIndicator()
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FollowButton(
                following = following,
                enabled = !followLoading,
                onFollowingChange = { target ->
                    if (!followLoading) {
                        followLoading = true
                        scope.launch {
                            try {
                                delay(600)
                                if (updateDuringLoading) following = target
                                delay(600)
                                if (!updateDuringLoading) following = target
                            } finally {
                                followLoading = false
                            }
                        }
                    }
                },
            )
            if (followLoading) SimulatedLoadingIndicator()
        }
    }
}

@Composable
private fun SimulatedLoadingIndicator() {
    IndeterminateCircularProgressIndicator(
        modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
        strokeWidth = 2.dp,
    )
    Text("加载中")
}

@Composable
internal fun ConnectedChoiceSamples(modifier: Modifier = Modifier) {
    var enabled by rememberSaveable { mutableStateOf(true) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("启用选择组")
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        listOf(false, true).forEach { fixedPress ->
            var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
            Text(if (fixedPress) "固定按压轮廓" else "按压与选中形变")
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                listOf("公开", "关注", "私密").forEachIndexed { index, label ->
                    val checked = selectedIndex == index
                    val shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        2 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    ShadowToggleButton(
                        checked = checked,
                        onCheckedChange = { selectedIndex = index },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).semantics {
                            role = Role.RadioButton
                            selected = checked
                        },
                        shapes = if (fixedPress) {
                            shapes.copy(pressedShape = if (checked) shapes.checkedShape else shapes.shape)
                        } else shapes,
                    ) { Text(label) }
                }
            }
        }
    }
}
