package moe.kirakira.core.credentials

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.CancellationException
import moe.kirakira.BuildConfig

internal fun passwordCredentialGateway(context: Context): PasswordCredentialGateway =
    if (BuildConfig.SYSTEM_CREDENTIALS_ENABLED) SystemPasswordCredentialGateway(context.applicationContext)
    else DisabledPasswordCredentialGateway

private class SystemPasswordCredentialGateway(context: Context) : PasswordCredentialGateway {
    private val manager = CredentialManager.create(context)

    override suspend fun get(activity: Activity, email: String?): PasswordSelection = try {
        val credential = manager.getCredential(
            context = activity,
            request = GetCredentialRequest(
                credentialOptions = listOf(
                    GetPasswordOption(
                        allowedUserIds = email?.let(::setOf).orEmpty(),
                        isAutoSelectAllowed = false,
                    ),
                ),
            ),
        ).credential
        if (credential is PasswordCredential && (email == null || credential.id.equals(email, ignoreCase = true))) {
            PasswordSelection.Selected(PasswordDraft(credential.id, credential.password))
        } else PasswordSelection.Unavailable
    } catch (_: GetCredentialCancellationException) {
        PasswordSelection.Cancelled
    } catch (_: NoCredentialException) {
        PasswordSelection.Unavailable
    } catch (_: GetCredentialProviderConfigurationException) {
        PasswordSelection.Unavailable
    } catch (_: GetCredentialException) {
        PasswordSelection.Failed
    }

    override suspend fun save(activity: Activity, draft: PasswordDraft): PasswordSaveResult = try {
        manager.createCredential(activity, CreatePasswordRequest(id = draft.email, password = draft.password))
        PasswordSaveResult.SAVED
    } catch (_: CreateCredentialCancellationException) {
        PasswordSaveResult.CANCELLED
    } catch (_: CreateCredentialException) {
        PasswordSaveResult.FAILED
    }

    override suspend fun clearSession() {
        try {
            // Clears provider sign-in state only; it does not delete a stored password.
            manager.clearCredentialState(ClearCredentialStateRequest())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Best effort after the app's local session change has already succeeded.
        }
    }
}
