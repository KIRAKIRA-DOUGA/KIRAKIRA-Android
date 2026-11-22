package moe.kirakira.feature.settings.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.kirakira.core.credentials.passwordCredentialGateway

@Composable
internal fun SecuritySettingsPage(
    model: SecuritySettingsViewModel,
    isActive: Boolean,
    onBack: () -> Unit,
    onLogin: (String) -> Unit,
    predictiveBackEnabled: Boolean = false,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val autofill = LocalAutofillManager.current
    LaunchedEffect(model, isActive) {
        if (!isActive) { autofill?.cancel(); model.deactivate() }
        else model.refresh()
    }
    LaunchedEffect(state.loginEmail, isActive) {
        val email = state.loginEmail ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        autofill?.cancel()
        passwordCredentialGateway(context).clearSession()
        onLogin(email)
        model.consumeLogin()
    }
    DisposableEffect(Unit) { onDispose { autofill?.cancel() } }
    SecuritySettingsNavigation(
        state = if (state.revision == session.revision) state else SecuritySettingsState(revision = session.revision),
        model = model,
        email = session.activeProfile?.email,
        sessionBusy = session.isLoading || session.isBusy,
        isActive = isActive,
        predictiveBackEnabled = predictiveBackEnabled,
        onBack = onBack,
        onLogin = { onLogin("") },
    )
}
