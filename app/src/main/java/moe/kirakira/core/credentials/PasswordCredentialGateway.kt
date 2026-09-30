package moe.kirakira.core.credentials

import android.app.Activity

/** Secrets are intentionally plain classes: no generated toString, serialization or saved state. */
internal class PasswordDraft(val email: String, val password: String)

internal sealed interface PasswordSelection {
    class Selected(val draft: PasswordDraft) : PasswordSelection
    data object Cancelled : PasswordSelection
    data object Unavailable : PasswordSelection
    data object Failed : PasswordSelection
}

internal enum class PasswordSaveResult { SAVED, CANCELLED, FAILED }

/** The current Activity hosts UI; implementations must never retain it between requests. */
internal interface PasswordCredentialGateway {
    suspend fun get(activity: Activity, email: String?): PasswordSelection
    suspend fun save(activity: Activity, draft: PasswordDraft): PasswordSaveResult
    suspend fun clearSession()
}

internal object DisabledPasswordCredentialGateway : PasswordCredentialGateway {
    override suspend fun get(activity: Activity, email: String?) = PasswordSelection.Unavailable
    override suspend fun save(activity: Activity, draft: PasswordDraft) = PasswordSaveResult.CANCELLED
    override suspend fun clearSession() = Unit
}
