package moe.kirakira.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun DanmakuSettingsScreen(
    settings: DanmakuSettings?,
    onChange: (DanmakuSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = settings ?: DanmakuSettings()
    val ready = settings != null
    SettingsPage(title = stringResource(R.string.settings_danmaku), onBack = onBack, modifier = modifier) {
        SettingsMasterSwitchItem(
            title = stringResource(R.string.danmaku_display),
            checked = value.enabled,
            onCheckedChange = { onChange(value.copy(enabled = it)) },
            icon = R.drawable.ic_custom_danmaku,
            enabled = ready,
        )
        SettingsSection(stringResource(R.string.danmaku_section_display)) {
            SettingsSliderItem(
                title = stringResource(R.string.danmaku_opacity),
                value = value.opacityPercent, range = 10..100, step = 5,
                valueLabel = stringResource(R.string.danmaku_percent, value.opacityPercent),
                onValueChange = { onChange(value.copy(opacityPercent = it)) },
                index = 0, count = 4, icon = R.drawable.ic_symbol_opacity, enabled = ready,
            )
            SettingsSliderItem(
                title = stringResource(R.string.danmaku_font_scale),
                value = value.fontScalePercent, range = 50..200, step = 5,
                valueLabel = stringResource(R.string.danmaku_percent, value.fontScalePercent),
                onValueChange = { onChange(value.copy(fontScalePercent = it)) },
                index = 1, count = 4, icon = R.drawable.ic_symbol_format_size, enabled = ready,
            )
            SettingsSliderItem(
                title = stringResource(R.string.danmaku_area),
                value = value.areaPercent, range = 25..100, step = 25,
                valueLabel = stringResource(R.string.danmaku_percent, value.areaPercent),
                onValueChange = { onChange(value.copy(areaPercent = it)) },
                index = 2, count = 4, icon = R.drawable.ic_symbol_fit_screen, enabled = ready,
            )
            SettingsSliderItem(
                title = stringResource(R.string.danmaku_speed),
                value = value.speedTenths, range = 5..20, step = 1,
                valueLabel = stringResource(R.string.danmaku_speed_value, value.speedTenths / 10f),
                onValueChange = { onChange(value.copy(speedTenths = it)) },
                index = 3, count = 4, icon = R.drawable.ic_symbol_speed, enabled = ready,
            )
        }
        SettingsSection(stringResource(R.string.danmaku_section_types)) {
            SettingsSwitchItem(
                stringResource(R.string.danmaku_style_rtl), value.showRtl, { onChange(value.copy(showRtl = it)) },
                0, 4, icon = R.drawable.ic_symbol_west, enabled = ready,
            )
            SettingsSwitchItem(
                stringResource(R.string.danmaku_style_top), value.showTop, { onChange(value.copy(showTop = it)) },
                1, 4, icon = R.drawable.ic_symbol_vertical_align_top, enabled = ready,
            )
            SettingsSwitchItem(
                stringResource(R.string.danmaku_style_bottom), value.showBottom,
                { onChange(value.copy(showBottom = it)) },
                2, 4, icon = R.drawable.ic_symbol_vertical_align_bottom, enabled = ready,
            )
            SettingsSwitchItem(
                stringResource(R.string.danmaku_style_ltr), value.showLtr, { onChange(value.copy(showLtr = it)) },
                3, 4, icon = R.drawable.ic_symbol_east, enabled = ready,
            )
        }
    }
}

@Preview(locale = "zh", showBackground = true)
@Preview(locale = "en", showBackground = true)
@Composable
private fun DanmakuSettingsPreview() {
    KIRAKIRATheme { DanmakuSettingsScreen(DanmakuSettings(), {}, {}) }
}
