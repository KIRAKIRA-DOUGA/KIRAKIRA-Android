package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        }
        Text("独立选择按钮：按压与选中形变")
        SampleRow {
            ShadowToggleButton(checked = state.toggled, onCheckedChange = { onChange(state.copy(toggled = it)) }) {
                Text(if (state.toggled) "已切换" else "切换按钮")
            }
            FollowButton(following = state.following, onFollowingChange = { onChange(state.copy(following = it)) })
            FollowButton(following = true, onFollowingChange = {}, enabled = false)
        }
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
