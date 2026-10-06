package moe.kirakira.feature.auth

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import kotlinx.coroutines.flow.first
import moe.kirakira.ui.navigation.ActivityNavDisplay
import moe.kirakira.ui.navigation.LocalNavigationPageTransform
import moe.kirakira.ui.navigation.NavigationPage
import moe.kirakira.ui.navigation.NavigationPageTransform

@Composable
internal fun AuthNavigation(
    state: AuthUiState,
    viewModel: AuthViewModel,
    isActive: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    predictiveBackEnabled: Boolean = false,
) {
    val backStack = rememberNavBackStack(*state.step.routePath().toTypedArray())
    val dispatcherOwner = rememberNavigationEventDispatcherOwner(enabled = isActive)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val autofill = LocalAutofillManager.current

    LaunchedEffect(state.step) {
        // Cancel before removing the old fields; Compose can otherwise commit on navigation.
        autofill?.cancel()
        focusManager.clearFocus()
        // The validated business step determines the path; keep shared entries and their scroll state.
        val path = state.step.routePath()
        val commonSize = backStack.zip(path).takeWhile { (current, next) -> current == next }.size
        while (backStack.size > commonSize) backStack.removeLastOrNull()
        backStack.addAll(path.drop(commonSize))
    }

    fun back(from: AuthStep) {
        if (isActive && backStack.lastOrNull() == AuthStepRoute(from)) {
            autofill?.cancel()
            viewModel.back(from)
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides dispatcherOwner,
        // The outer AuthRoute has already applied its transform. Child pages apply only their own.
        LocalNavigationPageTransform provides { NavigationPageTransform() },
    ) {
        ActivityNavDisplay(
            backStack = backStack,
            predictiveBackEnabled = predictiveBackEnabled,
            onBack = { (backStack.lastOrNull() as? AuthStepRoute)?.let { back(it.step) } },
            modifier = modifier.background(MaterialTheme.colorScheme.surface),
            entryProvider = entryProvider {
                entry<AuthStepRoute> { route ->
                    NavigationPage {
                        AuthStepPage(
                            step = route.step,
                            state = state,
                            viewModel = viewModel,
                            isActive = isActive,
                            onBack = { back(route.step) },
                            onClose = onClose,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun AuthStepPage(
    step: AuthStep,
    state: AuthUiState,
    viewModel: AuthViewModel,
    isActive: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val currentStep = state.step == step && isActive
    // An outgoing entry must retain its own form during transitions, never render the next step.
    // Plain remember deliberately keeps these values out of saved state.
    var snapshot by remember { mutableStateOf(state.forStep(step)) }
    SideEffect {
        if (currentStep) snapshot = state
        if (!isActive || state.submission in setOf(AuthSubmission.FINISHING, AuthSubmission.SUCCEEDED)) {
            snapshot = snapshot.copy(password = "", confirmPassword = "", code = "", credentialOperation = null)
        }
    }
    val displayedState = if (currentStep) state else snapshot
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequesters = remember { AuthField.entries.associateWith { FocusRequester() } }
    val emailFocusRequester = focusRequesters.getValue(AuthField.EMAIL)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val autofill = LocalAutofillManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val locale = LocalConfiguration.current.locales[0]
    val language = if (locale.language == "zh") "zh-Hans-CN" else "en-US"
    var passwordVisible by remember { mutableStateOf(false) }
    LaunchedEffect(currentStep) { passwordVisible = false }
    LaunchedEffect(currentStep, state.canEdit, state.focusRequestId, lifecycle) {
        if (currentStep && state.canEdit) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            withFrameNanos { }
            state.focusField?.let { field ->
                focusRequesters.getValue(field).requestFocus()
                keyboard?.show()
                viewModel.consumeFocus(state.focusRequestId)
            }
        }
    }
    LaunchedEffect(currentStep, lifecycle) {
        if (currentStep && step == AuthStep.LOGIN) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            viewModel.requestSavedPassword(automatic = true)
        }
    }

    fun whenCurrent(action: () -> Unit) {
        if (isActive && viewModel.uiState.value.step == step) action()
    }

    AuthScreen(
        state = displayedState,
        passwordVisible = passwordVisible && currentStep,
        snackbarHostState = snackbarHostState,
        emailFocusRequester = emailFocusRequester,
        focusRequesters = focusRequesters,
        onEmailChange = { whenCurrent { viewModel.updateEmail(it) } },
        onPasswordChange = { whenCurrent { viewModel.updatePassword(it) } },
        onPasswordVisibilityChange = { whenCurrent { passwordVisible = !passwordVisible } },
        onSubmit = {
            whenCurrent {
                autofill?.cancel()
                passwordVisible = false
                viewModel.submit(language)
            }
        },
        onRegister = { whenCurrent { autofill?.cancel(); viewModel.requestRegistration() } },
        onClose = { whenCurrent(onClose) },
        onBack = { whenCurrent(onBack) },
        onFieldChange = { field, value -> whenCurrent { viewModel.updateField(field, value) } },
        onForgotPassword = { whenCurrent { autofill?.cancel(); viewModel.requestPasswordReset() } },
        onResend = { whenCurrent { viewModel.resend(language) } },
        onRecoveryHelp = {
            whenCurrent { uriHandler.openUri("https://github.com/KIRAKIRA-DOUGA/KIRAKIRA-Cerasus/issues") }
        },
    )
}

private fun AuthUiState.forStep(step: AuthStep): AuthUiState = when {
    this.step == step -> this
    step == AuthStep.LOGIN && this.step != AuthStep.LOGIN_EMAIL && this.step != AuthStep.LOGIN_TOTP -> {
        AuthUiState(email = email)
    }
    else -> copy(
        step = step,
        code = "",
        fieldErrors = emptyMap(),
        focusField = null,
        credentialOperation = null,
        emailInvalid = false,
        submission = AuthSubmission.IDLE,
        errorRes = null,
        noticeRes = null,
    )
}
