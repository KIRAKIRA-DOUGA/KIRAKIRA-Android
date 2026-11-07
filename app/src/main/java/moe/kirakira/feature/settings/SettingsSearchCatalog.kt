package moe.kirakira.feature.settings

import android.content.res.Resources
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import moe.kirakira.R
import moe.kirakira.ui.theme.ThemePresetColor

internal enum class SettingsSearchPage {
    HOME, APPEARANCE, PLAYBACK, DANMAKU, PROFILE, PRIVACY, SECURITY, BLOCKING, INVITATIONS, ABOUT, ACCOUNT,
}

internal data class SettingsSearchEntry(
    val id: String,
    @param:StringRes val title: Int,
    val page: SettingsSearchPage,
    @param:StringRes val category: Int,
    @param:DrawableRes val icon: Int,
    val requiresAccount: Boolean = false,
    val topLevel: Boolean = false,
    val minimumSdk: Int = 27,
)

internal data class SettingsSearchResult(val entry: SettingsSearchEntry, val title: String, val path: List<String>)

/** Static labels only: account values and remotely loaded content never enter this directory. */
internal val settingsSearchCatalog: List<SettingsSearchEntry> = buildList {
    fun page(id: String, title: Int, destination: SettingsSearchPage, icon: Int, account: Boolean = false) {
        add(SettingsSearchEntry(id, title, destination, R.string.me_settings, icon, account, topLevel = true))
    }
    fun items(destination: SettingsSearchPage, category: Int, icon: Int, account: Boolean, vararg labels: Pair<String, Int>) {
        labels.forEach { (id, title) -> add(SettingsSearchEntry(id, title, destination, category, icon, account)) }
    }
    page("profile", R.string.settings_profile, SettingsSearchPage.PROFILE, R.drawable.ic_icon_badge, true)
    items(SettingsSearchPage.PROFILE, R.string.settings_profile, R.drawable.ic_icon_badge, true,
        "profile.avatar" to R.string.profile_edit_avatar,
        "profile.username" to R.string.auth_username,
        "profile.nickname" to R.string.auth_nickname,
        "profile.signature" to R.string.profile_bio,
        "profile.birthday" to R.string.profile_birthday,
        "profile.gender" to R.string.profile_gender,
        "profile.labels" to R.string.profile_add_label,
    )
    page("privacy", R.string.settings_privacy, SettingsSearchPage.PRIVACY, R.drawable.ic_icon_shield, true)
    items(SettingsSearchPage.PRIVACY, R.string.settings_privacy, R.drawable.ic_icon_shield, true,
        "privacy.all" to R.string.privacy_set_all,
        "privacy.birthday" to R.string.profile_birthday,
        "privacy.age" to R.string.privacy_age,
        "privacy.following" to R.string.me_following_count,
        "privacy.followers" to R.string.me_followers_count,
        "privacy.favorites" to R.string.me_favorites,
    )
    page("security", R.string.settings_security, SettingsSearchPage.SECURITY, R.drawable.ic_icon_lock, true)
    items(SettingsSearchPage.SECURITY, R.string.settings_security, R.drawable.ic_icon_lock, true,
        "security.email" to R.string.auth_email,
        "security.password" to R.string.security_password,
        "security.two_factor" to R.string.security_two_factor_section,
    )
    page("blocking", R.string.settings_blocking, SettingsSearchPage.BLOCKING, R.drawable.ic_icon_block, true)
    items(SettingsSearchPage.BLOCKING, R.string.settings_blocking, R.drawable.ic_icon_block, true,
        "blocking.users" to R.string.management_blocked_users,
        "blocking.hidden" to R.string.management_hidden_users,
        "blocking.tags" to R.string.management_tags,
        "blocking.keywords" to R.string.management_keywords,
        "blocking.regex" to R.string.management_regex,
    )
    page("invitations", R.string.settings_invitation_code, SettingsSearchPage.INVITATIONS,
        R.drawable.ic_icon_gift, true)
    page("appearance", R.string.settings_appearance, SettingsSearchPage.APPEARANCE, R.drawable.ic_icon_palette)
    items(SettingsSearchPage.APPEARANCE, R.string.settings_appearance, R.drawable.ic_icon_palette, false,
        "appearance.brightness" to R.string.theme_brightness,
        "appearance.system" to R.string.theme_system,
        "appearance.light" to R.string.theme_light,
        "appearance.dark" to R.string.theme_dark,
        "appearance.colors" to R.string.theme_colors,
        "appearance.custom" to R.string.theme_color_custom,
        "appearance.predictive_back" to R.string.settings_predictive_back,
    )
    ThemePresetColor.entries.forEach { preset ->
        add(SettingsSearchEntry("appearance.color.${preset.name}", preset.titleRes, SettingsSearchPage.APPEARANCE,
            R.string.settings_appearance, R.drawable.ic_icon_palette))
    }
    add(SettingsSearchEntry("appearance.system_accent", R.string.theme_color_system_accent, SettingsSearchPage.APPEARANCE,
        R.string.settings_appearance, R.drawable.ic_symbol_android, minimumSdk = 31))
    page("playback", R.string.settings_playback, SettingsSearchPage.PLAYBACK, R.drawable.ic_icon_play)
    items(SettingsSearchPage.PLAYBACK, R.string.settings_playback, R.drawable.ic_icon_play, false,
        "playback.in_app" to R.string.settings_in_app_mini_player,
        "playback.outside_app" to R.string.settings_outside_app_mini_player,
        "playback.autoplay" to R.string.settings_autoplay,
    )
    page("danmaku", R.string.settings_danmaku, SettingsSearchPage.DANMAKU, R.drawable.ic_custom_danmaku)
    items(SettingsSearchPage.DANMAKU, R.string.settings_danmaku, R.drawable.ic_custom_danmaku, false,
        "danmaku.display" to R.string.danmaku_display,
        "danmaku.opacity" to R.string.danmaku_opacity,
        "danmaku.font" to R.string.danmaku_font_scale,
        "danmaku.area" to R.string.danmaku_area,
        "danmaku.speed" to R.string.danmaku_speed,
        "danmaku.rtl" to R.string.danmaku_style_rtl,
        "danmaku.top" to R.string.danmaku_style_top,
        "danmaku.bottom" to R.string.danmaku_style_bottom,
        "danmaku.ltr" to R.string.danmaku_style_ltr,
    )
    page("about", R.string.settings_about, SettingsSearchPage.ABOUT, R.drawable.ic_icon_info)
    items(SettingsSearchPage.ABOUT, R.string.settings_about, R.drawable.ic_icon_info, false,
        "about.github" to R.string.about_github,
        "about.licenses" to R.string.about_licenses,
    )
    page("account", R.string.settings_switch_account, SettingsSearchPage.ACCOUNT, R.drawable.ic_symbol_switch_account)
    page("logout", R.string.settings_log_out, SettingsSearchPage.HOME, R.drawable.ic_icon_logout, true)
}

internal fun SettingsSearchEntry.isVisible(signedIn: Boolean): Boolean =
    (!requiresAccount || signedIn) && Build.VERSION.SDK_INT >= minimumSdk

internal fun searchSettings(resources: Resources, query: String, signedIn: Boolean): List<SettingsSearchResult> {
    val rootTitle = resources.getString(R.string.me_settings)
    val normalized = query.trim()
    if (normalized.isEmpty()) return emptyList()
    val words = normalized.split(Regex("\\s+")).filter(String::isNotEmpty)
    return settingsSearchCatalog.asSequence()
        .filter { it.isVisible(signedIn) }
        .map {
            val path = if (it.category == R.string.me_settings) emptyList()
                else listOf(resources.getString(it.category))
            SettingsSearchResult(it, resources.getString(it.title), path)
        }
        .filter { result -> words.all { word ->
            result.title.contains(word, ignoreCase = true) ||
                result.path.any { it.contains(word, ignoreCase = true) } || rootTitle.contains(word, ignoreCase = true)
        } }
        .sortedBy { result -> when {
            result.title.equals(normalized, ignoreCase = true) -> 0
            words.all { result.title.contains(it, ignoreCase = true) } -> 1
            else -> 2
        } }
        .toList()
}
