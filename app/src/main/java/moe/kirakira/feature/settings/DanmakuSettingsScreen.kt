package moe.kirakira.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import moe.kirakira.R
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun DanmakuSettingsScreen(
    settings: DanmakuSettings?,
    onChange: (DanmakuSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberCollapsibleTopAppBarScrollBehavior()
    val direction = LocalLayoutDirection.current
    val value = settings ?: DanmakuSettings()
    val ready = settings != null
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            CollapsibleTopAppBar(stringResource(R.string.settings_danmaku), onBack, scrollBehavior = scroll)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(
                        start = padding.calculateStartPadding(direction) + 16.dp,
                        end = padding.calculateEndPadding(direction) + 16.dp,
                        top = 16.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                DanmakuToggle(R.string.danmaku_display, value.enabled, ready, 0, 1) { onChange(value.copy(enabled = it)) }
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    DanmakuSlider(R.string.danmaku_opacity, value.opacityPercent, 10..100, 5, ready, 0,
                        stringResource(R.string.danmaku_percent, value.opacityPercent)) { onChange(value.copy(opacityPercent = it)) }
                    DanmakuSlider(R.string.danmaku_font_scale, value.fontScalePercent, 50..200, 5, ready, 1,
                        stringResource(R.string.danmaku_percent, value.fontScalePercent)) { onChange(value.copy(fontScalePercent = it)) }
                    DanmakuSlider(R.string.danmaku_area, value.areaPercent, 25..100, 25, ready, 2,
                        stringResource(R.string.danmaku_percent, value.areaPercent)) { onChange(value.copy(areaPercent = it)) }
                    DanmakuSlider(R.string.danmaku_speed, value.speedTenths, 5..20, 1, ready, 3,
                        stringResource(R.string.danmaku_speed_value, value.speedTenths / 10f)) { onChange(value.copy(speedTenths = it)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    DanmakuToggle(R.string.danmaku_style_rtl, value.showRtl, ready, 0, 4) { onChange(value.copy(showRtl = it)) }
                    DanmakuToggle(R.string.danmaku_style_top, value.showTop, ready, 1, 4) { onChange(value.copy(showTop = it)) }
                    DanmakuToggle(R.string.danmaku_style_bottom, value.showBottom, ready, 2, 4) { onChange(value.copy(showBottom = it)) }
                    DanmakuToggle(R.string.danmaku_style_ltr, value.showLtr, ready, 3, 4) { onChange(value.copy(showLtr = it)) }
                }
            }
        }
    }
}

@Composable
private fun DanmakuToggle(label: Int, checked: Boolean, enabled: Boolean, index: Int, count: Int, onChange: (Boolean) -> Unit) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Switch },
        trailingContent = { Switch(checked, onCheckedChange = null, enabled = enabled) },
        content = { Text(stringResource(label)) },
    )
}

@Composable
private fun DanmakuSlider(
    label: Int,
    value: Int,
    range: IntRange,
    step: Int,
    enabled: Boolean,
    index: Int,
    formatted: String,
    onChange: (Int) -> Unit,
) {
    val title = stringResource(label)
    val slider = rememberSliderState(
        steps = (range.last - range.first) / step - 1,
        trackRange = range.first.toFloat()..range.last.toFloat(),
    )
    SideEffect { slider.value = value.toFloat() }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, 4),
        content = { Text(title) },
        trailingContent = { Text(formatted) },
        supportingContent = {
            Slider(
                state = slider,
                onValueChange = { onChange((it / step).roundToInt() * step) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = title
                    stateDescription = formatted
                },
            )
        },
    )
}

@Preview(locale = "zh", showBackground = true)
@Preview(locale = "en", showBackground = true)
@Composable
private fun DanmakuSettingsPreview() {
    KIRAKIRATheme { DanmakuSettingsScreen(DanmakuSettings(), {}, {}) }
}
