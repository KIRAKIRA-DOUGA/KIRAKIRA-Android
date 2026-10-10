package moe.kirakira.feature.settings.security

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import moe.kirakira.ui.navigation.ActivityNavDisplay
import moe.kirakira.ui.navigation.LocalNavigationPageTransform
import moe.kirakira.ui.navigation.NavigationPage
import moe.kirakira.ui.navigation.NavigationPageTransform

@Composable
internal fun SecuritySettingsNavigation(
    state: SecuritySettingsState,
    model: SecuritySettingsViewModel,
    email: String?,
    sessionBusy: Boolean,
    isActive: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    predictiveBackEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(*state.step.routePath().toTypedArray())
    val dispatcherOwner = rememberNavigationEventDispatcherOwner(enabled = isActive)
    val autofill = LocalAutofillManager.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.step, state.revision) {
        autofill?.cancel()
        focus.clearFocus()
        keyboard?.hide()
        val path = state.step.routePath()
        val commonSize = backStack.zip(path).takeWhile { (current, next) -> current == next }.size
        while (backStack.size > commonSize) backStack.removeLastOrNull()
        backStack.addAll(path.drop(commonSize))
    }

    fun isCurrent(step: SecurityStep): Boolean = isActive &&
        backStack.lastOrNull() == SecurityStepRoute(step) && model.state.value.step == step &&
        model.state.value.revision == state.revision && state.revision == model.session.value.revision

    fun back(step: SecurityStep) {
        if (!isCurrent(step)) return
        autofill?.cancel()
        focus.clearFocus()
        keyboard?.hide()
        if (model.requestBack()) onBack()
    }

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides dispatcherOwner,
        // The outer security entry already applies its own page transform.
        LocalNavigationPageTransform provides { NavigationPageTransform() },
    ) {
        ActivityNavDisplay(
            backStack = backStack,
            predictiveBackEnabled = predictiveBackEnabled,
            canNavigateBack = {
                val route = backStack.lastOrNull() as? SecurityStepRoute
                route != null && isCurrent(route.step) && model.state.value.canLeaveStep
            },
            onBackRequested = {
                val route = backStack.lastOrNull() as? SecurityStepRoute
                when {
                    route == null || !isCurrent(route.step) -> false
                    model.state.value.canLeaveStep -> true
                    else -> { back(route.step); false }
                }
            },
            onBack = { (backStack.lastOrNull() as? SecurityStepRoute)?.let { back(it.step) } },
            modifier = modifier.background(MaterialTheme.colorScheme.surface),
            entryProvider = entryProvider {
                entry<SecurityStepRoute> { route ->
                    NavigationPage {
                        SecurityStepPage(
                            step = route.step,
                            state = state,
                            model = model,
                            email = email,
                            sessionBusy = sessionBusy,
                            isActive = isActive,
                            onBack = { back(route.step) },
                            onLogin = onLogin,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SecurityStepPage(
    step: SecurityStep,
    state: SecuritySettingsState,
    model: SecuritySettingsViewModel,
    email: String?,
    sessionBusy: Boolean,
    isActive: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val currentStep = isActive && state.step == step
    // Rendering snapshots stay in memory and never retain input credentials or one-time codes.
    val transitionSnapshot = remember(state, step) { state.forStep(step).forTransition() }
    var snapshot by remember(state.revision) { mutableStateOf(transitionSnapshot) }
    SideEffect {
        if (currentStep) snapshot = transitionSnapshot
        if (!isActive && snapshot.twoFactor is TwoFactorFlow.ConfirmTotp) {
            snapshot = snapshot.copy(twoFactor = TwoFactorFlow.Manage())
        }
    }
    val displayedState = when {
        !isActive -> state.forStep(step).withoutMaterials()
        currentStep -> state
        step == SecurityStep.OVERVIEW -> state.forStep(step)
        step == SecurityStep.TWO_FACTOR && state.status != null -> state.forStep(step)
        step == SecurityStep.EMAIL && state.credentials is CredentialFlow.VerifyEmail -> state.forStep(step)
        step == SecurityStep.PASSWORD && state.credentials is CredentialFlow.VerifyPassword -> state.forStep(step)
        else -> snapshot
    }
    val autofill = LocalAutofillManager.current
    val language = if (LocalConfiguration.current.locales[0].language == "zh") "zh-Hans-CN" else "en-US"

    fun whenCurrent(action: () -> Unit) {
        val current = model.state.value
        if (currentStep && current.step == step && current.revision == state.revision &&
            current.revision == model.session.value.revision) action()
    }

    SecuritySettingsScreen(
        state = displayedState,
        step = step,
        isActive = currentStep,
        email = email,
        sessionBusy = sessionBusy,
        onBack = { whenCurrent(onBack) },
        onLogin = { whenCurrent(onLogin) },
        onRefresh = { whenCurrent(model::refresh) },
        onOpen = { whenCurrent { autofill?.cancel(); model.open(it) } },
        onEdit = { field, value -> whenCurrent { model.edit(field, value) } },
        onSendCode = { whenCurrent { model.sendCode(language, it) } },
        onSubmit = { whenCurrent { autofill?.cancel(); model.submit() } },
        onFinishCodes = { whenCurrent(model::finishCodes) },
        onCancelDiscard = { whenCurrent(model::cancelDiscardCodes) },
        onDismissMessage = { whenCurrent(model::dismissMessage) },
        onCopied = { whenCurrent { model.copied(it) } },
        onCancelDiscardCredentials = { whenCurrent(model::cancelDiscardCredentials) },
        onDiscardCredentials = { whenCurrent(model::discardCredentials) },
    )
}

private fun SecuritySettingsState.forStep(step: SecurityStep): SecuritySettingsState {
    if (this.step == step) return this
    val flow = when (step) {
        SecurityStep.OVERVIEW -> when (val current = twoFactor) {
            is TwoFactorFlow.Checking -> TwoFactorFlow.Checking(current.mutation.withoutSecrets())
            else -> TwoFactorFlow.Manage()
        }
        SecurityStep.TWO_FACTOR -> TwoFactorFlow.Manage(
            draft = when (val current = twoFactor) {
                is TwoFactorFlow.Manage -> current.draft
                is TwoFactorFlow.EnableEmail -> current.draft
                is TwoFactorFlow.ConfirmTotp -> TotpDraft.Available(current.setup)
                is TwoFactorFlow.DisableEmail, is TwoFactorFlow.DisableTotp,
                is TwoFactorFlow.SaveCodes, is TwoFactorFlow.Checking -> null
            },
        )
        SecurityStep.ENABLE_EMAIL -> TwoFactorFlow.EnableEmail()
        SecurityStep.DISABLE_EMAIL -> TwoFactorFlow.DisableEmail()
        SecurityStep.DISABLE_TOTP -> TwoFactorFlow.DisableTotp()
        SecurityStep.EMAIL, SecurityStep.VERIFY_EMAIL, SecurityStep.PASSWORD, SecurityStep.VERIFY_PASSWORD, SecurityStep.TOTP_CONFIRM,
        SecurityStep.SECRETS, SecurityStep.CHECK_FACTOR -> TwoFactorFlow.Manage()
    }
    return copy(
        page = when (step) {
            SecurityStep.OVERVIEW -> SecurityPage.OVERVIEW
            SecurityStep.EMAIL, SecurityStep.VERIFY_EMAIL -> SecurityPage.EMAIL
            SecurityStep.PASSWORD, SecurityStep.VERIFY_PASSWORD -> SecurityPage.PASSWORD
            else -> SecurityPage.TWO_FACTOR
        },
        twoFactor = flow,
        credentials = when (step) {
            SecurityStep.EMAIL -> CredentialFlow.EditEmail(credentials.form.only(SecurityField.EMAIL))
            SecurityStep.VERIFY_EMAIL -> CredentialFlow.VerifyEmail(credentials.form.only(SecurityField.EMAIL))
            SecurityStep.PASSWORD -> CredentialFlow.EditPassword(
                credentials.form.only(SecurityField.NEW_PASSWORD, SecurityField.CONFIRM_PASSWORD),
            )
            SecurityStep.VERIFY_PASSWORD -> CredentialFlow.VerifyPassword(SecurityForm.Empty)
            else -> CredentialFlow.Idle
        },
        validation = null,
        fieldErrors = emptyMap(),
        error = null,
        message = null,
    )
}

private fun SecuritySettingsState.forTransition(): SecuritySettingsState = copy(
    credentials = when (val flow = credentials) {
        is CredentialFlow.EditEmail -> CredentialFlow.EditEmail(flow.form.only(SecurityField.EMAIL))
        is CredentialFlow.VerifyEmail -> CredentialFlow.VerifyEmail(flow.form.only(SecurityField.EMAIL))
        is CredentialFlow.EditPassword -> CredentialFlow.EditPassword()
        is CredentialFlow.VerifyPassword -> CredentialFlow.VerifyPassword(SecurityForm.Empty)
        CredentialFlow.Idle, CredentialFlow.SyncEmail, CredentialFlow.SyncPassword -> flow
    },
    twoFactor = when (val flow = twoFactor) {
        is TwoFactorFlow.ConfirmTotp -> TwoFactorFlow.ConfirmTotp(flow.setup)
        is TwoFactorFlow.DisableEmail -> TwoFactorFlow.DisableEmail()
        is TwoFactorFlow.DisableTotp -> TwoFactorFlow.DisableTotp()
        is TwoFactorFlow.SaveCodes -> TwoFactorFlow.Manage()
        is TwoFactorFlow.Checking -> TwoFactorFlow.Checking(flow.mutation.withoutSecrets())
        is TwoFactorFlow.Manage -> TwoFactorFlow.Manage(notice = flow.notice)
        is TwoFactorFlow.EnableEmail -> TwoFactorFlow.EnableEmail()
    },
    fieldErrors = emptyMap(),
    message = null,
)

private fun SecuritySettingsState.withoutMaterials(): SecuritySettingsState =
    forTransition().let { snapshot ->
        if (snapshot.twoFactor is TwoFactorFlow.ConfirmTotp) snapshot.copy(twoFactor = TwoFactorFlow.Manage())
        else snapshot
    }
