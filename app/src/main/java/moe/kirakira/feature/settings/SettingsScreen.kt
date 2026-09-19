package moe.kirakira.feature.settings

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.SegmentedMenuItem
import moe.kirakira.ui.theme.KIRAKIRATheme

private data class SettingsEntry(@param:StringRes val title: Int, @param:DrawableRes val icon: Int)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val personalSettings = remember {
        listOf(
            SettingsEntry(R.string.settings_profile, R.drawable.ic_symbol_person),
            SettingsEntry(R.string.settings_privacy, R.drawable.ic_symbol_shield),
            SettingsEntry(R.string.settings_security, R.drawable.ic_symbol_lock),
            SettingsEntry(R.string.settings_blocking, R.drawable.ic_symbol_block),
            SettingsEntry(R.string.settings_invitation_code, R.drawable.ic_symbol_confirmation_number),
        )
    }

    val generalSettings = remember {
        listOf(
            SettingsEntry(R.string.settings_appearance, R.drawable.ic_symbol_palette),
            SettingsEntry(R.string.settings_playback, R.drawable.ic_symbol_play_circle),
            SettingsEntry(R.string.settings_danmaku, R.drawable.ic_symbol_chat_bubble),
            SettingsEntry(R.string.settings_about, R.drawable.ic_symbol_info),
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.me_settings)) },
                navigationIcon = {
                    IconButton (
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back"),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_symbol_arrow_back),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SettingsGroup(stringResource(R.string.nav_me), personalSettings)
                SettingsGroup(
                    title = stringResource(R.string.settings_general),
                    entries = generalSettings,
                    onAboutClick = onNavigateToAbout,
                    onAppearanceClick = onNavigateToAppearance,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_account),
                        modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        SegmentedMenuItem(
                            title = stringResource(R.string.settings_switch_account),
                            icon = R.drawable.ic_symbol_switch_account,
                            index = 0,
                            count = 2,
                            onClick = onNavigateToAccount,
                        )
                        SegmentedMenuItem(
                            title = stringResource(R.string.settings_log_out),
                            icon = R.drawable.ic_symbol_logout,
                            index = 1,
                            count = 2,
                            showChevron = false,
                            destructive = true,
                            onClick = { /* TODO: Logout */ },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    entries: List<SettingsEntry>,
    modifier: Modifier = Modifier,
    onAboutClick: (() -> Unit)? = null,
    onAppearanceClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            entries.forEachIndexed { index, entry ->
                SegmentedMenuItem(
                    title = stringResource(entry.title),
                    icon = entry.icon,
                    index = index,
                    count = entries.size,
                    onClick = when (entry.title) {
                        R.string.settings_about -> onAboutClick
                        R.string.settings_appearance -> onAppearanceClick
                        else -> null
                    },
                )
            }
        }
    }
}

@Preview(name = "Settings · English", locale = "en", showBackground = true)
@Preview(name = "设置 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Settings · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Settings · Large text", fontScale = 2f, showBackground = true)
@Composable
private fun SettingsPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        SettingsScreen(
            onBack = {},
            onNavigateToAbout = {},
            onNavigateToAppearance = {},
            onNavigateToAccount = {},
        )
    }
}
