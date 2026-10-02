package moe.kirakira.data.auth

import kotlinx.serialization.Serializable
import moe.kirakira.core.network.ApiFailure

@Serializable
internal data class AccountProfile(
    val uuid: String,
    val uid: Long,
    val email: String,
    val username: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val signature: String = "",
    val banner: String? = null,
    val birthday: String = "",
    val gender: String = "",
    val labels: List<ProfileLabel> = emptyList(),
) {
    val displayName: String get() = nickname.ifBlank { username }.ifBlank { email }
}

@Serializable
internal data class ProfileLabel(val id: Int, val labelName: String)

internal data class SavedAccount(val profile: AccountProfile, val needsLogin: Boolean)

internal enum class SessionOperationType { INITIALIZE, SWITCH, REMOVE, RESET }
internal data class SessionOperation(val type: SessionOperationType, val targetUuid: String? = null)

internal data class SessionState(
    val accounts: List<SavedAccount> = emptyList(),
    val activeUuid: String? = null,
    val isLoading: Boolean = true,
    val operation: SessionOperation? = null,
    val error: ApiFailure? = null,
    val failedAccountUuid: String? = null,
    val revision: Long = 0,
) {
    val isBusy: Boolean get() = operation != null
    val activeProfile: AccountProfile? get() = accounts.find { it.profile.uuid == activeUuid }?.profile
}

// Plain classes deliberately avoid generated toString() methods containing session secrets.
@Serializable
internal class StoredAccount(
    val profile: AccountProfile,
    val token: String? = null,
    val requiresLogin: Boolean = false,
    val userDataBootstrapHint: String? = null,
) {
    fun homeCookie(): String? = userDataBootstrapHint?.let { hint ->
        require(hint.matches(Regex("[a-zA-Z0-9_-]+")))
        "uuid=${profile.uuid}; uid=${profile.uid}; user-data-bootstrap-hint=$hint"
    }

    fun cookie(): String {
        val value = token ?: throw moe.kirakira.core.network.ApiException(ApiFailure.SESSION_EXPIRED)
        require(value.all { it.code in 0x21..0x7E && it != ';' && it != ',' })
        require(profile.uuid.matches(Regex("[a-zA-Z0-9-]+")))
        return "uuid=${profile.uuid}; uid=${profile.uid}; token=$value"
    }
}

@Serializable
internal class StoredSessions(
    val version: Int = 1,
    val baseUrl: String,
    val accounts: List<StoredAccount> = emptyList(),
    val activeUuid: String? = null,
)
