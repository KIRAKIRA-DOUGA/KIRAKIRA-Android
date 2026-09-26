package moe.kirakira.feature.auth

internal data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val emailInvalid: Boolean = false,
    val submission: AuthSubmission = AuthSubmission.IDLE,
    val feedback: AuthFeedback? = null,
) {
    val isSubmitting: Boolean
        get() = submission == AuthSubmission.SUBMITTING

    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotEmpty() && !isSubmitting
}

internal enum class AuthSubmission {
    IDLE,
    SUBMITTING,
    FAILED,
}

internal enum class AuthFeedback {
    LOGIN_UNAVAILABLE,
    REGISTRATION_UNAVAILABLE,
}
