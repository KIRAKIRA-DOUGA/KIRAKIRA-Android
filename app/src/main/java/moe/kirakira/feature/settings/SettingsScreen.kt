package moe.kirakira.feature.settings

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.SectionHeader
import moe.kirakira.ui.components.SegmentedMenuItem
import moe.kirakira.ui.components.ShadingIcon
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorDefaults

private data class SettingsEntry(@param:StringRes val title: Int, @param:DrawableRes val icon: Int)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToAccount: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToPlayback: () -> Unit = {},
    onNavigateToDanmaku: () -> Unit = {},
    onNavigateToBlocking: () -> Unit = {},
    onNavigateToInvitations: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    signedIn: Boolean = false,
    accountBusy: Boolean = false,
    onLogout: () -> Unit = {},
) {
    var confirmLogout by rememberSaveable(signedIn) { mutableStateOf(false) }
    if (confirmLogout && signedIn) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.settings_log_out)) },
            text = { Text(stringResource(R.string.account_logout_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                    enabled = !accountBusy,
                ) {
                    Text(stringResource(R.string.settings_log_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.account_cancel)) }
            },
        )
    }
    val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()
    val listState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current

    val personalSettings = remember {
        listOf(
            SettingsEntry(R.string.settings_profile, R.drawable.ic_symbol_badge),
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
            SettingsEntry(R.string.settings_danmaku, R.drawable.ic_custom_danmaku),
            SettingsEntry(R.string.settings_about, R.drawable.ic_symbol_info),
        )
    }

    FrostedScaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("settings_screen"),
        containerColor = ThemeColorDefaults.settingsBackgroundColor(),
        topBar = {
            Box {
                ShadingIcon(
                    icon = R.drawable.ic_symbol_settings,
                    modifier = Modifier.matchParentSize(),
                    rotating = true,
                    endPadding = 0.dp,
                    alignment = Alignment.BottomEnd,
                    offset = DpOffset(32.dp, 32.dp),
                )
                CollapsibleTopAppBar(
                    title = stringResource(R.string.me_settings),
                    onBack = onBack,
                    scrollBehavior = scrollBehavior,
                    backButtonModifier = Modifier.testTag("settings_back"),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (signedIn) {
                    item(key = "personal", contentType = "settings_group") {
                        SettingsGroup(
                            title = stringResource(R.string.nav_me),
                            entries = personalSettings,
                            onBlockingClick = onNavigateToBlocking,
                            onInvitationsClick = onNavigateToInvitations,
                            onProfileClick = onNavigateToProfile,
                            onPrivacyClick = onNavigateToPrivacy,
                            onSecurityClick = onNavigateToSecurity,
                        )
                    }
                }
                item(key = "general", contentType = "settings_group") {
                    SettingsGroup(
                        title = stringResource(R.string.settings_general),
                        entries = generalSettings,
                        onAboutClick = onNavigateToAbout,
                        onAppearanceClick = onNavigateToAppearance,
                        onPlaybackClick = onNavigateToPlayback,
                        onDanmakuClick = onNavigateToDanmaku,
                    )
                }
                item(key = "account", contentType = "settings_group") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.settings_account),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        ConnectedListGroup {
                            SegmentedMenuItem(
                                title = stringResource(R.string.settings_switch_account),
                                icon = R.drawable.ic_symbol_switch_account,
                                index = 0,
                                count = if (signedIn) 2 else 1,
                                onClick = onNavigateToAccount,
                            )
                            if (signedIn) {
                                SegmentedMenuItem(
                                    title = stringResource(R.string.settings_log_out),
                                    icon = R.drawable.ic_symbol_logout,
                                    index = 1,
                                    count = 2,
                                    showChevron = false,
                                    destructive = true,
                                    onClick = if (!accountBusy) ({ confirmLogout = true }) else null,
                                )
                            }
                        }
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
    onPlaybackClick: (() -> Unit)? = null,
    onDanmakuClick: (() -> Unit)? = null,
    onBlockingClick: (() -> Unit)? = null,
    onInvitationsClick: (() -> Unit)? = null,
    onAboutClick: (() -> Unit)? = null,
    onAppearanceClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    onPrivacyClick: (() -> Unit)? = null,
    onSecurityClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = title,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ConnectedListGroup {
            entries.forEachIndexed { index, entry ->
                SegmentedMenuItem(
                    title = stringResource(entry.title),
                    icon = entry.icon,
                    index = index,
                    count = entries.size,
                    onClick = when (entry.title) {
                        R.string.settings_profile -> onProfileClick
                        R.string.settings_privacy -> onPrivacyClick
                        R.string.settings_security -> onSecurityClick
                        R.string.settings_blocking -> onBlockingClick
                        R.string.settings_invitation_code -> onInvitationsClick
                        R.string.settings_playback -> onPlaybackClick
                        R.string.settings_danmaku -> onDanmakuClick
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
