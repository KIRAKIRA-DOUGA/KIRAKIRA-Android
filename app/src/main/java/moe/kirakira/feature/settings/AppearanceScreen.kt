package moe.kirakira.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemeMode
import moe.kirakira.ui.theme.ThemePresetColor
import moe.kirakira.ui.theme.wallpaperAccentColor
import kotlin.math.floor

private sealed interface AppearanceColorOption {
    val key: String

    data class Preset(val preset: ThemePresetColor) : AppearanceColorOption {
        override val key = preset.name
    }

    data class Wallpaper(val color: Color) : AppearanceColorOption {
        override val key = "wallpaper"
    }

    data object Custom : AppearanceColorOption {
        override val key = "custom"
    }
}

@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    themeColors: ThemeColorSettings = ThemeColorSettings(),
    onThemeColorsChange: (ThemeColorSettings) -> Unit = {},
    predictiveBackEnabled: Boolean = false,
    onPredictiveBackEnabledChange: (Boolean) -> Unit = {},
) {
    var showCustomColor by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val direction = LocalLayoutDirection.current
    val fontScale = LocalDensity.current.fontScale
    val wallpaperColor = wallpaperAccentColor()
    val usesSystemColors = themeColors.useSystemColors && wallpaperColor != null
    val modes = remember { listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK) }
    val colorOptions = remember(wallpaperColor) {
        buildList<AppearanceColorOption> {
            ThemePresetColor.entries.forEach { add(AppearanceColorOption.Preset(it)) }
            wallpaperColor?.let { add(AppearanceColorOption.Wallpaper(it)) }
            add(AppearanceColorOption.Custom)
        }
    }
    SettingsScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
        shadingIcon = R.drawable.ic_symbol_palette,
        modifier = modifier.testTag("appearance_screen"),
        backButtonModifier = Modifier.testTag("appearance_back"),
    ) { padding ->
        val start = padding.calculateStartPadding(direction) + SettingsDefaults.HorizontalPadding
        val end = padding.calculateEndPadding(direction) + SettingsDefaults.HorizontalPadding
        Box(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            BoxWithConstraints(
                modifier = Modifier.widthIn(max = SettingsDefaults.MaxContentWidth).fillMaxSize(),
            ) {
                val contentWidth = maxWidth - start - end
                val modeColumns = themeCardColumns(contentWidth, 100.dp, fontScale, modes.size)
                val colorColumns = themeCardColumns(contentWidth, 156.dp, fontScale, colorOptions.size)
                val modeRows = remember(modes, modeColumns) { modes.chunked(modeColumns) }
                val colorRows = remember(colorOptions, colorColumns) { colorOptions.chunked(colorColumns) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = start,
                        end = end,
                        top = padding.calculateTopPadding() + SettingsDefaults.TopPadding,
                        bottom = padding.calculateBottomPadding() + SettingsDefaults.BottomPadding,
                    ),
                ) {
                    item(key = "brightness_header", contentType = "header") {
                        SettingsSectionHeader(
                            stringResource(R.string.theme_brightness),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    itemsIndexed(
                        modeRows,
                        key = { _, row -> "brightness_${row.first().name}" },
                        contentType = { _, _ -> "brightness_row" },
                    ) { index, row ->
                        ThemeCardRow(
                            items = row,
                            columns = modeColumns,
                            itemKey = { it.name },
                            modifier = Modifier.padding(
                                bottom = if (index == modeRows.lastIndex) SettingsDefaults.SectionSpacing else 12.dp,
                            ),
                        ) { mode, cardModifier ->
                            ThemeSelectionCard(
                                title = stringResource(mode.titleRes),
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                compact = true,
                                modifier = cardModifier,
                            ) {
                                Icon(
                                    painter = painterResource(mode.iconRes()),
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                                )
                            }
                        }
                    }
                    item(key = "colors_header", contentType = "header") {
                        SettingsSectionHeader(
                            stringResource(R.string.theme_colors),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    itemsIndexed(
                        colorRows,
                        key = { _, row -> "colors_${row.first().key}" },
                        contentType = { _, _ -> "color_row" },
                    ) { index, row ->
                        ThemeCardRow(
                            items = row,
                            columns = colorColumns,
                            itemKey = { it.key },
                            modifier = Modifier.padding(
                                bottom = if (index == colorRows.lastIndex) SettingsDefaults.SectionSpacing else 12.dp,
                            ),
                        ) { option, cardModifier ->
                            when (option) {
                                is AppearanceColorOption.Preset -> ThemeColorCard(
                                    preset = option.preset,
                                    selected = !usesSystemColors && !themeColors.useCustomColor &&
                                        themeColors.seedColorArgb == option.preset.color.toArgb(),
                                    onClick = { onThemeColorsChange(themeColors.selectPreset(option.preset)) },
                                    modifier = cardModifier,
                                )
                                is AppearanceColorOption.Wallpaper -> ThemeColorSourceCard(
                                    title = stringResource(R.string.theme_color_wallpaper),
                                    color = option.color,
                                    selected = usesSystemColors,
                                    onClick = { onThemeColorsChange(themeColors.selectWallpaperColor()) },
                                    icon = R.drawable.ic_symbol_wallpaper,
                                    modifier = cardModifier,
                                )
                                AppearanceColorOption.Custom -> ThemeColorSourceCard(
                                    title = stringResource(R.string.theme_color_custom),
                                    color = Color(themeColors.customColorArgb),
                                    selected = !usesSystemColors && themeColors.useCustomColor,
                                    onClick = { showCustomColor = true },
                                    icon = R.drawable.ic_symbol_edit,
                                    modifier = cardModifier,
                                )
                            }
                        }
                    }
                    item(key = "animations", contentType = "settings_group") {
                        SettingsSection(stringResource(R.string.settings_section_animations)) {
                            SettingsSwitchItem(
                                title = stringResource(R.string.settings_predictive_back),
                                checked = predictiveBackEnabled,
                                onCheckedChange = onPredictiveBackEnabledChange,
                                index = 0,
                                count = 1,
                                icon = R.drawable.ic_symbol_arrow_back,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showCustomColor) {
        CustomColorDialog(
            seedColorArgb = themeColors.customColorArgb,
            onDismiss = { showCustomColor = false },
            onConfirm = { argb ->
                onThemeColorsChange(themeColors.selectCustomColor(argb))
                showCustomColor = false
            },
        )
    }
}

private fun ThemeMode.iconRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.drawable.ic_symbol_light_mode
    ThemeMode.DARK -> R.drawable.ic_symbol_dark_mode
    ThemeMode.SYSTEM -> R.drawable.ic_symbol_brightness_auto
}

private fun themeCardColumns(width: Dp, minWidth: Dp, fontScale: Float, count: Int): Int =
    floor((width + 12.dp) / (minWidth * fontScale.coerceAtLeast(1f) + 12.dp)).toInt().coerceIn(1, count)

@Preview(name = "Appearance · English", locale = "en", showBackground = true)
@Preview(name = "外观 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Appearance · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Appearance · Narrow", locale = "en", widthDp = 320, heightDp = 800, showBackground = true)
@Preview(name = "外观 · 宽屏", locale = "zh", widthDp = 640, heightDp = 900, showBackground = true)
@Preview(
    name = "Appearance · Large font",
    locale = "en",
    widthDp = 320,
    heightDp = 900,
    fontScale = 2f,
    showBackground = true,
)
@Preview(
    name = "外观 · 大字体",
    locale = "zh",
    widthDp = 640,
    heightDp = 900,
    fontScale = 2f,
    showBackground = true,
)
@Composable
private fun AppearancePreview() {
    KIRAKIRATheme(dynamicColor = false) {
        AppearanceScreen(
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
            onBack = {},
            themeColors = ThemeColorSettings(useSystemColors = false),
        )
    }
}
