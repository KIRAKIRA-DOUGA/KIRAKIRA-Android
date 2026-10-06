package moe.kirakira.feature.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import moe.kirakira.data.auth.SessionState

@Composable
internal fun AccountSwitchPage(
    state: SessionState,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddAccount: () -> Unit = {},
    onReauthenticate: (String) -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var pendingRemovalId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val accounts = listOf(AccountItem(GUEST_ACCOUNT_ID, stringResource(R.string.account_guest))) +
        state.accounts.map { account ->
            AccountItem(
                id = account.profile.uuid,
                name = account.profile.displayName,
                handle = if (account.needsLogin) stringResource(R.string.account_needs_login)
                    else account.profile.email,
                avatar = account.profile.avatar,
            )
        }

    AccountSwitchScreen(
        accounts = accounts,
        selectedAccountId = state.activeUuid ?: GUEST_ACCOUNT_ID,
        editing = editing,
        snackbarHostState = snackbarHostState,
        onSelectAccount = { id ->
            val account = state.accounts.find { it.profile.uuid == id }
            if (account?.needsLogin == true) onReauthenticate(account.profile.email) else onSelectAccount(id)
        },
        busy = state.isLoading || state.isBusy,
        operation = state.operation,
        onEditingChange = { editing = it },
        onAddAccount = onAddAccount,
        onRemoveAccount = { pendingRemovalId = it },
        onBack = onBack,
        modifier = modifier,
    )

    val pendingAccount = accounts.firstOrNull { it.id == pendingRemovalId && it.id != GUEST_ACCOUNT_ID }
    if (pendingAccount != null) {
        AlertDialog(
            onDismissRequest = { pendingRemovalId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.account_remove_title)) },
            text = { Text(stringResource(R.string.account_remove_message, pendingAccount.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveAccount(pendingAccount.id)
                        pendingRemovalId = null
                        if (accounts.size == 2) editing = false
                    },
                    modifier = Modifier.testTag("account_remove_confirm"),
                ) {
                    Text(stringResource(R.string.account_remove))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRemovalId = null },
                ) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
}
