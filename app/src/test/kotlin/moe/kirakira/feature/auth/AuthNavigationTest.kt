package moe.kirakira.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthNavigationTest {
    @Test
    fun registration_returnsThroughCredentialsAndProfileToLogin() {
        assertEquals(
            listOf(AuthStep.LOGIN, AuthStep.REGISTER_PROFILE, AuthStep.REGISTER_CREDENTIALS, AuthStep.REGISTER_INVITATION, AuthStep.REGISTER_VERIFY),
            AuthStep.REGISTER_VERIFY.routePath().map { it.step },
        )
        assertNull(AuthStep.LOGIN.previousStep)
    }

    @Test
    fun recovery_bothBranches_returnThroughEmailEntry() {
        assertEquals(
            listOf(AuthStep.LOGIN, AuthStep.FORGOT_EMAIL, AuthStep.RESET_PASSWORD),
            AuthStep.RESET_PASSWORD.routePath().map { it.step },
        )
        assertEquals(
            listOf(AuthStep.LOGIN, AuthStep.FORGOT_EMAIL, AuthStep.TOTP_HELP),
            AuthStep.TOTP_HELP.routePath().map { it.step },
        )
    }

    @Test
    fun saveRetries_doNotReturnToConsumedVerificationSteps() {
        assertEquals(listOf(AuthStep.LOGIN, AuthStep.SAVE_SESSION), AuthStep.SAVE_SESSION.routePath().map { it.step })
        assertEquals(
            listOf(AuthStep.LOGIN, AuthStep.SAVE_PASSWORD_RESET),
            AuthStep.SAVE_PASSWORD_RESET.routePath().map { it.step },
        )
    }
}
