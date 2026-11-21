package moe.kirakira.feature.account

import android.content.res.Configuration
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import moe.kirakira.KIRAKIRAApp
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountSwitchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selection_updatesInPlaceAndSurvivesNavigation() {
        composeRule.setContent { AccountTestTheme { TestApp() } }
        openAccounts()
        composeRule.onNodeWithTag("nav_me").assertDoesNotExist()
        composeRule.onNodeWithTag("account_guest").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Current account"))
        val tags = listOf("account_guest", "account_demo_kirakira", "account_demo_sakura", "account_add")
        val positions = tags.map { composeRule.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
        assertTrue(positions.zipWithNext().all { (first, second) -> first < second })
        composeRule.onNodeWithTag("account_add").performClick()
        composeRule.onNodeWithText("Sign-in is not available yet.").assertIsDisplayed()
        composeRule.onNodeWithTag("account_demo_sakura").performClick().performClick().assertIsSelected()
        composeRule.onNodeWithTag("account_guest").assertIsNotSelected()
        composeRule.onNodeWithTag("account_demo_kirakira").assertIsNotSelected()
        composeRule.onNodeWithTag("account_switch_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("account_back").performClick()
        composeRule.onNodeWithText("Switch account").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("account_demo_sakura").assertIsSelected()
        Espresso.pressBack()
        composeRule.onNodeWithTag("settings_back").performClick()
        composeRule.onNodeWithTag("nav_me").assertIsSelected()
        composeRule.onNodeWithText("KIRAKIRA User").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editing_cancelAndConfirmRemoval_preservesOrFallsBackToGuest() {
        composeRule.setContent { AccountTestTheme { TestApp() } }
        openAccounts()
        composeRule.onNodeWithTag("account_demo_sakura").performClick()
        composeRule.onNodeWithTag("account_edit").performClick()
        composeRule.onNodeWithTag("account_guest").assertHasNoClickAction()
        composeRule.onNodeWithTag("account_demo_sakura").assertHasNoClickAction()
        composeRule.onNodeWithTag("account_add").assertIsNotEnabled()
        composeRule.onNodeWithTag("account_remove_guest").assertDoesNotExist()
        composeRule.onNodeWithTag("account_remove_demo_kirakira").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("account_demo_kirakira").assertIsDisplayed()
        composeRule.onNodeWithTag("account_remove_demo_kirakira").performClick()
        composeRule.onNodeWithTag("account_remove_confirm").performClick()
        composeRule.onNodeWithTag("account_demo_kirakira").assertDoesNotExist()
        composeRule.onNodeWithTag("account_demo_sakura").assertIsSelected()
        composeRule.onNodeWithTag("account_edit").performClick()
        composeRule.onNodeWithTag("account_remove_demo_sakura").assertDoesNotExist()
        composeRule.onNodeWithTag("account_edit").performClick()
        composeRule.onNodeWithTag("account_remove_demo_sakura").performClick()
        composeRule.onNodeWithTag("account_remove_confirm").performClick()
        composeRule.onNodeWithTag("account_guest").assertIsSelected()
        composeRule.onNodeWithTag("account_demo_sakura").assertDoesNotExist()
        composeRule.onNodeWithTag("account_edit").assertDoesNotExist()
        composeRule.onNodeWithTag("account_add").performClick()
        composeRule.onNodeWithText("Sign-in is not available yet.").assertIsDisplayed()
        composeRule.onNodeWithTag("account_back").performClick()
        composeRule.onNodeWithText("Switch account").performClick()
        composeRule.onNodeWithTag("account_demo_sakura").assertDoesNotExist()
        composeRule.onNodeWithTag("account_guest").assertIsSelected()
    }

    @Test
    fun restoration_keepsSelectionRemovalEditingAndPendingDialog() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { AccountTestTheme { TestApp() } }
        openAccounts()
        composeRule.onNodeWithTag("account_demo_sakura").performClick()
        composeRule.onNodeWithTag("account_edit").performClick()
        composeRule.onNodeWithTag("account_remove_demo_kirakira").performClick()
        composeRule.onNodeWithTag("account_remove_confirm").performClick()
        composeRule.onNodeWithTag("account_remove_demo_sakura").performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("account_remove_confirm").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("account_demo_sakura").assertIsSelected()
        composeRule.onNodeWithTag("account_demo_kirakira").assertDoesNotExist()
        composeRule.onNodeWithTag("account_remove_demo_sakura").assertIsDisplayed()
        composeRule.onNodeWithTag("account_back").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Switch account").assertIsDisplayed()
    }

    @Test
    fun navigation_repeatedClicksAndPredictiveBack_onlyPopOnePage() {
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { dispatcher = owner.onBackPressedDispatcher }
            AccountTestTheme { TestApp() }
        }
        openSettings()
        composeRule.onNodeWithText("Switch account").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { click -> click(); click() }
        composeRule.onNodeWithTag("account_demo_sakura").performClick()
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        composeRule.onNodeWithTag("account_demo_sakura").assertIsSelected()
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_RIGHT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_RIGHT))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Switch account").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("account_back").performSemanticsAction(SemanticsActions.OnClick) { click ->
            click()
            click()
        }
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("nav_me").assertIsSelected()
    }

    @Test
    fun chinese_darkNarrowLargeText_keepsActionsReachable() {
        composeRule.setContent {
            AccountTestTheme(language = "zh", darkTheme = true, fontScale = 2f) {
                TestApp(modifier = Modifier.width(320.dp))
            }
        }
        openSettings(label = "设置")
        composeRule.onNodeWithText("切换账户").performScrollTo().performClick()
        composeRule.onNodeWithText("游客").assertIsDisplayed()
        composeRule.onNodeWithTag("account_demo_sakura").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag("account_edit").performClick()
        composeRule.onNodeWithContentDescription("移除 小樱（演示）").performScrollTo().performClick()
        composeRule.onNodeWithText("取消").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("完成").performClick()
        composeRule.onNodeWithTag("account_add").performScrollTo().performClick()
        composeRule.onNodeWithText("登录功能尚未接入").assertIsDisplayed()
    }

    @Test
    fun longNames_dynamicColorLargeText_keepsRemoveTargetInsideRow() {
        composeRule.setContent {
            AccountTestTheme(fontScale = 2f, dynamicColor = true) {
                AccountSwitchScreen(
                    accounts = listOf(
                        AccountItem(GUEST_ACCOUNT_ID, "Guest"),
                        AccountItem("long", "A very long demonstration account name", "@long_sample_handle"),
                    ),
                    selectedAccountId = "long",
                    editing = true,
                    snackbarHostState = remember { SnackbarHostState() },
                    onSelectAccount = {},
                    onEditingChange = {},
                    onAddAccount = {},
                    onRemoveAccount = {},
                    onBack = {},
                    modifier = Modifier.width(320.dp),
                )
            }
        }
        val row = composeRule.onNodeWithTag("account_long").performScrollTo().getUnclippedBoundsInRoot()
        val remove = composeRule.onNodeWithTag("account_remove_long").assertIsDisplayed()
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp).getUnclippedBoundsInRoot()
        assertTrue(remove.left >= row.left && remove.right <= row.right)
        composeRule.onNodeWithTag("account_add").performScrollTo().assertIsDisplayed()
    }

    private fun openSettings(label: String = "Settings") {
        composeRule.onNodeWithTag("nav_me").performClick()
        composeRule.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun openAccounts() {
        openSettings()
        composeRule.onNodeWithText("Switch account").performScrollTo().performClick()
    }
}

@Composable
private fun TestApp(modifier: Modifier = Modifier) {
    KIRAKIRAApp(themeMode = ThemeMode.SYSTEM, onThemeModeChange = {}, modifier = modifier)
}

@Composable
private fun AccountTestTheme(
    language: String = "en",
    darkTheme: Boolean = false,
    fontScale: Float = 1f,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
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
        LocalResources provides localizedContext.resources,
        LocalDensity provides Density(density, fontScale),
    ) {
        KIRAKIRATheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
    }
}
