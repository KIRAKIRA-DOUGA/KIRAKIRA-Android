package moe.kirakira.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.SessionState
import moe.kirakira.ui.components.messageRes

@Composable
internal fun SessionFeedback(
    state: SessionState,
    onDismissError: () -> Unit,
    onRetry: () -> Unit,
    onResetLocalAccounts: () -> Unit,
    onLogin: (String) -> Unit,
) {
    val error = state.error ?: return
    var confirmingReset by remember(error) { mutableStateOf(false) }
    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(stringResource(R.string.account_reset_local)) },
            text = { Text(stringResource(R.string.account_reset_local_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingReset = false
                    onDismissError()
                    onResetLocalAccounts()
                }) {
                    Text(stringResource(R.string.account_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) { Text(stringResource(R.string.account_cancel)) }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismissError,
        title = { Text(stringResource(R.string.account_session_problem)) },
        text = {
            val message = if (error == ApiFailure.REJECTED) {
                R.string.account_session_unverified
            } else {
                error.messageRes()
            }
            Column {
                state.accounts.find { it.profile.uuid == state.failedAccountUuid }?.let {
                    Text(it.profile.displayName)
                }
                Text(stringResource(message))
            }
        },
        confirmButton = {
            Column {
                if (error == ApiFailure.STORAGE) {
                    TextButton(onClick = { confirmingReset = true }) { Text(stringResource(R.string.account_reset_local)) }
                }
                TextButton(onClick = {
                    onDismissError()
                    onRetry()
                }) { Text(stringResource(R.string.auth_retry)) }
                if (error == ApiFailure.SESSION_EXPIRED || error == ApiFailure.REJECTED) {
                    TextButton(onClick = {
                        val email = state.accounts.find { it.profile.uuid == state.failedAccountUuid }
                            ?.profile?.email.orEmpty()
                        onDismissError()
                        onLogin(email)
                    }) { Text(stringResource(R.string.auth_sign_in)) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissError) { Text(stringResource(R.string.account_cancel)) }
        },
    )
}
