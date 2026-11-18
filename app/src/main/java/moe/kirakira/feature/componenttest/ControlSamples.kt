package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import moe.kirakira.R
import moe.kirakira.ui.components.AppChipDefaults
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.FilledTextField
import moe.kirakira.ui.components.FilledTextFieldDefaults
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.OtpInputField
import moe.kirakira.ui.components.PasswordTextField
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.SmoothSlider
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.theme.ThemeColorDefaults
import kotlin.math.roundToInt

@Composable
internal fun SwitchSamples() {
    var checked by rememberSaveable { mutableStateOf(false) }
    SampleSection(R.string.component_gallery_variant_states) {
        SampleRow {
            Switch(
                checked = checked,
                onCheckedChange = { checked = it },
                modifier = Modifier.semantics { contentDescription = "开关" },
            )
            Switch(
                checked = true,
                onCheckedChange = {},
                enabled = false,
                modifier = Modifier.semantics { contentDescription = "禁用开关" },
            )
        }
    }
}

@Composable
internal fun CheckboxSamples() {
    var checked by rememberSaveable { mutableStateOf(false) }
    SampleSection(R.string.component_gallery_variant_states) {
        SampleRow {
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = it },
                modifier = Modifier.semantics { contentDescription = "复选框" },
            )
            Checkbox(
                checked = true,
                onCheckedChange = {},
                enabled = false,
                modifier = Modifier.semantics { contentDescription = "禁用复选框" },
            )
        }
    }
}

@Composable
internal fun RadioSamples() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.selectableGroup()) {
        repeat(2) { index ->
            Row(
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = selected == index,
                    onClick = { selected = index },
                    role = Role.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShadowRadioButton(selected = selected == index, onClick = null)
                Text("单选 ${index + 1}")
            }
        }
    }
}

@Composable
internal fun SliderSamples() {
    var continuous by rememberSaveable { mutableFloatStateOf(0.4f) }
    var stepped by rememberSaveable { mutableFloatStateOf(0.5f) }
    SampleSection(R.string.component_gallery_variant_continuous) {
        Text(
            text = "连续滑块 ${(continuous * 100).roundToInt()}%",
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
        SampleSlider(
            value = continuous,
            onValueChange = { continuous = it },
            modifier = Modifier.semantics { contentDescription = "连续滑块" },
        )
    }
    SampleSection(R.string.component_gallery_variant_discrete) {
        Text(
            text = "分段滑块 ${(stepped * 100).roundToInt()}%",
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
        SampleSlider(
            value = stepped,
            onValueChange = { stepped = it },
            steps = 3,
            modifier = Modifier.semantics { contentDescription = "分段滑块" },
        )
    }
}

@Composable
internal fun InputSamples(component: GalleryComponent, parentContainerColor: Color) {
    var showError by rememberSaveable { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    val colors = FilledTextFieldDefaults.colors(parentContainerColor)
    SampleSection(R.string.component_gallery_variant_states) {
        SampleRow {
            Text("错误状态", Modifier.align(Alignment.CenterVertically))
            Switch(
                checked = showError,
                onCheckedChange = { showError = it },
                modifier = Modifier.semantics { contentDescription = "输入框错误状态" },
            )
        }
        when (component) {
            GalleryComponent.TEXT_FIELD -> {
                FilledTextField(
                    value = value, onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(), colors = colors, label = { Text("普通输入框") },
                    errorMessage = if (showError) "输入内容有误" else null,
                )
                FilledTextField(
                    value = "禁用内容", onValueChange = {}, enabled = false,
                    modifier = Modifier.fillMaxWidth(), colors = colors, label = { Text("禁用输入框") },
                )
                FilledTextField(
                    value = "只读内容", onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth(), colors = colors, label = { Text("只读输入框") },
                )
            }
            GalleryComponent.PASSWORD -> {
                var visible by remember { mutableStateOf(false) }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) visible = false
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                PasswordTextField(
                    value = value, onValueChange = { value = it },
                    passwordVisible = visible, onPasswordVisibilityChange = { visible = !visible },
                    modifier = Modifier.fillMaxWidth(), colors = colors, label = { Text("密码") },
                    errorMessage = if (showError) "密码格式有误" else null,
                )
            }
            GalleryComponent.OTP -> OtpInputField(
                value = value, onValueChange = { value = it },
                label = "六位验证码", enabled = true, modifier = Modifier.fillMaxWidth(),
                error = if (showError) "验证码有误" else null, colors = colors,
            )
            else -> error("Not an input sample: $component")
        }
    }
}

@Composable
internal fun ProgressSamples() {
    var progress by rememberSaveable { mutableFloatStateOf(0.4f) }
    SampleSection(R.string.component_gallery_variant_indeterminate) {
        Text("40dp / 24dp / 20dp")
        SampleRow {
            IndeterminateCircularProgressIndicator(Modifier.size(40.dp).semantics { contentDescription = "页面加载" })
            IndeterminateCircularProgressIndicator(
                Modifier.size(24.dp).semantics { contentDescription = "图标加载" }, strokeWidth = 2.dp,
            )
            IndeterminateCircularProgressIndicator(
                Modifier.size(20.dp).semantics { contentDescription = "刷新加载" }, strokeWidth = 2.5.dp,
            )
        }
    }
    SampleSection(R.string.component_gallery_variant_determinate) {
        Text(
            text = "确定进度 ${(progress * 100).roundToInt()}%",
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(40.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        SampleSlider(
            value = progress,
            onValueChange = { progress = it },
            modifier = Modifier.semantics { contentDescription = "确定进度" },
        )
    }
}

@Composable
internal fun ConnectedListSamples() {
    var switched by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(false) }
    SampleSection(R.string.component_gallery_variant_single_item) {
        ConnectedListGroup {
            SegmentedListItem(
                shapes = connectedListItemShapes(0, 1),
                leadingContent = { SampleIcon() },
                supportingContent = { Text("说明文字") },
                content = { Text("单项列表") },
            )
        }
    }
    SampleSection(R.string.component_gallery_variant_grouped_items) {
        ConnectedListGroup {
            repeat(3) { index ->
                SegmentedListItem(
                    shapes = connectedListItemShapes(index, 3),
                    leadingContent = { SampleIcon() },
                    supportingContent = { Text("连接列表说明") },
                    trailingContent = {
                        when (index) {
                            0 -> Switch(
                                switched, { switched = it },
                                modifier = Modifier.semantics { contentDescription = "列表开关" },
                            )
                            1 -> Checkbox(
                                checked, { checked = it },
                                modifier = Modifier.semantics { contentDescription = "列表复选框" },
                            )
                            else -> Text("尾部文字")
                        }
                    },
                    content = { Text("连接条目 ${index + 1}") },
                )
            }
        }
    }
}

@Composable
internal fun ChipSamples(parentContainerColor: Color, onFeedback: (String) -> Unit) {
    val chipContainerColor = ThemeColorDefaults.controlContainerColor(parentContainerColor)
    var filter by rememberSaveable { mutableStateOf(false) }
    var chip by rememberSaveable { mutableStateOf(true) }
    SampleRow {
        AssistChip(
            onClick = { onFeedback("已点击辅助标签") },
            label = { Text("辅助标签") },
            shape = CircleShape,
            border = null,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = chipContainerColor,
                labelColor = MaterialTheme.colorScheme.onSurface,
                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                trailingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        SuggestionChip(
            onClick = { onFeedback("已点击建议标签") },
            label = { Text("建议标签") },
            shape = CircleShape,
            border = null,
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = chipContainerColor,
                labelColor = MaterialTheme.colorScheme.onSurface,
                iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        FilterChip(selected = filter, onClick = { filter = !filter },
            shape = CircleShape, border = null,
            label = { Text("筛选标签") }, colors = AppChipDefaults.filterColors(parentContainerColor))
        InputChip(selected = chip, onClick = { chip = !chip },
            shape = CircleShape, border = null,
            label = { Text("输入标签") }, colors = AppChipDefaults.inputColors(parentContainerColor))
        FilterChip(selected = true, onClick = {}, enabled = false, label = { Text("禁用标签") },
            shape = CircleShape, border = null)
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
    SmoothSlider(state = slider, onValueChange = onValueChange, modifier = modifier)
}
