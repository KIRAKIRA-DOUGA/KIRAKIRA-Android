package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.FilledTextField
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.OtpInputField
import moe.kirakira.ui.components.PasswordTextField
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.connectedListItemShapes
import kotlin.math.roundToInt

@Composable
internal fun SelectionSamples(
    state: ComponentSelection,
    onChange: (ComponentSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SampleRow {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("开关")
                Switch(state.switched, { onChange(state.copy(switched = it)) },
                    modifier = Modifier.semantics { contentDescription = "开关" })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("复选框")
                Checkbox(state.checked, { onChange(state.copy(checked = it)) },
                    modifier = Modifier.semantics { contentDescription = "复选框" })
            }
            Switch(checked = true, onCheckedChange = {}, enabled = false,
                modifier = Modifier.semantics { contentDescription = "禁用开关" })
            Checkbox(checked = true, onCheckedChange = {}, enabled = false,
                modifier = Modifier.semantics { contentDescription = "禁用复选框" })
        }
        Column(Modifier.selectableGroup()) {
            repeat(2) { index ->
                Row(
                    Modifier.fillMaxWidth().selectable(state.radio == index,
                        onClick = { onChange(state.copy(radio = index)) }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShadowRadioButton(selected = state.radio == index, onClick = null)
                    Text("单选 ${index + 1}")
                }
            }
        }
        Text("连续滑块 ${(state.progress * 100).roundToInt()}%")
        SampleSlider(value = state.progress, onValueChange = { onChange(state.copy(progress = it)) },
            modifier = Modifier.semantics { contentDescription = "连续滑块" })
        Text("分段滑块 ${(state.stepped * 100).roundToInt()}%")
        SampleSlider(value = state.stepped, onValueChange = { onChange(state.copy(stepped = it)) }, steps = 3,
            modifier = Modifier.semantics { contentDescription = "分段滑块" })
    }
}

@Composable
internal fun InputSamples(
    state: ComponentInput,
    onChange: (ComponentInput) -> Unit,
    error: Boolean,
    onErrorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("错误状态")
            Switch(error, onErrorChange, modifier = Modifier.semantics { contentDescription = "输入框错误状态" })
        }
        FilledTextField(value = state.text, onValueChange = { onChange(state.copy(text = it)) },
            modifier = Modifier.fillMaxWidth(), label = { Text("普通输入框") },
            errorMessage = if (error) "输入内容有误" else null)
        FilledTextField(value = "禁用内容", onValueChange = {}, enabled = false,
            modifier = Modifier.fillMaxWidth(), label = { Text("禁用输入框") })
        FilledTextField(value = "只读内容", onValueChange = {}, readOnly = true,
            modifier = Modifier.fillMaxWidth(), label = { Text("只读输入框") })
        PasswordTextField(value = state.password, onValueChange = { onChange(state.copy(password = it)) },
            passwordVisible = state.passwordVisible,
            onPasswordVisibilityChange = { onChange(state.copy(passwordVisible = !state.passwordVisible)) },
            modifier = Modifier.fillMaxWidth(), label = { Text("密码") },
            errorMessage = if (error) "密码格式有误" else null)
        OtpInputField(value = state.code, onValueChange = { onChange(state.copy(code = it)) },
            label = "六位验证码", enabled = true, modifier = Modifier.fillMaxWidth(),
            error = if (error) "验证码有误" else null)
    }
}

@Composable
internal fun ProgressSamples(progress: Float, onProgressChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("不确定进度 · 40dp / 24dp / 20dp")
        SampleRow {
            IndeterminateCircularProgressIndicator(Modifier.size(40.dp).semantics { contentDescription = "页面加载" })
            IndeterminateCircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = "图标加载" },
                strokeWidth = 2.dp)
            IndeterminateCircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "刷新加载" },
                strokeWidth = 2.5.dp)
        }
        Text("确定进度 ${(progress * 100).roundToInt()}%")
        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(40.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        SampleSlider(value = progress, onValueChange = onProgressChange,
            modifier = Modifier.semantics { contentDescription = "确定进度" })
    }
}

@Composable
internal fun ListSamples(
    state: ComponentSelection,
    onChange: (ComponentSelection) -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ConnectedListGroup {
            SegmentedListItem(shapes = connectedListItemShapes(0, 1), leadingContent = { SampleIcon() },
                supportingContent = { Text("说明文字") }, content = { Text("单项列表") })
        }
        ConnectedListGroup {
            repeat(3) { index ->
                SegmentedListItem(
                    shapes = connectedListItemShapes(index, 3),
                    leadingContent = { SampleIcon() },
                    supportingContent = { Text("连接列表说明") },
                    trailingContent = {
                        when (index) {
                            0 -> Switch(state.switched, { onChange(state.copy(switched = it)) },
                                modifier = Modifier.semantics { contentDescription = "列表开关" })
                            1 -> Checkbox(state.checked, { onChange(state.copy(checked = it)) },
                                modifier = Modifier.semantics { contentDescription = "列表复选框" })
                            else -> Text("尾部文字")
                        }
                    },
                    content = { Text("连接条目 ${index + 1}") },
                )
            }
        }
        SampleRow {
            AssistChip(onClick = { onFeedback("已点击辅助标签") }, label = { Text("辅助标签") })
            SuggestionChip(onClick = { onFeedback("已点击建议标签") }, label = { Text("建议标签") })
            FilterChip(selected = state.filter, onClick = { onChange(state.copy(filter = !state.filter)) },
                label = { Text("筛选标签") }, colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary))
            InputChip(selected = state.chip, onClick = { onChange(state.copy(chip = !state.chip)) },
                label = { Text("输入标签") }, colors = InputChipDefaults.inputChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary))
            FilterChip(selected = true, onClick = {}, enabled = false, label = { Text("禁用标签") })
        }
    }
}

@Composable
private fun SampleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val slider = rememberSliderState(value = value, steps = steps)
    SideEffect { slider.value = value }
    Slider(state = slider, onValueChange = onValueChange, modifier = modifier)
}
