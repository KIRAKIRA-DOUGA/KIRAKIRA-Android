package moe.kirakira.navigation

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import moe.kirakira.feature.settings.search.SettingsSearchPage
import moe.kirakira.feature.settings.search.settingsSearchCatalog
import moe.kirakira.ui.adaptive.AppWindowLayout
import moe.kirakira.ui.navigation.SettingsPaneLayout

internal fun Density.settingsPaneLayout(
    window: AppWindowLayout,
    direction: LayoutDirection,
    useDesktopRail: Boolean,
): SettingsPaneLayout? {
    val minHeight = 480.dp.roundToPx()
    val directoryWidth = 320.dp.roundToPx()
    val railWidth = if (useDesktopRail) DesktopRailWidth.roundToPx() else 0
    if (window.primaryPane != null) {
        val panes = window.foldPanes?.takeIf { it.sideBySide } ?: return null
        val (start, end) = panes.within(window.safeBounds)
        if (minOf(start.height, end.height) < minHeight ||
            start.width < directoryWidth || end.width < 360.dp.roundToPx()
        ) return null
        return SettingsPaneLayout(
            directory = panes.first,
            detail = panes.second,
            rail = useDesktopRail && start.width >= directoryWidth + railWidth,
        )
    }
    if (window.safeBounds.width < 840.dp.roundToPx() || window.safeBounds.height < minHeight) return null
    val (directory, detail) = window.columns(directoryWidth + railWidth, direction, edgeToEdge = true)
    return SettingsPaneLayout(directory, detail, rail = useDesktopRail)
}

internal val SettingsSearchPage.settingsRoute: NavKey
    get() = when (this) {
        SettingsSearchPage.HOME -> SettingsRoute
        SettingsSearchPage.APPEARANCE -> AppearanceRoute
        SettingsSearchPage.LANGUAGE -> LanguageSettingsRoute
        SettingsSearchPage.PLAYBACK -> PlaybackSettingsRoute
        SettingsSearchPage.DANMAKU -> DanmakuSettingsRoute
        SettingsSearchPage.PROFILE -> ProfileEditorRoute
        SettingsSearchPage.PRIVACY -> PrivacySettingsRoute
        SettingsSearchPage.SECURITY -> SecuritySettingsRoute
        SettingsSearchPage.BLOCKING -> BlockingOverviewRoute
        SettingsSearchPage.INVITATIONS -> InvitationsRoute
        SettingsSearchPage.ABOUT -> AboutRoute
        SettingsSearchPage.ACCOUNT -> AccountSwitchRoute
    }

internal val NavKey.settingsCategory: SettingsSearchPage?
    get() = when (this) {
        is SettingsSearchTargetRoute -> settingsSearchCatalog.firstOrNull { it.id == entryId }?.page
        else -> SettingsSearchPage.entries.firstOrNull { it != SettingsSearchPage.HOME && it.settingsRoute == this }
    }

internal val NavKey.isSettingsDetail: Boolean
    get() = settingsCategory != null || this is RuleManagementRoute || this == LicensesRoute
