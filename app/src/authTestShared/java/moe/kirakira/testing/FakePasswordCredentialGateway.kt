package moe.kirakira.testing

import android.app.Activity
import moe.kirakira.core.credentials.PasswordCredentialGateway
import moe.kirakira.core.credentials.PasswordDraft
import moe.kirakira.core.credentials.PasswordSaveResult
import moe.kirakira.core.credentials.PasswordSelection

internal class FakePasswordCredentialGateway : PasswordCredentialGateway {
    val requestedEmails = mutableListOf<String?>()
    var saveCount = 0
        private set
    var clearCount = 0
        private set
    var getResult: suspend () -> PasswordSelection = { PasswordSelection.Cancelled }
    var saveResult: suspend (PasswordDraft) -> PasswordSaveResult = { PasswordSaveResult.CANCELLED }

    override suspend fun get(activity: Activity, email: String?): PasswordSelection {
        requestedEmails += email
        return getResult()
    }

    override suspend fun save(activity: Activity, draft: PasswordDraft): PasswordSaveResult {
        saveCount++
        return saveResult(draft)
    }

    var clearResult: suspend () -> Unit = {}
    override suspend fun clearSession() {
        clearCount++
        clearResult()
    }
}
