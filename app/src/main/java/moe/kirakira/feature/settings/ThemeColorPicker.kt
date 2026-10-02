package moe.kirakira.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.connectedListItemShadow
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemePresetColor
import moe.kirakira.ui.theme.wallpaperAccentColor

@Composable
internal fun ThemeColorPicker(
    settings: ThemeColorSettings,
    onSettingsChange: (ThemeColorSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomColor by rememberSaveable { mutableStateOf(false) }
    val wallpaperColor = wallpaperAccentColor()
    val usesSystemColors = settings.useSystemColors && wallpaperColor != null

    SettingsSection(title = stringResource(R.string.theme_colors), modifier = modifier) {
        SegmentedListItem(
            shapes = connectedListItemShapes(index = 0, count = 1),
            modifier = Modifier.connectedListItemShadow(index = 0, count = 1),
            contentPadding = PaddingValues(vertical = 12.dp),
            content = {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(items = ThemePresetColor.entries, key = { it.name }) { preset ->
                        ThemePaletteOption(
                            seedColor = preset.color,
                            label = stringResource(preset.titleRes),
                            selected = !usesSystemColors && !settings.useCustomColor &&
                                settings.seedColorArgb == preset.color.toArgb(),
                            onClick = { onSettingsChange(settings.selectPreset(preset)) },
                        )
                    }
                    if (wallpaperColor != null) {
                        item(key = "wallpaper") {
                            ThemePaletteOption(
                                seedColor = wallpaperColor,
                                label = stringResource(R.string.theme_color_wallpaper),
                                selected = usesSystemColors,
                                onClick = { onSettingsChange(settings.selectWallpaperColor()) },
                                width = 80.dp,
                            )
                        }
                    }
                    item(key = "custom") {
                        ThemePaletteOption(
                            seedColor = Color(settings.customColorArgb),
                            label = stringResource(R.string.theme_color_custom),
                            selected = !usesSystemColors && settings.useCustomColor,
                            onClick = { showCustomColor = true },
                        )
                    }
                }
            },
        )
    }

    if (showCustomColor) {
        CustomColorDialog(
            seedColorArgb = settings.customColorArgb,
            onDismiss = { showCustomColor = false },
            onConfirm = { argb ->
                onSettingsChange(settings.selectCustomColor(argb))
                showCustomColor = false
            },
        )
    }
}
