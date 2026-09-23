package moe.kirakira.feature.settings

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikepenz.aboutlibraries.Libs
import java.util.Locale
import moe.kirakira.KIRAKIRAApp
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicensesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun licenses_english_sheetBackKeepsListThenReturnsToAbout() {
        composeRule.setContent { LocalizedLicensesApp() }
        openLicenses()
        composeRule.onNodeWithText("Open source components").assertIsDisplayed()
        scrollToLibrary("Material Symbols")
        composeRule.onNodeWithText("Material Symbols").performClick()
        composeRule.onNodeWithText("Source: https://github.com/google/material-design-icons", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("View license").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithText("View license").assertDoesNotExist()
        composeRule.onNodeWithText("Material Symbols").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("about_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("about_back").performClick()
        composeRule.onNodeWithTag("settings_back").performClick()
        composeRule.onNodeWithTag("nav_me").assertIsSelected()
    }

    @Test
    fun licenses_chineseDarkLargeText_readsBundledAospNotice() {
        composeRule.setContent { LocalizedLicensesApp(language = "zh", darkTheme = true, fontScale = 2f) }
        openLicenses(language = "zh")
        composeRule.onNodeWithText("开源组件").assertIsDisplayed()
        scrollToLibrary("AOSP page transitions")
        composeRule.onNodeWithText("AOSP page transitions").performClick()
        composeRule.onNodeWithText("Copyright (C) 2024 The Android Open Source Project", substring = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("查看许可证").performScrollTo().assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("licenses_back").performClick()
        composeRule.onNodeWithTag("about_screen").assertIsDisplayed()
    }

    @Test
    fun licenses_stateRestored_keepsSheetAndListScrollAndNestedBackStack() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { LocalizedLicensesApp() }
        openLicenses()
        composeRule.onNodeWithText("Open source components").assertIsDisplayed()
        scrollToLibrary("Material Symbols")
        composeRule.onNodeWithText("Material Symbols").performClick()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText("View license")).fetchSemanticsNodes().isNotEmpty()
        }
        Espresso.pressBack()
        composeRule.onNodeWithText("Material Symbols").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        awaitList()
        composeRule.onNodeWithText("Material Symbols").assertIsDisplayed()
        composeRule.onNodeWithTag("licenses_back").performClick()
        composeRule.onNodeWithTag("about_screen").assertIsDisplayed()
    }

    @Test
    fun licenses_repeatedClicks_doNotDuplicateOrSkipPages() {
        composeRule.setContent { LocalizedLicensesApp() }
        openAbout()
        composeRule.onNodeWithTag("about_licenses").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { click ->
                click()
                click()
            }
        awaitList()
        composeRule.onNodeWithTag("licenses_back").performSemanticsAction(SemanticsActions.OnClick) { click ->
            click()
            click()
        }
        composeRule.onNodeWithTag("about_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("about_back").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun licenses_predictiveBack_cancelPreservesPageAndCommitReturnsOneLevel() {
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { dispatcher = owner.onBackPressedDispatcher }
            LocalizedLicensesApp()
        }
        openLicenses()
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        composeRule.onNodeWithTag("licenses_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("about_screen").assertDoesNotExist()
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_RIGHT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_RIGHT))
        }
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        composeRule.onNodeWithTag("about_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("licenses_screen").assertDoesNotExist()
    }

    @Test
    fun components_listDrawsBehindSystemBars_lastItemRemainsAboveNavigationBar() {
        var bottomInsetDp = 0f
        composeRule.setContent {
            val density = LocalDensity.current
            val inset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }
            SideEffect { bottomInsetDp = inset }
            LocalizedLicensesApp()
        }
        openLicenses()
        val screen = composeRule.onNodeWithTag("licenses_screen").getUnclippedBoundsInRoot()
        val list = composeRule.onNodeWithTag("licenses_list").getUnclippedBoundsInRoot()
        assertEquals(screen.top.value, list.top.value, 0.5f)
        assertEquals(screen.bottom.value, list.bottom.value, 0.5f)

        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val libraries = Libs.Builder().withJson(
            resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() },
        ).build().libraries
        composeRule.onNodeWithTag("licenses_list").performScrollToIndex(libraries.lastIndex)
        val lastItem = composeRule.onNodeWithText(libraries.last().name)
        lastItem.assertIsDisplayed()
        assertTrue(lastItem.getUnclippedBoundsInRoot().bottom.value <= screen.bottom.value - bottomInsetDp)
    }

    @Test
    fun components_sheetActions_openMatchingLinks() {
        val opened = mutableListOf<String>()
        val libraries = Libs.Builder().withJson(
            """
            {
              "libraries": [{
                "uniqueId": "sample:component", "name": "Sample component", "artifactVersion": "1.0",
                "description": "Sample description", "website": "https://example.com/component",
                "scm": {"url": "https://example.com/source"}, "licenses": ["sample-license"]
              }],
              "licenses": {"sample-license": {
                "name": "Sample license", "url": "https://example.com/license", "content": "Sample license body"
              }}
            }
            """.trimIndent(),
        ).build()
        composeRule.setContent {
            KIRAKIRATheme(dynamicColor = false) {
                LicensesContent(state = LicensesUiState.Ready(libraries), onBack = {}, onRetry = {})
            }
        }
        composeRule.onNodeWithText("Sample component").performClick()
        composeRule.onNodeWithText("Sample description").assertIsDisplayed()
        composeRule.onNodeWithText("Sample license body").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // The sheet has its own Android window; intercept outgoing intents instead of a composition local.
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                opened.add(requireNotNull(intent.dataString))
                return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            listOf(R.string.licenses_source, R.string.licenses_website, R.string.licenses_view_license).forEach {
                composeRule.onNodeWithText(context.getString(it)).performClick()
            }
            composeRule.runOnIdle {
                assertEquals(
                    listOf("https://example.com/source", "https://example.com/component", "https://example.com/license"),
                    opened,
                )
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun licenses_loadingFailureAndRetry_showDistinctStates() {
        var state by mutableStateOf<LicensesUiState>(LicensesUiState.Loading)
        composeRule.setContent {
            KIRAKIRATheme(dynamicColor = false) {
                LicensesContent(
                    state = state,
                    onBack = {},
                    onRetry = { state = LicensesUiState.Ready(Libs(emptyList(), emptySet())) },
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        ).assertIsDisplayed()
        composeRule.runOnIdle { state = LicensesUiState.Failed }
        composeRule.onNodeWithText(context.getString(R.string.licenses_error)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.licenses_retry)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.licenses_empty)).assertIsDisplayed()
    }

    @Test
    fun packagedLicenses_haveTextAndManualNoticesWithoutTestDependencies() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val json = resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        val libs = Libs.Builder().withJson(json).build()
        assertTrue(libs.libraries.isNotEmpty())
        libs.libraries.forEach { library ->
            assertTrue("Missing license: ${library.uniqueId}", library.licenses.isNotEmpty())
            library.licenses.forEach { license ->
                assertTrue("Missing text: ${library.uniqueId}", !license.licenseContent.isNullOrBlank())
            }
        }
        assertTrue(libs.libraries.any { it.uniqueId == "third-party:material-symbols" })
        assertTrue(libs.libraries.any { it.uniqueId == "third-party:aosp-motion" })
        assertTrue(libs.libraries.none { it.uniqueId.startsWith("junit:") || it.uniqueId.startsWith("androidx.test:") })
    }

    private fun openAbout(language: String = "en") {
        composeRule.onNodeWithTag("nav_me").performClick()
        composeRule.onNodeWithText(if (language == "zh") "设置" else "Settings").performScrollTo().performClick()
        composeRule.onNodeWithText(if (language == "zh") "关于" else "About").performScrollTo().performClick()
    }

    private fun openLicenses(language: String = "en") {
        openAbout(language)
        composeRule.onNodeWithTag("about_licenses").performScrollTo().performClick()
        awaitList()
    }

    private fun awaitList() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasTestTag("licenses_list"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("licenses_list").assertIsDisplayed()
    }

    private fun scrollToLibrary(name: String) {
        composeRule.onNodeWithTag("licenses_list").performScrollToNode(hasText(name))
    }
}

@Composable
private fun LocalizedLicensesApp(language: String = "en", darkTheme: Boolean = false, fontScale: Float = 1f) {
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
        KIRAKIRATheme(darkTheme = darkTheme, dynamicColor = false) {
            KIRAKIRAApp(themeMode = ThemeMode.SYSTEM, onThemeModeChange = {})
        }
    }
}
