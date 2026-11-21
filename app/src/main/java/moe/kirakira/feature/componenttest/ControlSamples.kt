package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import moe.kirakira.R
import moe.kirakira.ui.components.AppChipDefaults
import moe.kirakira.ui.components.CharacterCount
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.FilledTextField
import moe.kirakira.ui.components.FilledTextFieldDefaults
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.IndeterminateLinearProgressIndicator
import moe.kirakira.ui.components.OtpInputField
import moe.kirakira.ui.components.PasswordTextField
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.SmoothSlider
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.theme.ThemeColorDefaults

@Composable
internal fun SwitchSamples() {
    var checked by rememberSaveable { mutableStateOf(false) }
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

@Composable
internal fun CheckboxSamples() {
    var checked by rememberSaveable { mutableStateOf(false) }
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
        SamplePercent(continuous)
        SampleSlider(
            value = continuous,
            onValueChange = { continuous = it },
            modifier = Modifier.semantics { contentDescription = "连续滑块" },
        )
    }
    SampleSection(R.string.component_gallery_variant_discrete) {
        SamplePercent(stepped)
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
    val editor = remember(component) { TextFieldState() }
    val colors = FilledTextFieldDefaults.colors(parentContainerColor)
    if (component == GalleryComponent.TEXT_FIELD) {
        SampleSection(R.string.component_gallery_variant_editable) {
            InputErrorToggle(showError) { showError = it }
            FilledTextField(
                state = editor,
                inputTransformation = InputTransformation.byValue { _, proposed -> proposed.take(SAMPLE_TEXT_LIMIT) },
                modifier = Modifier.fillMaxWidth(),
                colors = colors,
                label = { Text("普通输入框") },
                errorMessage = if (showError) "输入内容有误" else null,
                supportingText = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showError) {
                            Text("输入内容有误", Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        CharacterCount(count = editor.text.length, limit = SAMPLE_TEXT_LIMIT)
                    }
                },
            )
        }
        SampleSection(R.string.component_gallery_variant_read_only_disabled) {
            FilledTextField(
                state = remember { TextFieldState("只读内容") }, readOnly = true,
                modifier = Modifier.fillMaxWidth(), colors = colors, label = { Text("只读输入框") },
            )
            FilledTextField(
                state = remember { TextFieldState("禁用内容") }, enabled = false,
                modifier = Modifier.fillMaxWidth(), colors = colors, label = { Text("禁用输入框") },
            )
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InputErrorToggle(showError) { showError = it }
            when (component) {
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
                        state = editor,
                        passwordVisible = visible, onPasswordVisibilityChange = { visible = !visible },
                        modifier = Modifier.fillMaxWidth(), colors = colors, label = { Text("密码") },
                        errorMessage = if (showError) "密码格式有误" else null,
                    )
                }
                GalleryComponent.OTP -> OtpInputField(
                    state = editor,
                    label = "六位验证码", enabled = true, modifier = Modifier.fillMaxWidth(),
                    error = if (showError) "验证码有误" else null, colors = colors,
                )
                else -> error("Not an input sample: $component")
            }
        }
    }
}

private const val SAMPLE_TEXT_LIMIT = 200

@Composable
private fun InputErrorToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.component_gallery_show_error), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun ProgressSamples() {
    var progress by rememberSaveable { mutableFloatStateOf(0.4f) }
    SampleSection(R.string.component_gallery_variant_indeterminate) {
        SampleRow {
            val sizes = listOf(40 to 4.dp, 24 to 2.dp, 20 to 2.5.dp)
            sizes.forEach { (size, strokeWidth) ->
                SampleLabeledItem(stringResource(R.string.component_gallery_size_dp, size), minPreviewHeight = 40.dp) {
                    IndeterminateCircularProgressIndicator(Modifier.size(size.dp), strokeWidth = strokeWidth)
                }
            }
        }
        IndeterminateLinearProgressIndicator(Modifier.fillMaxWidth())
    }
    SampleSection(R.string.component_gallery_variant_determinate) {
        SamplePercent(progress)
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
private fun SamplePercent(value: Float) {
    Text(
        text = stringResource(R.string.component_gallery_percent, (value * 100).roundToInt()),
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
