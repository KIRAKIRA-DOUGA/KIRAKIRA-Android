package moe.kirakira.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import moe.kirakira.core.network.ApiClient
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure

internal enum class SecondFactor { NONE, EMAIL, TOTP }

internal enum class VerificationPurpose(val businessName: String, val template: String) {
    LOGIN("login", "SendLoginVerificationCode"),
    REGISTRATION("registration", "SendRegistrationVerificationCode"),
    PASSWORD_RESET("forgot-password", "SendResetPasswordVerificationCode"),
}

internal class AuthApi(private val client: ApiClient) {
    suspend fun secondFactor(email: String): SecondFactor {
        val result = client.get<CheckDto>("user/checkUserHave2FAByEmail", mapOf("email" to email))
        result.requireSuccess()
        return when {
            result.have2FA == false -> SecondFactor.NONE
            result.have2FA == true && result.type == "email" -> SecondFactor.EMAIL
            result.have2FA == true && result.type == "totp" -> SecondFactor.TOTP
            else -> throw ApiException(ApiFailure.INVALID_RESPONSE)
        }
    }

    suspend fun sendCode(email: String, purpose: VerificationPurpose, language: String) {
        client.post<CheckDto>(
            "user/sendGeneralEmailVerificationCode",
            client.json.encodeToString(CodeRequest(email, language, purpose.template, purpose.businessName)),
        ).requireSuccess()
    }

    suspend fun checkUsername(username: String): Boolean {
        val result = client.get<CheckDto>("user/checkUsername", mapOf("username" to username))
        result.requireSuccess()
        return result.isAvailableUsername ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
    }

    suspend fun emailExists(email: String): Boolean {
        val result = client.get<CheckDto>("user/existsCheck", mapOf("email" to email))
        result.requireSuccess()
        return result.exists ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
    }

    suspend fun checkInvitation(code: String): Boolean {
        val result = client.post<CheckDto>("user/checkInvitationCode", client.json.encodeToString(InvitationRequest(code)))
        result.requireSuccess()
        return result.isAvailableInvitationCode ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
    }

    suspend fun login(email: String, hash: String, code: String, factor: SecondFactor): StoredAccount {
        val request = LoginRequest(
            email = email,
            passwordHash = hash,
            clientOtp = code.takeIf { factor == SecondFactor.TOTP },
            verificationCode = code.takeIf { factor == SecondFactor.EMAIL },
        )
        return client.post<LoginDto>("user/login", client.json.encodeToString(request)).account(email)
    }

    suspend fun register(
        email: String,
        hash: String,
        code: String,
        invitation: String,
        username: String,
        nickname: String,
        passwordHint: String,
    ): StoredAccount = client.post<LoginDto>(
        "user/registering",
        client.json.encodeToString(RegistrationRequest(email, hash, code, invitation, username, nickname, passwordHint)),
    ).account(email)

    suspend fun resetPassword(email: String, hash: String, code: String) {
        client.post<CheckDto>(
            "user/forgot/password",
            client.json.encodeToString(ResetRequest(email, hash, code)),
        ).requireSuccess()
    }

    suspend fun profile(account: StoredAccount): AccountProfile = refreshAccount(account).profile

    suspend fun refreshAccount(account: StoredAccount): StoredAccount {
        val result = client.post<SelfDto>("user/self", "{}", account.cookie())
        // Rosales also returns success=false for database errors. Do not erase a token on that signal.
        if (!result.success) throw ApiException(ApiFailure.REJECTED)
        val profile = result.result ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        if (profile.uuid != account.profile.uuid || profile.uid != account.profile.uid) {
            throw ApiException(ApiFailure.INVALID_RESPONSE)
        }
        val updated = AccountProfile(
            uuid = profile.uuid,
            uid = profile.uid,
            email = profile.email ?: account.profile.email,
            username = profile.username.orEmpty(),
            nickname = profile.userNickname.orEmpty(),
            avatar = profile.avatar?.trim()?.takeIf { it.isNotEmpty() },
            signature = profile.signature.orEmpty(),
            banner = profile.userBannerImage,
        )
        return StoredAccount(updated, account.token, userDataBootstrapHint =
            profile.userDataBootstrapHint.validHint() ?: account.userDataBootstrapHint)
    }
}

@Serializable
private class LoginRequest(
    val email: String,
    val passwordHash: String,
    val clientOtp: String?,
    val verificationCode: String?,
)

@Serializable
private class RegistrationRequest(
    val email: String,
    val passwordHash: String,
    val verificationCode: String,
    val invitationCode: String,
    val username: String,
    val userNickname: String,
    val passwordHint: String,
)

@Serializable
private class ResetRequest(val email: String, val newPasswordHash: String, val verificationCode: String)

@Serializable
private class CodeRequest(
    val email: String,
    val clientLanguage: String,
    val mailTemplate: String,
    val exclusiveBusinessName: String,
)

@Serializable
private class InvitationRequest(val invitationCode: String)

@Serializable
private class CheckDto(
    val success: Boolean,
    val have2FA: Boolean? = null,
    val type: String? = null,
    val exists: Boolean? = null,
    val isAvailableUsername: Boolean? = null,
    val isAvailableInvitationCode: Boolean? = null,
    val isCoolingDown: Boolean = false,
    val isMaxDailyCreateAttempts: Boolean = false,
    val isMaxDailyVerifierAttempts: Boolean = false,
) {
    fun requireSuccess() {
        when {
            isMaxDailyCreateAttempts || isMaxDailyVerifierAttempts -> throw ApiException(ApiFailure.DAILY_LIMIT)
            isCoolingDown -> throw ApiException(ApiFailure.RATE_LIMITED)
            !success -> throw ApiException(ApiFailure.REJECTED)
        }
    }
}

@Serializable
private class LoginDto(
    val success: Boolean,
    @SerialName("UUID") val uuid: String? = null,
    val uid: Long? = null,
    val token: String? = null,
    val userDataBootstrapHint: String? = null,
    val isCoolingDown: Boolean = false,
) {
    fun account(email: String): StoredAccount {
        if (isCoolingDown) throw ApiException(ApiFailure.RATE_LIMITED)
        if (!success) throw ApiException(ApiFailure.REJECTED)
        if (uuid.isNullOrBlank() || uid == null || uid < 0 || token.isNullOrBlank() ||
            !uuid.matches(Regex("[a-zA-Z0-9-]+")) ||
            token.any { it.code !in 0x21..0x7E || it == ';' || it == ',' }
        ) throw ApiException(ApiFailure.INVALID_RESPONSE)
        return StoredAccount(AccountProfile(uuid, uid, email), token,
            userDataBootstrapHint = userDataBootstrapHint.validHint())
    }
}

@Serializable
private class SelfDto(val success: Boolean, val result: ProfileDto? = null)

@Serializable
private class ProfileDto(
    val uuid: String,
    val uid: Long,
    val email: String? = null,
    val username: String? = null,
    val userNickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    val userBannerImage: String? = null,
    val userDataBootstrapHint: String? = null,
)

private fun String?.validHint(): String? = this?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]+")) }
