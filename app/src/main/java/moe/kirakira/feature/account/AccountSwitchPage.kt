package moe.kirakira.feature.account

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import moe.kirakira.data.auth.SessionState
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ShadowButton

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
                handle = account.profile.username.takeIf { it.isNotBlank() }?.let { "@$it" },
                avatar = account.profile.avatar,
                needsLogin = account.needsLogin,
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
        ActionDialog(
            onDismissRequest = { pendingRemovalId = null },
            icon = { Icon(painterResource(R.drawable.ic_symbol_logout), contentDescription = null) },
            title = { Text(stringResource(R.string.account_remove_title)) },
            text = { Text(stringResource(R.string.account_remove_message, pendingAccount.name)) },
            actions = {
                ShadowButton(
                    onClick = {
                        onRemoveAccount(pendingAccount.id)
                        pendingRemovalId = null
                        if (accounts.size == 2) editing = false
                    },
                    modifier = Modifier.testTag("account_remove_confirm"),
                    destructive = true,
                ) {
                    Text(stringResource(R.string.settings_log_out))
                }
                TextButton(
                    contentPadding = ButtonDefaults.ContentPadding,
                    onClick = { pendingRemovalId = null },
                ) {
                    Text(stringResource(R.string.account_cancel))
                }
            },
        )
    }
}
