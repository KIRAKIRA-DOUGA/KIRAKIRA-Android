package moe.kirakira

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import moe.kirakira.ui.theme.KIRAKIRATheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigation_english_showsEachDestination() {
        composeRule.setContent { LocalizedApp(language = "en") }

        composeRule.onNodeWithTag("nav_home").assertIsSelected().assertTextContains("Home")
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_search").assertTextContains("Search").performClick()
        composeRule.onNodeWithText("Search KIRAKIRA").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_following").assertTextContains("Following").performClick()
        composeRule.onNodeWithTag("following_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_me").assertTextContains("Me").performClick()
        composeRule.onNodeWithText("KIRAKIRA User").assertIsDisplayed()
        composeRule.onNodeWithText("History").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Favorites").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("nav_home").performClick().assertIsSelected()
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
    }

    @Test
    fun navigation_chinese_usesTranslatedLabelsAndContent() {
        composeRule.setContent { LocalizedApp(language = "zh") }

        composeRule.onNodeWithTag("nav_home").assertTextContains("首页")
        composeRule.onNodeWithTag("nav_search").assertTextContains("搜索").performClick()
        composeRule.onNodeWithText("搜索 KIRAKIRA").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_following").assertTextContains("关注").performClick()
        composeRule.onNodeWithTag("following_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_me").assertTextContains("我").performClick()
        composeRule.onNodeWithText("KIRAKIRA 用户").assertIsDisplayed()
        composeRule.onNodeWithText("个人主页").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("历史记录").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("收藏").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("设置").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun navigation_stateRestored_keepsSelectedTab() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { LocalizedApp(language = "en") }
        composeRule.onNodeWithTag("nav_me").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("nav_me").assertIsSelected()
        composeRule.onNodeWithTag("me_screen").assertIsDisplayed()
    }

    @Test
    fun me_darkThemeAndLargeText_keepsMenuReachable() {
        composeRule.setContent { LocalizedApp(language = "en", darkTheme = true, fontScale = 2f) }
        composeRule.onNodeWithTag("nav_me").performClick()
        composeRule.onNodeWithText("KIRAKIRA User").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("nav_home").performClick().assertIsSelected()
    }

    @Test
    fun settings_english_showsAllPlaceholdersAndReturnsToMe() {
        composeRule.setContent { LocalizedApp(language = "en") }
        openSettings()
        composeRule.onNodeWithTag("nav_me").assertDoesNotExist()
        listOf(
            "Profile", "Privacy", "Security", "Blocked and hidden", "Invitation code",
            "General", "Appearance", "Playback", "Danmaku", "About", "Switch account", "Log out",
        ).forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasNoClickAction()
        }
        composeRule.onNodeWithTag("settings_back").performClick()
        composeRule.onNodeWithTag("nav_me").assertIsSelected()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun settings_chineseDarkLargeText_scrollsAndHandlesSystemBack() {
        composeRule.setContent { LocalizedApp(language = "zh", darkTheme = true, fontScale = 2f) }
        openSettings(label = "设置")
        listOf("资料", "隐私", "安全", "屏蔽与隐藏", "邀请码", "常规", "外观", "播放", "弹幕", "关于", "切换账户", "登出")
            .forEach { label ->
                composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed().assertHasNoClickAction()
            }
        Espresso.pressBack()
        composeRule.onNodeWithTag("nav_me").assertIsSelected()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun settings_stateRestored_keepsPageAndReturnDestination() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { LocalizedApp(language = "en") }
        openSettings()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_back").performClick()
        composeRule.onNodeWithTag("nav_me").assertIsSelected()
    }

    private fun openSettings(label: String = "Settings") {
        composeRule.onNodeWithTag("nav_me").performClick()
        composeRule.onNodeWithText(label).performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

}

@Composable
private fun LocalizedApp(language: String, darkTheme: Boolean = false, fontScale: Float = 1f) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val configuration = Configuration(LocalConfiguration.current).apply {
        setLocale(Locale.forLanguageTag(language))
        this.fontScale = fontScale
    }
    val localizedContext = remember(context, language, fontScale) {
        context.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        LocalDensity provides Density(density, fontScale),
    ) {
        KIRAKIRATheme(darkTheme = darkTheme, dynamicColor = false) { KIRAKIRAApp() }
    }
}
