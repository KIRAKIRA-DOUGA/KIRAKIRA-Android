package moe.kirakira.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun PlaybackSettingsScreen(
    settings: PlaybackSettings?,
    onOutsideAppMiniPlayerChange: (Boolean) -> Unit,
    onInAppMiniPlayerChange: (Boolean) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = settings ?: PlaybackSettings()
    val ready = settings != null
    SettingsPage(title = stringResource(R.string.settings_playback), onBack = onBack, modifier = modifier) {
        SettingsSection(stringResource(R.string.settings_section_mini_player)) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_in_app_mini_player),
                checked = values.inAppMiniPlayer,
                onCheckedChange = onInAppMiniPlayerChange,
                index = 0,
                count = 2,
                icon = R.drawable.ic_symbol_picture_in_picture_alt,
                enabled = ready,
            )
            SettingsSwitchItem(
                title = stringResource(R.string.settings_outside_app_mini_player),
                checked = values.outsideAppMiniPlayer,
                onCheckedChange = onOutsideAppMiniPlayerChange,
                index = 1,
                count = 2,
                icon = R.drawable.ic_symbol_pip,
                enabled = ready,
            )
        }
        SettingsSection(stringResource(R.string.settings_playback)) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_autoplay),
                checked = values.autoplay,
                onCheckedChange = onAutoplayChange,
                index = 0,
                count = 1,
                icon = R.drawable.ic_symbol_autoplay,
                enabled = ready,
            )
        }
    }
}

@Preview(locale = "zh", showBackground = true)
@Preview(locale = "en", showBackground = true)
@Composable
private fun PlaybackSettingsPreview() {
    KIRAKIRATheme { PlaybackSettingsScreen(PlaybackSettings(), {}, {}, {}, {}) }
}
