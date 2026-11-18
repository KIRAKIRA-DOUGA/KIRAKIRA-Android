package moe.kirakira.feature.componenttest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.ui.components.DestructiveTextButton
import moe.kirakira.ui.components.FlatTextButton
import moe.kirakira.ui.components.FollowButton
import moe.kirakira.ui.components.Segmented
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.ShadowFilledIconButton
import moe.kirakira.ui.components.ShadowFilledTonalButton
import moe.kirakira.ui.components.ShadowFilledTonalIconButton
import moe.kirakira.ui.components.ShadowFloatingActionButton
import moe.kirakira.ui.components.ShadowToggleButton
import moe.kirakira.ui.components.pressToRoundButtonShapes

@Composable
internal fun TextButtonSamples(onFeedback: (String) -> Unit) {
    val click = { onFeedback("已点击按钮") }
    SampleSection(R.string.component_gallery_variant_styles) {
        Text("带文字按钮：按压变形")
        SampleRow {
            ShadowButton(onClick = click) { Text("实心按钮") }
            ShadowFilledTonalButton(onClick = click) { Text("浅色按钮") }
            OutlinedButton(onClick = click, shapes = pressToRoundButtonShapes()) { Text("描边按钮") }
            ShadowButton(onClick = click, destructive = true) { Text("危险操作") }
        }
        Text("平面文字按钮：按压变形")
        SampleRow {
            FlatTextButton(onClick = click) { Text("文字按钮") }
            DestructiveTextButton(onClick = click) { Text("危险文字按钮") }
        }
    }
    SampleSection(R.string.component_gallery_variant_disabled_loading) {
        SampleRow {
            ShadowButton(onClick = {}, enabled = false) { Text("禁用按钮") }
            ShadowFilledTonalButton(onClick = {}, enabled = false) { Text("禁用浅色按钮") }
            OutlinedButton(onClick = {}, enabled = false, shapes = pressToRoundButtonShapes()) { Text("禁用描边按钮") }
            ShadowButton(onClick = {}, enabled = false, destructive = true) { Text("禁用危险操作") }
            FlatTextButton(onClick = {}, enabled = false) { Text("禁用文字按钮") }
        }
        SampleRow {
            ShadowButton(onClick = {}, loading = true) { Text("提交") }
            ShadowFilledTonalButton(onClick = {}, loading = true) { Text("确认") }
            FlatTextButton(onClick = {}, loading = true) { Text("保存") }
            ShadowFilledIconButton(onClick = {}, loading = true) {
                Icon(painterResource(R.drawable.ic_icon_send), "发送")
            }
        }
    }
    SampleSection(R.string.component_gallery_variant_sizes) {
        Text("Small / Medium / Large")
        SampleRow {
            listOf(ButtonDefaults.MinHeight, ButtonDefaults.MediumContainerHeight, ButtonDefaults.LargeContainerHeight)
                .zip(listOf("Small", "Medium", "Large"))
                .forEach { (height, label) ->
                    ShadowButton(
                        onClick = click,
                        modifier = Modifier.heightIn(min = height),
                        shapes = pressToRoundButtonShapes(height),
                        contentPadding = ButtonDefaults.contentPaddingFor(height),
                    ) {
                        Text(label, style = ButtonDefaults.textStyleFor(height))
                    }
                    FlatTextButton(
                        onClick = click,
                        modifier = Modifier.heightIn(min = height),
                        shapes = pressToRoundButtonShapes(height),
                        contentPadding = ButtonDefaults.contentPaddingFor(height),
                    ) {
                        Text(label, style = ButtonDefaults.textStyleFor(height))
                    }
                }
        }
    }
}

@Composable
internal fun IconButtonSamples(onFeedback: (String) -> Unit) {
    val click = { onFeedback("已点击按钮") }
    SampleSection(R.string.component_gallery_variant_styles) {
        Text("图标按钮：固定圆形")
        SampleRow {
            ShadowFilledIconButton(onClick = click) { SampleIcon(description = "实心图标按钮") }
            ShadowFilledTonalIconButton(onClick = click) { SampleIcon(description = "浅色图标按钮") }
            OutlinedIconButton(onClick = click, shape = CircleShape) {
                SampleIcon(description = "描边图标按钮")
            }
            IconButton(onClick = click) { SampleIcon(description = "普通图标按钮") }
            ShadowFilledTonalIconButton(
                onClick = click,
                shapes = IconButtonShapes(IconButtonDefaults.filledShape, IconButtonDefaults.filledShape),
            ) { SampleIcon(description = "固定轮廓工具按钮") }
        }
    }
    SampleSection(R.string.component_gallery_variant_states) {
        SampleRow {
            ShadowFilledIconButton(onClick = {}, enabled = false) { SampleIcon(description = "禁用实心图标按钮") }
            ShadowFilledTonalIconButton(onClick = {}, enabled = false) { SampleIcon(description = "禁用浅色图标按钮") }
            IconButton(onClick = {}, enabled = false) { SampleIcon(description = "禁用裸图标按钮") }
        }
    }
}

@Composable
internal fun FabSample(onFeedback: (String) -> Unit) {
    ShadowFloatingActionButton(onClick = { onFeedback("已点击添加") }) { SampleIcon(description = "添加") }
}

@Composable
internal fun ToggleButtonSamples() {
    var toggled by rememberSaveable { mutableStateOf(false) }
    var toggledWithIcon by rememberSaveable { mutableStateOf(false) }
    var iconToggled by rememberSaveable { mutableStateOf(false) }
    SampleSection(R.string.component_gallery_variant_states) {
        SampleRow {
            ShadowToggleButton(checked = toggled, onCheckedChange = { toggled = it }) { checked ->
                Text(if (checked) "已切换" else "切换按钮")
            }
            ShadowToggleButton(checked = false, onCheckedChange = {}, enabled = false) { Text("禁用未选中") }
            ShadowToggleButton(checked = true, onCheckedChange = {}, enabled = false) { Text("禁用已选中") }
        }
    }
    SampleSection(R.string.component_gallery_variant_icon_and_text) {
        val label = stringResource(R.string.video_save)
        SampleRow {
            ShadowToggleButton(
                checked = toggledWithIcon,
                onCheckedChange = { toggledWithIcon = it },
                icon = { ToggleSampleIcon() },
            ) { Text(label) }
            ShadowToggleButton(
                checked = false,
                onCheckedChange = {},
                enabled = false,
                icon = { ToggleSampleIcon() },
            ) { Text("禁用未选中") }
            ShadowToggleButton(
                checked = true,
                onCheckedChange = {},
                enabled = false,
                icon = { ToggleSampleIcon() },
            ) { Text("禁用已选中") }
        }
    }
    SampleSection(R.string.component_gallery_variant_icon_only) {
        val label = stringResource(R.string.video_save)
        SampleRow {
            FilledIconToggleButton(checked = iconToggled, onCheckedChange = { iconToggled = it }) {
                ToggleSampleIcon(label)
            }
            FilledIconToggleButton(checked = false, onCheckedChange = {}, enabled = false) {
                ToggleSampleIcon(label)
            }
            FilledIconToggleButton(checked = true, onCheckedChange = {}, enabled = false) {
                ToggleSampleIcon(label)
            }
        }
    }
    LoadingButtonSamples { checked, loading, onChange ->
        ShadowToggleButton(checked = checked, loading = loading, onCheckedChange = onChange) {
            Text(if (it) "已切换" else "切换按钮")
        }
    }
}

@Composable
private fun ToggleSampleIcon(contentDescription: String? = null) {
    Icon(painterResource(R.drawable.ic_icon_star), contentDescription = contentDescription)
}

@Composable
internal fun FollowButtonSamples() {
    var following by rememberSaveable { mutableStateOf(false) }
    SampleSection(R.string.component_gallery_variant_states) {
        SampleRow {
            FollowButton(following = following, onFollowingChange = { following = it })
            FollowButton(following = true, onFollowingChange = {}, enabled = false)
            FollowButton(following = false, onFollowingChange = {}, enabled = false)
        }
    }
    LoadingButtonSamples { checked, loading, onChange ->
        FollowButton(following = checked, loading = loading, onFollowingChange = onChange)
    }
}

@Composable
private fun LoadingButtonSamples(content: @Composable (Boolean, Boolean, (Boolean) -> Unit) -> Unit) {
    SampleSection(R.string.component_gallery_variant_after_loading) {
        SimulatedLoadingButton(updateDuringLoading = false, content = content)
    }
    SampleSection(R.string.component_gallery_variant_during_loading) {
        SimulatedLoadingButton(updateDuringLoading = true, content = content)
    }
}

@Composable
private fun SimulatedLoadingButton(
    updateDuringLoading: Boolean,
    content: @Composable (Boolean, Boolean, (Boolean) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var checked by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    SampleRow {
        content(checked, loading) { target ->
            if (!loading) {
                loading = true
                scope.launch {
                    try {
                        delay(600)
                        if (updateDuringLoading) checked = target
                        delay(600)
                        if (!updateDuringLoading) checked = target
                    } finally {
                        loading = false
                    }
                }
            }
        }
    }
}

@Composable
internal fun SegmentedSample(parentContainerColor: Color, modifier: Modifier = Modifier) {
    var enabled by rememberSaveable { mutableStateOf(true) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SampleRow {
            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("启用选择组")
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            FlatTextButton(onClick = { selected = null }, enabled = selected != null) { Text("清空选择") }
        }
        Segmented(
            parentContainerColor = parentContainerColor,
            items = listOf("列表", "网格", "磁贴"),
            selected = selected,
            onSelectedChange = { selected = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        ) { label ->
            Text(label)
        }
    }
}
