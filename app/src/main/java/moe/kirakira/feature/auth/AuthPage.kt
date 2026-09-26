package moe.kirakira.feature.auth

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.kirakira.R

@Composable
internal fun AuthPage(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel { AuthViewModel(createSavedStateHandle()) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val emailFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Visibility is intentionally reset whenever this UI is recreated.
    var passwordVisible by remember { mutableStateOf(false) }
    val feedback = state.feedback
    val feedbackMessage = when (feedback) {
        AuthFeedback.LOGIN_UNAVAILABLE -> stringResource(R.string.account_login_unavailable)
        AuthFeedback.REGISTRATION_UNAVAILABLE -> stringResource(R.string.auth_registration_unavailable)
        null -> null
    }
    LaunchedEffect(feedback, feedbackMessage, lifecycleOwner) {
        if (feedback != null && feedbackMessage != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snackbarHostState.showSnackbar(feedbackMessage)
                viewModel.dismissFeedback(feedback)
            }
        }
    }

    AuthScreen(
        state = state,
        passwordVisible = passwordVisible,
        snackbarHostState = snackbarHostState,
        emailFocusRequester = emailFocusRequester,
        onEmailChange = viewModel::updateEmail,
        onPasswordChange = viewModel::updatePassword,
        onPasswordVisibilityChange = { passwordVisible = !passwordVisible },
        onSubmit = {
            if (viewModel.submit()) {
                focusManager.clearFocus()
                keyboard?.hide()
                passwordVisible = false
            } else if (viewModel.uiState.value.emailInvalid) {
                emailFocusRequester.requestFocus()
            }
        },
        onRegister = viewModel::requestRegistration,
        onClose = {
            keyboard?.hide()
            onClose()
        },
        modifier = modifier,
    )
}
