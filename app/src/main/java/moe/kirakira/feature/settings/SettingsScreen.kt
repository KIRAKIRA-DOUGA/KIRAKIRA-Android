package moe.kirakira.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.ui.adaptive.pageContentWidth
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.DestructiveActionIcon
import moe.kirakira.ui.components.FlatTextButton
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.PageTopAppBar
import moe.kirakira.ui.components.SectionHeader
import moe.kirakira.ui.components.SegmentedMenuItem
import moe.kirakira.ui.components.ShadingIcon
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.StatusBarScrollToTopEffect
import moe.kirakira.ui.components.topAppBarContentWidth
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorDefaults

private val categoryEntries = settingsSearchCatalog.filter { it.topLevel && it.page != SettingsSearchPage.HOME }
private val personalSettings = categoryEntries.filter { it.requiresAccount }
private val generalSettings = categoryEntries.filter { !it.requiresAccount && it.page != SettingsSearchPage.ACCOUNT }
private val accountSetting = categoryEntries.single { it.page == SettingsSearchPage.ACCOUNT }
private val directoryPages = (personalSettings + generalSettings + accountSetting).map { it.page }

internal val SettingsSearchPage.directoryIndex: Int?
    get() = directoryPages.indexOf(this).takeIf { it >= 0 }

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onSelectCategory: (SettingsSearchPage) -> Unit,
    modifier: Modifier = Modifier,
    twoPane: Boolean = false,
    selectedCategory: SettingsSearchPage? = null,
    signedIn: Boolean = false,
    accountBusy: Boolean = false,
    onLogout: () -> Unit = {},
    isActive: Boolean = true,
    onSearchDestination: (SettingsSearchEntry) -> Unit = {},
) {
    var confirmLogout by rememberSaveable(signedIn) { mutableStateOf(false) }
    if (confirmLogout && signedIn && isActive) {
        ActionDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { DestructiveActionIcon(R.drawable.ic_icon_logout) },
            title = { Text(stringResource(R.string.settings_log_out)) },
            text = { Text(stringResource(R.string.account_logout_message)) },
            actions = {
                ShadowButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                    enabled = !accountBusy,
                    destructive = true,
                ) {
                    Text(stringResource(R.string.settings_log_out))
                }
                FlatTextButton(
                    onClick = { confirmLogout = false },
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
    val listState = rememberLazyListState()
    StatusBarScrollToTopEffect(listState, enabled = isActive && selectedCategory == null)
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current

    SettingsSearchLayout(
        signedIn = signedIn,
        isActive = isActive,
        twoPane = twoPane,
        hasDetail = selectedCategory != null,
        modifier = modifier.testTag("settings_screen"),
        onSelect = { entry ->
            if (entry.page == SettingsSearchPage.HOME) {
                scope.launch { listState.animateScrollToItem(if (signedIn) 2 else 1) }
            } else onSearchDestination(entry)
        },
    ) { searchBar ->
        FrostedScaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ThemeColorDefaults.settingsBackgroundColor(),
            topBar = {
                Column {
                    Box {
                        ShadingIcon(
                            icon = R.drawable.ic_icon_settings,
                            modifier = Modifier.matchParentSize(),
                            rotating = true,
                            endPadding = 0.dp,
                            alignment = Alignment.BottomEnd,
                            offset = DpOffset(32.dp, 32.dp),
                        )
                        PageTopAppBar(
                            title = stringResource(R.string.me_settings),
                            onBack = onBack,
                            modifier = Modifier.topAppBarContentWidth(SettingsDefaults.MaxContentWidth),
                            backButtonModifier = Modifier.testTag("settings_back"),
                        )
                    }
                    searchBar()
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                    )
                    .consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .pageContentWidth(SettingsDefaults.MaxContentWidth)
                        .fillMaxSize()
                        .then(if (twoPane) Modifier.selectableGroup() else Modifier),
                    contentPadding = PaddingValues(
                        start = SettingsDefaults.HorizontalPadding,
                        end = SettingsDefaults.HorizontalPadding,
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
                                twoPane = twoPane,
                                selectedCategory = selectedCategory,
                                onSelect = onSelectCategory,
                            )
                        }
                    }
                    item(key = "general", contentType = "settings_group") {
                        SettingsGroup(
                            title = stringResource(R.string.settings_general),
                            entries = generalSettings,
                            twoPane = twoPane,
                            selectedCategory = selectedCategory,
                            onSelect = onSelectCategory,
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
                                    title = stringResource(accountSetting.title),
                                    icon = accountSetting.icon,
                                    index = 0,
                                    count = if (signedIn) 2 else 1,
                                    onClick = { onSelectCategory(accountSetting.page) },
                                    showChevron = !twoPane,
                                    selected = if (twoPane) selectedCategory == accountSetting.page else null,
                                )
                                if (signedIn) {
                                    SegmentedMenuItem(
                                        title = stringResource(R.string.settings_log_out),
                                        icon = R.drawable.ic_icon_logout,
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
}

@Composable
private fun SettingsGroup(
    title: String,
    entries: List<SettingsSearchEntry>,
    twoPane: Boolean,
    selectedCategory: SettingsSearchPage?,
    onSelect: (SettingsSearchPage) -> Unit,
    modifier: Modifier = Modifier,
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
                    onClick = { onSelect(entry.page) },
                    showChevron = !twoPane,
                    selected = if (twoPane) selectedCategory == entry.page else null,
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
            onSelectCategory = {},
        )
    }
}
