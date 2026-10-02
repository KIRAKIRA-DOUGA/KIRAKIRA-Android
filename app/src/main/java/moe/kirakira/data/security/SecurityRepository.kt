package moe.kirakira.data.security

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moe.kirakira.core.network.ApiClient
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.auth.SecondFactor
import moe.kirakira.data.auth.StoredAccount

internal data class SecurityStatus(val factor: SecondFactor, val totpCreationDateTime: Long? = null)

// Plain classes avoid generated toString() methods containing secrets.
internal class TotpSetup(val uri: String, val secret: String)
internal class TotpSecrets(val backupCodes: List<String>, val recoveryCode: String)

internal enum class SecurityEmailPurpose(val businessName: String, val template: String) {
    EMAIL("update-email", "SendChangeEmailVerificationCode"),
    PASSWORD("update-password", "SendChangePasswordVerificationCode"),
    DISABLE_EMAIL("delete-email-2fa", "SendDisableUserEmail2FAVerificationCode"),
}

/** One navigation entry owns completion checkpoints; a local failure never replays a confirmed mutation. */
internal class SecurityRepository(private val api: ApiClient, private val auth: AuthRepository) {
    val session = auth.session
    var publishedRevision: Long? = null
        private set
    private var pending: Completion? = null
    val needsCompletion: Boolean get() = pending != null
    var totpConfirmed: Boolean = false
        private set

    fun resendSeconds(email: String): Int = auth.resendSeconds(email)
    fun discard() { pending = null; publishedRevision = null; totpConfirmed = false }

    suspend fun status(revision: Long): SecurityStatus = request(revision) { snapshot ->
        val dto = api.get<StatusDto>("user/checkUserHave2FAByUUID", cookie = snapshot.requiredAccount().cookie())
        if (!dto.success) rejected()
        when {
            dto.have2FA == false && dto.type == null -> SecurityStatus(SecondFactor.NONE)
            dto.have2FA == true && dto.type == "email" -> SecurityStatus(SecondFactor.EMAIL)
            dto.have2FA == true && dto.type == "totp" -> SecurityStatus(SecondFactor.TOTP, dto.totpCreationDateTime)
            else -> invalid()
        }
    }

    suspend fun sendCode(revision: Long, purpose: SecurityEmailPurpose, language: String, newEmail: String?, factor: SecondFactor) =
        request(revision) { snapshot ->
            val account = snapshot.requiredAccount()
            val email = newEmail ?: account.profile.email
            auth.withVerificationCooldown(email) {
                checkCurrent(snapshot)
                val body = buildJsonObject {
                    if (newEmail != null) put("email", email)
                    put("clientLanguage", language)
                    put("mailTemplate", purpose.template)
                    // Rosales strict verification hardcodes update-email for accounts without a second factor.
                    put("exclusiveBusinessName", if (purpose == SecurityEmailPurpose.PASSWORD && factor == SecondFactor.NONE)
                        SecurityEmailPurpose.EMAIL.businessName else purpose.businessName)
                }.toString()
                api.post<MutationDto>(if (newEmail == null) "user/sendGeneral2FAEmailVerificationCode"
                    else "user/sendGeneralEmailVerificationCode", body, account.cookie()).requireSuccess()
            }
        }

    suspend fun changeEmail(revision: Long, email: String, password: String, currentCode: String, newCode: String) {
        request(revision) { snapshot ->
            val account = snapshot.requiredAccount()
            val hash = auth.hashPassword(password)
            checkCurrent(snapshot)
            val body = buildJsonObject {
                put("oldEmail", account.profile.email)
                put("newEmail", email)
                put("passwordHash", hash)
                put("changeEmailVerificationCode", currentCode)
                put("changeEmailNewEmailVerificationCode", newCode)
            }.toString()
            api.post<MutationDto>("user/update/email", body, account.cookie()).requireSuccess()
            pending = Completion(snapshot, email)
        }
        finishCompletion()
    }

    suspend fun changePassword(revision: Long, old: String, new: String, code: String) {
        request(revision) { snapshot ->
            val oldHash = auth.hashPassword(old)
            val newHash = auth.hashPassword(new)
            checkCurrent(snapshot)
            val body = buildJsonObject {
                put("oldPasswordHash", oldHash)
                put("newPasswordHash", newHash)
                put("verificationCode", code)
            }.toString()
            api.post<MutationDto>("user/update/password", body, snapshot.requiredAccount().cookie()).requireSuccess()
            pending = Completion(snapshot)
        }
        finishCompletion()
    }

    suspend fun finishCompletion() {
        val completion = pending ?: return
        val snapshot = completion.snapshot
        checkCurrent(snapshot)
        val profile = if (completion.email != null) auth.fetchProfile(snapshot).also {
            if (!it.email.equals(completion.email, ignoreCase = true)) invalid()
        } else null
        try {
            val onPublishing: (Long) -> Unit = { publishedRevision = it }
            if (profile == null) auth.removeRequestSession(snapshot, onPublishing)
            else auth.publishProfile(snapshot, profile, onPublishing)
            pending = null
        } catch (error: Exception) {
            publishedRevision = null
            throw error
        }
    }

    suspend fun enableEmail(revision: Long) = request(revision) { snapshot ->
        val result = api.post<MutationDto>("user/createEmailAuthenticator", "{}", snapshot.requiredAccount().cookie())
        result.requireSuccess()
        if (result.isExists) rejected()
    }

    suspend fun disable(revision: Long, factor: SecondFactor, password: String, code: String) = request(revision) { snapshot ->
        val hash = auth.hashPassword(password)
        checkCurrent(snapshot)
        val body = buildJsonObject {
            put("passwordHash", hash)
            put(if (factor == SecondFactor.TOTP) "clientOtp" else "verificationCode", code)
        }.toString()
        api.delete<MutationDto>(if (factor == SecondFactor.TOTP)
            "user/deleteTotpAuthenticatorByTotpVerificationCodeController" else "user/deleteUserEmailAuthenticator",
            body, snapshot.requiredAccount().cookie()).requireSuccess()
    }

    suspend fun beginTotp(revision: Long): TotpSetup = request(revision) { snapshot ->
        totpConfirmed = false
        val dto = api.post<SetupDto>("user/createTotpAuthenticator", "{}", snapshot.requiredAccount().cookie())
        if (!dto.success || dto.isExists) rejected()
        val value = dto.result?.otpAuth ?: invalid()
        val uri = Uri.parse(value)
        if (uri.scheme != "otpauth" || uri.host != "totp") invalid()
        val secret = uri.getQueryParameter("secret")?.takeIf { it.matches(Regex("[A-Z2-7]+")) } ?: invalid()
        TotpSetup(value, secret)
    }

    suspend fun confirmTotp(revision: Long, setup: TotpSetup, code: String): TotpSecrets = request(revision) { snapshot ->
        val body = buildJsonObject { put("clientOtp", code); put("otpAuth", setup.uri) }.toString()
        val dto = api.post<ConfirmDto>("user/confirmUserTotpAuthenticator", body, snapshot.requiredAccount().cookie())
        if (!dto.success) rejected()
        totpConfirmed = true
        val result = dto.result ?: invalid()
        val codes = result.backupCode ?: invalid()
        val recovery = result.recoveryCode ?: invalid()
        if (codes.size != 5 || codes.distinct().size != 5 || codes.any { !it.matches(Regex("[A-Z0-9]{6}")) } ||
            !recovery.matches(Regex("[A-Z0-9]{24}"))) invalid()
        TotpSecrets(codes, recovery)
    }

    private fun checkCurrent(snapshot: AuthRepository.RequestSession) {
        if (!auth.isCurrent(snapshot)) throw CancellationException("Account changed")
    }

    private suspend fun <T> request(revision: Long, block: suspend (AuthRepository.RequestSession) -> T): T {
        val snapshot = auth.requestSession()
        if (snapshot.revision != revision) throw CancellationException("Account changed")
        if (snapshot.account == null) throw ApiException(ApiFailure.SESSION_EXPIRED)
        try {
            val result = block(snapshot)
            currentCoroutineContext().ensureActive()
            checkCurrent(snapshot)
            return result
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            checkCurrent(snapshot)
            if (error is ApiException && error.failure == ApiFailure.SESSION_EXPIRED) auth.expireRequestSession(snapshot)
            throw error
        }
    }

    private class Completion(val snapshot: AuthRepository.RequestSession, val email: String? = null)
}

@Serializable private class StatusDto(val success: Boolean, val have2FA: Boolean? = null,
    val type: String? = null, val totpCreationDateTime: Long? = null)
@Serializable private class MutationDto(val success: Boolean, val isExists: Boolean = false,
    val isCoolingDown: Boolean = false, val isMaxDailyCreateAttempts: Boolean = false,
    val isMaxDailyVerifierAttempts: Boolean = false) {
    fun requireSuccess() {
        when {
            isMaxDailyCreateAttempts || isMaxDailyVerifierAttempts -> throw ApiException(ApiFailure.DAILY_LIMIT)
            isCoolingDown -> throw ApiException(ApiFailure.RATE_LIMITED)
            !success -> rejected()
        }
    }
}
@Serializable private class SetupDto(val success: Boolean, val isExists: Boolean = false, val result: SetupResult? = null)
@Serializable private class SetupResult(val otpAuth: String? = null)
@Serializable private class ConfirmDto(val success: Boolean, val result: SecretResult? = null)
@Serializable private class SecretResult(val backupCode: List<String>? = null, val recoveryCode: String? = null)
private fun rejected(): Nothing = throw ApiException(ApiFailure.REJECTED)
private fun invalid(): Nothing = throw ApiException(ApiFailure.INVALID_RESPONSE)
private fun AuthRepository.RequestSession.requiredAccount(): StoredAccount =
    account ?: throw ApiException(ApiFailure.SESSION_EXPIRED)
