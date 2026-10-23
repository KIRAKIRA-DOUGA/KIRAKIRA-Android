package moe.kirakira.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.SessionNotice
import moe.kirakira.data.auth.SessionState
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.DestructiveTextButton
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.messageRes

@Composable
internal fun SessionFeedback(
    state: SessionState,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit,
    onRetry: () -> Unit,
    onResetLocalAccounts: () -> Unit,
    onLogin: (String) -> Unit,
) {
    val error = state.error
    if (error == null) {
        when (state.notice) {
            SessionNotice.LEGACY_SIGN_IN_REQUIRED -> ActionDialog(
                onDismissRequest = onDismissNotice,
                icon = { Icon(painterResource(R.drawable.ic_symbol_info), contentDescription = null) },
                title = { Text(stringResource(R.string.account_needs_login)) },
                text = { Text(stringResource(R.string.account_migration_sign_in_required)) },
                actions = {
                    ShadowButton(
                        onClick = {
                            onDismissNotice()
                            onLogin("")
                        },
                    ) { Text(stringResource(R.string.auth_sign_in)) }
                    TextButton(
                        onClick = onDismissNotice,
                    ) {
                        Text(stringResource(R.string.account_continue))
                    }
                },
            )
            null -> Unit
        }
        return
    }
    val isConnectionFailure = error == ApiFailure.NETWORK || error == ApiFailure.TIMEOUT
    var confirmingReset by remember(error) { mutableStateOf(false) }
    if (confirmingReset) {
        ActionDialog(
            onDismissRequest = { confirmingReset = false },
            icon = { Icon(painterResource(R.drawable.ic_symbol_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.account_reset_local)) },
            text = { Text(stringResource(R.string.account_reset_local_message)) },
            actions = {
                ShadowButton(
                    onClick = {
                        confirmingReset = false
                        onDismissError()
                        onResetLocalAccounts()
                    },
                    destructive = true,
                ) {
                    Text(stringResource(R.string.account_remove))
                }
                TextButton(
                    onClick = { confirmingReset = false },
                ) { Text(stringResource(R.string.account_cancel)) }
            },
        )
        return
    }
    ActionDialog(
        onDismissRequest = onDismissError,
        icon = { Icon(painterResource(R.drawable.ic_symbol_error), contentDescription = null) },
        title = {
            val title = when (error) {
                ApiFailure.NETWORK -> R.string.account_connection_error_title
                ApiFailure.TIMEOUT -> R.string.account_connection_timeout_title
                ApiFailure.SERVER, ApiFailure.INVALID_RESPONSE, ApiFailure.REJECTED,
                ApiFailure.SESSION_EXPIRED, ApiFailure.RATE_LIMITED, ApiFailure.DAILY_LIMIT,
                ApiFailure.STORAGE, ApiFailure.STORAGE_FULL,
                ApiFailure.STORAGE_DATA -> R.string.account_session_problem
            }
            Text(stringResource(title))
        },
        text = {
            val message = if (isConnectionFailure) {
                R.string.account_connection_error_message
            } else if (error == ApiFailure.REJECTED) {
                R.string.account_session_unverified
            } else {
                error.messageRes()
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.accounts.find { it.profile.uuid == state.failedAccountUuid }?.let {
                    Text(it.profile.displayName)
                }
                Text(stringResource(message))
            }
        },
        actions = {
            val needsLogin = error == ApiFailure.SESSION_EXPIRED || error == ApiFailure.REJECTED
            val retry = {
                onDismissError()
                onRetry()
            }
            if (needsLogin) {
                ShadowButton(
                    onClick = {
                        val email = state.accounts.find { it.profile.uuid == state.failedAccountUuid }
                            ?.profile?.email.orEmpty()
                        onDismissError()
                        onLogin(email)
                    },
                ) { Text(stringResource(R.string.auth_sign_in)) }
                TextButton(onClick = retry) {
                    Text(stringResource(R.string.auth_retry))
                }
            } else {
                ShadowButton(onClick = retry) {
                    Text(stringResource(R.string.auth_retry))
                }
            }
            if (error.isStorageFailure) {
                DestructiveTextButton(
                    onClick = { confirmingReset = true },
                ) { Text(stringResource(R.string.account_reset_local)) }
            }
            TextButton(onClick = onDismissError) {
                val label = if (isConnectionFailure) R.string.account_continue else R.string.account_cancel
                Text(stringResource(label))
            }
        },
    )
}
