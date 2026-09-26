package moe.kirakira.feature.auth

import android.util.Patterns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class AuthViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    // Only the email is restored. Credentials and submission results stay in this entry's memory.
    private val _uiState = MutableStateFlow(AuthUiState(email = savedStateHandle[EMAIL_KEY] ?: ""))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        if (_uiState.value.isSubmitting) return
        savedStateHandle[EMAIL_KEY] = email
        _uiState.update { it.copy(email = email, emailInvalid = false, submission = AuthSubmission.IDLE) }
    }

    fun updatePassword(password: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(password = password, submission = AuthSubmission.IDLE) }
    }

    /** Returns whether the input was accepted for submission, not whether authentication succeeded. */
    fun submit(): Boolean {
        val state = _uiState.value
        if (!state.canSubmit) return false
        val email = state.email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(emailInvalid = true, submission = AuthSubmission.IDLE) }
            return false
        }
        savedStateHandle[EMAIL_KEY] = email
        // API integration point: call AuthRepository in viewModelScope and map its result to UI state.
        // The prototype deliberately does not enter SUBMITTING or fabricate a successful session.
        _uiState.update {
            it.copy(
                email = email,
                emailInvalid = false,
                submission = AuthSubmission.IDLE,
                feedback = AuthFeedback.LOGIN_UNAVAILABLE,
            )
        }
        return true
    }

    fun requestRegistration() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(feedback = AuthFeedback.REGISTRATION_UNAVAILABLE) }
    }

    fun dismissFeedback(feedback: AuthFeedback) {
        _uiState.update { if (it.feedback == feedback) it.copy(feedback = null) else it }
    }

    override fun onCleared() {
        _uiState.value = AuthUiState()
        super.onCleared()
    }

    private companion object {
        const val EMAIL_KEY = "auth_email"
    }
}
