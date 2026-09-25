package moe.kirakira.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.SectionHeader
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemePresetColor

@Composable
internal fun AppearanceSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        content()
    }
}

@Composable
internal fun ThemeColorPicker(
    settings: ThemeColorSettings,
    onSettingsChange: (ThemeColorSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomColor by rememberSaveable { mutableStateOf(false) }
    val supportsSystemColors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val usesSystemColors = settings.useSystemColors && supportsSystemColors

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        AppearanceSection(title = stringResource(R.string.theme_colors)) {
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                ColorSourceItem(
                    title = stringResource(R.string.theme_colors_system),
                    isSelected = usesSystemColors,
                    enabled = supportsSystemColors,
                    index = 0,
                    onClick = { onSettingsChange(settings.copy(useSystemColors = true)) },
                )
                ColorSourceItem(
                    title = stringResource(R.string.theme_colors_manual),
                    isSelected = !usesSystemColors,
                    enabled = true,
                    index = 1,
                    onClick = { onSettingsChange(settings.copy(useSystemColors = false)) },
                )
            }
        }

        if (!usesSystemColors) {
            AppearanceSection(title = stringResource(R.string.theme_color_scheme)) {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        content = {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectableGroup(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                items(
                                    items = ThemePresetColor.entries,
                                    key = { it.name },
                                ) { preset ->
                                    ThemePaletteOption(
                                        seedColor = preset.color,
                                        algorithm = settings.algorithm,
                                        label = stringResource(preset.titleRes),
                                        selected = !settings.useCustomColor &&
                                            settings.seedColorArgb == preset.color.toArgb(),
                                        onClick = { onSettingsChange(settings.selectPreset(preset)) },
                                    )
                                }
                                item(key = "custom") {
                                    ThemePaletteOption(
                                        seedColor = Color(settings.customColorArgb),
                                        algorithm = settings.algorithm,
                                        label = stringResource(R.string.theme_color_custom),
                                        selected = settings.useCustomColor,
                                        onClick = { showCustomColor = true },
                                    )
                                }
                            }
                        },
                    )

                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        content = {
                            ThemeAlgorithmPicker(
                                selectedAlgorithm = settings.algorithm,
                                seedColor = Color(settings.seedColorArgb),
                                onSelect = { algorithm ->
                                    onSettingsChange(settings.copy(algorithm = algorithm))
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    if (showCustomColor) {
        CustomColorDialog(
            seedColorArgb = settings.customColorArgb,
            algorithm = settings.algorithm,
            onDismiss = { showCustomColor = false },
            onConfirm = { argb ->
                onSettingsChange(settings.selectCustomColor(argb))
                showCustomColor = false
            },
        )
    }
}

@Composable
private fun ColorSourceItem(
    title: String,
    isSelected: Boolean,
    enabled: Boolean,
    index: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = 2),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                selected = isSelected
            },
        trailingContent = {
            RadioButton(
                selected = isSelected,
                onClick = null,
                enabled = enabled,
            )
        },
        content = { Text(title) },
    )
}
