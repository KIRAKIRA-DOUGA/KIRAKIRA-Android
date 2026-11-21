package moe.kirakira.feature.settings.security

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
internal data class SecurityStepRoute(val step: SecurityStep) : NavKey

private val SecurityStep.previousStep: SecurityStep?
    get() = when (this) {
        SecurityStep.OVERVIEW -> null
        SecurityStep.EMAIL, SecurityStep.PASSWORD, SecurityStep.TWO_FACTOR,
        SecurityStep.CHECK_FACTOR -> SecurityStep.OVERVIEW
        SecurityStep.VERIFY_EMAIL -> SecurityStep.EMAIL
        SecurityStep.VERIFY_PASSWORD -> SecurityStep.PASSWORD
        SecurityStep.ENABLE_EMAIL, SecurityStep.DISABLE_EMAIL, SecurityStep.TOTP_CONFIRM,
        SecurityStep.DISABLE_TOTP, SecurityStep.SECRETS -> SecurityStep.TWO_FACTOR
    }

internal fun SecurityStep.routePath(): List<SecurityStepRoute> =
    generateSequence(this) { it.previousStep }.toList().asReversed().map(::SecurityStepRoute)
