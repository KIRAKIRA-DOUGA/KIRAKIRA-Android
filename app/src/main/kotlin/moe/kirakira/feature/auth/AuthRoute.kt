package moe.kirakira.feature.auth

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
internal data class AuthRoute(val email: String = "") : NavKey

@Serializable
internal data class AuthStepRoute(val step: AuthStep) : NavKey

internal val AuthStep.previousStep: AuthStep?
    get() = when (this) {
        AuthStep.LOGIN -> null
        AuthStep.REGISTER_CREDENTIALS -> AuthStep.REGISTER_PROFILE
        AuthStep.REGISTER_INVITATION -> AuthStep.REGISTER_CREDENTIALS
        AuthStep.REGISTER_VERIFY -> AuthStep.REGISTER_INVITATION
        AuthStep.RESET_PASSWORD, AuthStep.TOTP_HELP -> AuthStep.FORGOT_EMAIL
        else -> AuthStep.LOGIN
    }

internal fun AuthStep.routePath(): List<AuthStepRoute> =
    generateSequence(this) { it.previousStep }.toList().asReversed().map(::AuthStepRoute)
