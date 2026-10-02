package moe.kirakira.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorSettings
import moe.kirakira.ui.theme.ThemeMode

@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    themeColors: ThemeColorSettings = ThemeColorSettings(),
    onThemeColorsChange: (ThemeColorSettings) -> Unit = {},
) {
    SettingsPage(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
        modifier = modifier.testTag("appearance_screen"),
        backButtonModifier = Modifier.testTag("appearance_back"),
    ) {
        SettingsSection(
            title = stringResource(R.string.theme_brightness),
            contentModifier = Modifier.selectableGroup(),
        ) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SettingsRadioItem(
                    title = stringResource(mode.titleRes),
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    index = index,
                    count = ThemeMode.entries.size,
                    icon = mode.iconRes(),
                )
            }
        }
        ThemeColorPicker(
            settings = themeColors,
            onSettingsChange = onThemeColorsChange,
        )
    }
}

private fun ThemeMode.iconRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.drawable.ic_symbol_brightness_auto
    ThemeMode.LIGHT -> R.drawable.ic_symbol_light_mode
    ThemeMode.DARK -> R.drawable.ic_symbol_dark_mode
}

@Preview(name = "Appearance · English", locale = "en", showBackground = true)
@Preview(name = "外观 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Appearance · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
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
