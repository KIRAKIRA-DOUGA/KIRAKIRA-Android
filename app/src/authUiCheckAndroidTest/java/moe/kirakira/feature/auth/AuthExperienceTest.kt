package moe.kirakira.feature.auth

import android.Manifest
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.kirakira.BuildConfig
import moe.kirakira.R
import moe.kirakira.core.credentials.PasswordSelection
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.SessionOperation
import moe.kirakira.data.auth.SessionOperationType
import moe.kirakira.data.auth.StoredSessions
import moe.kirakira.feature.account.AccountItem
import moe.kirakira.feature.account.AccountSwitchScreen
import moe.kirakira.feature.account.GUEST_ACCOUNT_ID
import moe.kirakira.feature.account.SessionViewModel
import moe.kirakira.testing.AuthFixture
import moe.kirakira.testing.AuthUiCheckActivity
import moe.kirakira.testing.FakePasswordCredentialGateway
import moe.kirakira.testing.ScriptedApi
import moe.kirakira.testing.loginResponse
import moe.kirakira.testing.profileResponse
import moe.kirakira.testing.savedAccount
import moe.kirakira.ui.navigation.ActivityNavDisplay
import moe.kirakira.ui.navigation.MainRoute
import moe.kirakira.ui.navigation.NavigationPage
import moe.kirakira.ui.theme.KIRAKIRATheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AuthExperienceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: AuthFixture
    private lateinit var gateway: FakePasswordCredentialGateway
    private var activity: ActivityScenario<AuthUiCheckActivity>? = null
    private var closed = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("moe.kirakira.authuicheck", context.packageName)
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
        assertFalse(BuildConfig.SYSTEM_CREDENTIALS_ENABLED)
        fixture = AuthFixture(Dispatchers.Default)
        runBlocking { fixture.repository.initialize() }
        gateway = FakePasswordCredentialGateway()
    }

    @After
    fun tearDown() {
        activity?.close()
        AuthUiCheckActivity.content = null
        fixture.transport.cancelAll()
    }

    @Test
    fun fourSteps_forwardFocusAndKeyboard_backKeepsDraft_andSuccessReturnsToSource() {
        launchAuth()
        compose.onNodeWithTag("auth_register").performScrollTo().performClick()
        field(R.string.auth_username).assertIsFocused().performTextInput("Fixture User")
        fixture.transport.respond("""{"success":true,"isAvailableUsername":true}""")
        submit()
        compose.onNodeWithTag("auth_email").assertIsFocused().performTextInput("user1@example.invalid")
        compose.onNodeWithTag("auth_password").performTextInput("fixture-password")
        field(R.string.auth_confirm_password).performScrollTo().performTextInput("fixture-password")
        capture("credentials")
        fixture.transport.respond("""{"success":true,"exists":false}""")
        submit()
        field(R.string.auth_invitation).assertIsFocused().performTextInput("KIRA-AAAA-BBBB")
        compose.waitUntil(5_000) { imeVisible() }
        capture("invitation-ime")
        compose.onNodeWithTag("auth_back").performClick()
        compose.onNodeWithTag("auth_password").assertTextContains("fixture-password")
        field(R.string.auth_confirm_password).assertTextContains("fixture-password")
        compose.waitUntil(5_000) { !imeVisible() }
        fixture.transport.respond("""{"success":true,"exists":false}""")
        submit()
        field(R.string.auth_invitation).assertTextContains("KIRA-AAAA-BBBB")
        fixture.transport.respond("""{"success":true,"isAvailableInvitationCode":true}""")
        fixture.transport.respond("""{"success":true}""")
        submit()
        field(R.string.auth_verification_code).assertIsFocused()
        field(R.string.auth_confirm_password).assertDoesNotExist()
        assertEquals(0, gateway.saveCount)
        field(R.string.auth_verification_code).performTextInput("123456")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        submit()
        compose.waitUntil(5_000) { closed }
        assertEquals(1, gateway.saveCount)
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
        compose.onNodeWithTag("auth_REGISTER_VERIFY").assertDoesNotExist()
    }

    @Test
    fun picker_cancelAndRotation_requestAutomaticallyOnlyOnce() {
        launchAuth("user1@example.invalid")
        compose.waitUntil(5_000) { gateway.requestedEmails.size == 1 }
        assertEquals(listOf("user1@example.invalid"), gateway.requestedEmails)
        activity?.recreate()
        compose.waitForIdle()
        assertEquals(1, gateway.requestedEmails.size)
        compose.onNodeWithTag("auth_saved_password").performScrollTo().performClick()
        compose.waitUntil(5_000) { gateway.requestedEmails.size == 2 }
    }

    @Test
    fun picker_resultFromDestroyedActivity_isIgnored() {
        val result = CompletableDeferred<PasswordSelection>()
        gateway.getResult = { withContext(NonCancellable) { result.await() } }
        launchAuth()
        compose.waitUntil(5_000) { gateway.requestedEmails.size == 1 }
        activity?.recreate()
        compose.onNodeWithTag("auth_email").performTextInput("current@example.invalid")
        result.complete(PasswordSelection.Selected(moe.kirakira.core.credentials.PasswordDraft("old@example.invalid", "old")))
        compose.waitForIdle()
        compose.onNodeWithTag("auth_email").assertTextContains("current@example.invalid")
        assertTrue(fixture.transport.requests.isEmpty())
        assertEquals(1, gateway.requestedEmails.size)
    }

    @Test
    fun noProvider_keepsManualLoginAndSaveCancellationDoesNotUndoSuccess() {
        gateway.getResult = { PasswordSelection.Unavailable }
        launchAuth()
        compose.onNodeWithTag("auth_email").performTextInput("user1@example.invalid")
        compose.onNodeWithTag("auth_password").performTextInput("fixture-password")
        fixture.transport.respond("""{"success":true,"have2FA":false}""")
        fixture.transport.respond(loginResponse())
        fixture.transport.respond(profileResponse())
        submit()
        compose.waitUntil(5_000) { closed }
        assertEquals(1, gateway.saveCount)
        assertEquals("fixture-1", fixture.repository.session.value.activeUuid)
    }

    @Test
    fun accountSwitch_fixedTargetRegion_keepsSelectionAndListPosition() {
        val operation = mutableStateOf<SessionOperation?>(null)
        launch {
            AccountSwitchScreen(
                accounts = listOf(AccountItem(GUEST_ACCOUNT_ID, "Guest"), AccountItem("one", "Fixture One"), AccountItem("two", "Fixture Two")),
                selectedAccountId = "one", editing = false, snackbarHostState = remember { SnackbarHostState() },
                onSelectAccount = {}, onEditingChange = {}, onAddAccount = {}, onRemoveAccount = {}, onBack = { closed = true },
                busy = operation.value != null, operation = operation.value,
            )
        }
        val before = compose.onNodeWithTag("account_two").getUnclippedBoundsInRoot()
        compose.runOnIdle { operation.value = SessionOperation(SessionOperationType.SWITCH, "two") }
        val after = compose.onNodeWithTag("account_two").getUnclippedBoundsInRoot()
        assertEquals(before, after)
        compose.onNodeWithTag("account_one").assertIsSelected()
        compose.onNodeWithTag("account_two").assertIsNotSelected().assertIsNotEnabled()
        compose.onNodeWithTag("account_loading_two", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("account_status_two", useUnmergedTree = true).assertTextContains("Switching…")
        compose.onNodeWithTag("account_back").performClick()
        assertTrue(closed)
    }

    @Test
    fun sessionOperations_targetAndRetry_areSerialized_andProviderClearIsBestEffort() {
        fixture.store.saved = StoredSessions(baseUrl = ScriptedApi.BASE_URL, accounts = listOf(savedAccount(1), savedAccount(2)))
        val sessionFixture = AuthFixture(Dispatchers.Default)
        sessionFixture.store.saved = fixture.store.saved
        fixture = sessionFixture
        val store = ViewModelStore()
        lateinit var model: SessionViewModel
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            model = SessionViewModel(instrumentation.targetContext.applicationContext as android.app.Application,
                fixture.repository, gateway)
            store.put("session", model)
        }
        try {
            compose.waitUntil(5_000) { !model.state.value.isLoading && !model.state.value.isBusy }
            val held = fixture.transport.hold()
            instrumentation.runOnMainSync { model.select("fixture-2") }
            compose.waitUntil(5_000) { fixture.transport.requests.size == 1 }
            assertEquals(SessionOperation(SessionOperationType.SWITCH, "fixture-2"), model.state.value.operation)
            assertEquals(null, model.state.value.activeUuid)
            instrumentation.runOnMainSync { model.remove("fixture-1"); model.select("fixture-1") }
            assertEquals(1, fixture.transport.requests.size)
            held.respond(profileResponse(2))
            compose.waitUntil(5_000) { !model.state.value.isBusy }
            assertEquals("fixture-2", model.state.value.activeUuid)
            fixture.store.writeFailure = ApiFailure.STORAGE
            instrumentation.runOnMainSync { model.remove("fixture-2") }
            compose.waitUntil(5_000) { model.state.value.error != null }
            assertEquals("fixture-2", model.state.value.failedAccountUuid)
            assertEquals("fixture-2", model.state.value.activeUuid)
            assertEquals(0, gateway.clearCount)
            fixture.store.writeFailure = null
            gateway.clearResult = { error("Fake provider unavailable") }
            instrumentation.runOnMainSync { model.retry() }
            compose.waitUntil(5_000) { !model.state.value.isBusy && model.state.value.accounts.size == 1 }
            assertEquals(1, gateway.clearCount)
            assertEquals(null, model.state.value.error)
            assertEquals(null, model.state.value.activeUuid)
            fixture.transport.respond(profileResponse())
            instrumentation.runOnMainSync { model.select("fixture-1") }
            compose.waitUntil(5_000) { !model.state.value.isBusy && model.state.value.activeUuid == "fixture-1" }
            instrumentation.runOnMainSync { model.select(null) }
            compose.waitUntil(5_000) { !model.state.value.isBusy && model.state.value.activeUuid == null }
            assertEquals(2, gateway.clearCount)
            assertEquals(1, model.state.value.accounts.size)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test
    fun startupRejection_preservesTargetForRetryAndReauthentication() {
        val other = AuthFixture(Dispatchers.Default)
        other.store.saved = StoredSessions(baseUrl = ScriptedApi.BASE_URL,
            accounts = listOf(savedAccount(1)), activeUuid = "fixture-1")
        other.transport.respond("""{"success":false}""")
        fixture = other
        val store = ViewModelStore()
        lateinit var model: SessionViewModel
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            model = SessionViewModel(instrumentation.targetContext.applicationContext as android.app.Application,
                fixture.repository, gateway)
            store.put("startup", model)
        }
        try {
            compose.waitUntil(5_000) { model.state.value.error != null && !model.state.value.isBusy }
            assertEquals("fixture-1", model.state.value.failedAccountUuid)
            assertEquals(null, model.state.value.activeUuid)
            fixture.transport.respond(profileResponse())
            instrumentation.runOnMainSync { model.retry() }
            compose.waitUntil(5_000) { model.state.value.activeUuid == "fixture-1" && !model.state.value.isBusy }
            assertEquals(null, model.state.value.error)
            assertEquals(2, fixture.transport.requests.size)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test fun narrowEnglish_largeText_formsRemainScrollable() = layoutMatrix("en", DpSize(320.dp, 640.dp), 2f)
    @Test fun narrowChinese_largeText_formsRemainScrollable() = layoutMatrix("zh", DpSize(320.dp, 640.dp), 2f)
    @Test fun landscapeEnglish_formsRemainScrollable() = layoutMatrix("en", DpSize(740.dp, 360.dp), 1f)
    @Test fun landscapeChinese_largeText_formsRemainScrollable() = layoutMatrix("zh", DpSize(740.dp, 360.dp), 1.5f)

    private fun layoutMatrix(language: String, size: DpSize, scale: Float) {
        val step = mutableStateOf(AuthStep.LOGIN)
        launch(language) {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                    AuthScreen(
                        state = AuthUiState(step = step.value, email = "fixture@example.invalid"),
                        passwordVisible = false, snackbarHostState = remember { SnackbarHostState() },
                        emailFocusRequester = remember { FocusRequester() }, onEmailChange = {}, onPasswordChange = {},
                        onPasswordVisibilityChange = {}, onSubmit = {}, onRegister = {}, onClose = {}, onBack = {},
                    )
                }
            }
        }
        AuthStep.entries.filter { it != AuthStep.TOTP_HELP }.forEach {
            compose.runOnIdle { step.value = it }
            compose.onNodeWithTag("auth_submit").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("auth_close").assertIsDisplayed()
            if (it == AuthStep.REGISTER_CREDENTIALS) capture("$language-${size.width.value.toInt()}-large-credentials")
        }
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "auth-ui-$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun launchAuth(email: String = "") = launch {
        val backStack = rememberNavBackStack(MainRoute, AuthRoute(email))
        ActivityNavDisplay(backStack = backStack, onBack = { backStack.removeLastOrNull() }, entryProvider = entryProvider {
            entry<MainRoute> { NavigationPage { androidx.compose.material3.Text("Fixture origin") } }
            entry<AuthRoute> { route ->
                NavigationPage {
                    AuthPage(fixture.repository, route.email, onClose = { closed = true; backStack.removeLastOrNull() },
                        isActive = backStack.lastOrNull() == route, credentialGateway = gateway)
                }
            }
        })
    }

    private fun launch(language: String = "en", content: @Composable () -> Unit) {
        AuthUiCheckActivity.content = {
            val original = LocalContext.current
            val config = Configuration(original.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }
            val context = original.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config, LocalActivity provides (original as android.app.Activity)) {
                KIRAKIRATheme(dynamicColor = false) { content() }
            }
        }
        activity = ActivityScenario.launch(AuthUiCheckActivity::class.java)
        compose.waitForIdle()
    }

    private fun field(label: Int) = compose.onNodeWithTag("auth_field_$label")
    private fun submit() = compose.onNodeWithTag("auth_submit").performScrollTo().performClick().also { compose.waitForIdle() }
    private fun imeVisible(): Boolean {
        var visible = false
        activity?.onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        return visible
    }
}
