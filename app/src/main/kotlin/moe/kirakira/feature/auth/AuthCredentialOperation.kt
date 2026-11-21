package moe.kirakira.feature.auth

import moe.kirakira.core.credentials.PasswordDraft

/** Entry-local one-shot operations. Never put these objects in routes, SavedState or logs. */
internal sealed class AuthCredentialOperation(val id: Long) {
    class Get(id: Long, val email: String?, val automatic: Boolean) : AuthCredentialOperation(id)
    class Save(id: Long, val draft: PasswordDraft, val passwordReset: Boolean) : AuthCredentialOperation(id)
}
