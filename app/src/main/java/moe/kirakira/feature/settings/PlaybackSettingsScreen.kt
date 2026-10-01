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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
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
    val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()
    val direction = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            CollapsibleTopAppBar(
                title = stringResource(R.string.settings_playback),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
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
                val values = settings ?: PlaybackSettings()
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    PlaybackSetting(
                        R.string.settings_in_app_mini_player, values.inAppMiniPlayer, settings != null,
                        0, 2, onInAppMiniPlayerChange,
                    )
                    PlaybackSetting(
                        R.string.settings_outside_app_mini_player, values.outsideAppMiniPlayer, settings != null,
                        1, 2, onOutsideAppMiniPlayerChange,
                    )
                }
                PlaybackSetting(R.string.settings_autoplay, values.autoplay, settings != null, 0, 1, onAutoplayChange)
            }
        }
    }
}

@Composable
private fun PlaybackSetting(
    label: Int,
    checked: Boolean,
    enabled: Boolean,
    index: Int,
    count: Int,
    onChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = { onChange(!checked) },
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = Modifier.fillMaxWidth().semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        content = { Text(stringResource(label)) },
    )
}

@Preview(locale = "zh", showBackground = true)
@Preview(locale = "en", showBackground = true)
@Composable
private fun PlaybackSettingsPreview() {
    KIRAKIRATheme { PlaybackSettingsScreen(PlaybackSettings(), {}, {}, {}, {}) }
}
