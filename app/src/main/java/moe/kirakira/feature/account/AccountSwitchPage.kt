package moe.kirakira.feature.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import moe.kirakira.R

@Composable
internal fun AccountSwitchPage(
    state: DemoAccountState,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var pendingRemovalId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val loginUnavailable = stringResource(R.string.account_login_unavailable)
    val accounts = state.accounts.map { account ->
        AccountItem(
            id = account.id,
            name = stringResource(account.nameRes),
            handle = account.handleRes?.let { stringResource(it) },
        )
    }

    AccountSwitchScreen(
        accounts = accounts,
        selectedAccountId = state.selectedId,
        editing = editing,
        snackbarHostState = snackbarHostState,
        onSelectAccount = onSelectAccount,
        onEditingChange = { editing = it },
        onAddAccount = {
            scope.launch {
                if (snackbarHostState.currentSnackbarData == null) {
                    snackbarHostState.showSnackbar(loginUnavailable)
                }
            }
        },
        onRemoveAccount = { pendingRemovalId = it },
        onBack = onBack,
        modifier = modifier,
    )

    val pendingAccount = accounts.firstOrNull { it.id == pendingRemovalId && it.id != GUEST_ACCOUNT_ID }
    if (pendingAccount != null) {
        AlertDialog(
            onDismissRequest = { pendingRemovalId = null },
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
                TextButton(onClick = { pendingRemovalId = null }) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
}
