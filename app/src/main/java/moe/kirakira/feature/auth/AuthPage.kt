package moe.kirakira.feature.auth

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import moe.kirakira.BuildConfig
import moe.kirakira.R
import moe.kirakira.core.credentials.PasswordCredentialGateway
import moe.kirakira.core.credentials.PasswordSaveResult
import moe.kirakira.core.credentials.PasswordSelection
import moe.kirakira.core.credentials.passwordCredentialGateway
import moe.kirakira.data.auth.AuthRepository

@Composable
internal fun AuthPage(
    repository: AuthRepository,
    initialEmail: String,
    onClose: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    credentialGateway: PasswordCredentialGateway? = null,
) {
    // Own the form at the outer AuthRoute so all child entries share the same in-memory draft.
    val context = LocalContext.current
    val activity = LocalActivity.current
    val gateway = remember(credentialGateway, context) { credentialGateway ?: passwordCredentialGateway(context) }
    val viewModel = viewModel {
        AuthViewModel(
            createSavedStateHandle(), repository, initialEmail,
            credentialsEnabled = credentialGateway != null || BuildConfig.SYSTEM_CREDENTIALS_ENABLED,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    val autofill = LocalAutofillManager.current
    val credentialHost = remember(activity) { Any() }
    DisposableEffect(activity) {
        onDispose {
            autofill?.cancel()
            viewModel.uiState.value.credentialOperation?.let {
                viewModel.abandonCredentialOperation(it.id, credentialHost)
            }
        }
    }
    val language = if (LocalConfiguration.current.locales[0].language == "zh") "zh-Hans-CN" else "en-US"
    LaunchedEffect(state.credentialOperation?.id, activity, isActive) {
        val operation = state.credentialOperation ?: return@LaunchedEffect
        if (!isActive || activity == null) return@LaunchedEffect
        lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        val host = credentialHost
        if (!viewModel.claimCredentialOperation(operation.id, host)) return@LaunchedEffect
        autofill?.cancel()
        try {
            when (operation) {
                is AuthCredentialOperation.Get -> {
                    val result = try {
                        gateway.get(activity, operation.email)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        PasswordSelection.Failed
                    }
                    currentCoroutineContext().ensureActive()
                    viewModel.credentialSelected(operation.id, host, result, language)
                }
                is AuthCredentialOperation.Save -> {
                    val result = try {
                        gateway.save(activity, operation.draft)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        PasswordSaveResult.FAILED
                    }
                    currentCoroutineContext().ensureActive()
                    if (result == PasswordSaveResult.FAILED && viewModel.uiState.value.credentialOperation?.id == operation.id) {
                        val message = if (operation.passwordReset) R.string.auth_password_update_failed
                            else R.string.auth_password_save_failed
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    }
                    viewModel.passwordSaved(operation.id, host, result)
                }
            }
        } finally {
            viewModel.abandonCredentialOperation(operation.id, host)
        }
    }
    LaunchedEffect(state.submission, lifecycleOwner, isActive) {
        if (isActive && state.submission == AuthSubmission.SUCCEEDED) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { currentOnClose() }
        }
    }
    LaunchedEffect(isActive) {
        // Also cancel a root-level system back before the outer exit animation releases this entry.
        if (!isActive) {
            autofill?.cancel()
            viewModel.cancel()
        }
    }
    key(viewModel.navigationStateKey) {
        AuthNavigation(
            state = state,
            viewModel = viewModel,
            isActive = isActive,
            onClose = {
                autofill?.cancel()
                viewModel.cancel()
                keyboard?.hide()
                currentOnClose()
            },
            modifier = modifier,
        )
    }
}
