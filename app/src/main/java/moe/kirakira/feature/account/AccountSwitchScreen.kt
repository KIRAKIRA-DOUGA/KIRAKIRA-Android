package moe.kirakira.feature.account

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.PlaceholderAvatar
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun AccountSwitchScreen(
    accounts: List<AccountItem>,
    selectedAccountId: String,
    editing: Boolean,
    snackbarHostState: SnackbarHostState,
    onSelectAccount: (String) -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()
    var swipedAccountId by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    LaunchedEffect(editing, scrollState.isScrollInProgress) {
        if (editing || scrollState.isScrollInProgress) swipedAccountId = null
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("account_switch_screen"),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            CollapsibleTopAppBar(
                title = stringResource(R.string.settings_switch_account),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                backButtonModifier = Modifier.testTag("account_back"),
                actions = {
                    if (accounts.any { it.id != GUEST_ACCOUNT_ID }) {
                        TextButton(
                            onClick = {
                                swipedAccountId = null
                                onEditingChange(!editing)
                            },
                            modifier = Modifier.testTag("account_edit"),
                        ) {
                            Text(stringResource(if (editing) R.string.account_done else R.string.account_edit))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    accounts.forEachIndexed { index, account ->
                        key(account.id) {
                            val onSelect = {
                                swipedAccountId = null
                                if (account.id != selectedAccountId) onSelectAccount(account.id)
                            }
                            val onRemove = {
                                swipedAccountId = null
                                onRemoveAccount(account.id)
                            }
                            val content: @Composable (Modifier, () -> Unit) -> Unit = { rowModifier, onClick ->
                                AccountRow(
                                    account = account,
                                    isSelected = account.id == selectedAccountId,
                                    editing = editing,
                                    index = index,
                                    count = accounts.size + 1,
                                    onSelect = onClick,
                                    onRemove = onRemove,
                                    modifier = rowModifier,
                                )
                            }
                            if (!editing && account.id != GUEST_ACCOUNT_ID) {
                                SwipeToRemoveAccount(
                                    accountId = account.id,
                                    removeLabel = stringResource(R.string.account_remove_description, account.name),
                                    active = swipedAccountId == account.id,
                                    onSwipeStarted = { swipedAccountId = account.id },
                                    onClose = { swipedAccountId = null },
                                    onSelect = onSelect,
                                    onRemove = onRemove,
                                    content = content,
                                )
                            } else {
                                content(Modifier, onSelect)
                            }
                        }
                    }
                }
                SegmentedListItem(
                    onClick = {
                        swipedAccountId = null
                        onAddAccount()
                    },
                    enabled = !editing,
                    shapes = ListItemDefaults.segmentedShapes(index = accounts.size, count = accounts.size + 1),
                    modifier = Modifier.fillMaxWidth().testTag("account_add"),
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = if (editing) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (editing) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_symbol_add), contentDescription = null)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.account_add))
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountItem,
    isSelected: Boolean,
    editing: Boolean,
    index: Int,
    count: Int,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentAccount = stringResource(R.string.account_current)
    val rowModifier = modifier.fillMaxWidth().testTag("account_${account.id}").semantics(mergeDescendants = true) {
        selected = isSelected
        if (isSelected) stateDescription = currentAccount
    }
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val avatar: @Composable () -> Unit = { PlaceholderAvatar(size = 48.dp) }
    val headline: @Composable () -> Unit = { Text(account.name) }
    val supporting: (@Composable () -> Unit)? = if (account.handle != null || (editing && isSelected)) {
        {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                account.handle?.let { Text(it) }
                if (editing && isSelected) {
                    Text(
                        text = currentAccount,
                        modifier = Modifier.clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    } else null
    val trailing: (@Composable () -> Unit)? = when {
        !editing -> {
            { RadioButton(selected = isSelected, onClick = null) }
        }
        account.id != GUEST_ACCOUNT_ID -> {
            {
                IconButton(onClick = onRemove, modifier = Modifier.testTag("account_remove_${account.id}")) {
                    Icon(
                        painterResource(R.drawable.ic_symbol_delete),
                        contentDescription = stringResource(R.string.account_remove_description, account.name),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        else -> null
    }

    if (editing) {
        SegmentedListItem(
            shapes = shapes,
            modifier = rowModifier,
            leadingContent = avatar,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    } else {
        SegmentedListItem(
            selected = isSelected,
            onClick = onSelect,
            shapes = shapes,
            modifier = rowModifier,
            leadingContent = avatar,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    }
}

@Preview(name = "Accounts · English", locale = "en", showBackground = true)
@Preview(name = "账户 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Accounts · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Accounts · Narrow, large text", widthDp = 320, fontScale = 2f, showBackground = true)
@Preview(name = "Accounts · Wide", widthDp = 840, showBackground = true)
@Composable
private fun AccountSwitchPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        AccountSwitchPage(DemoAccountState(), onSelectAccount = {}, onRemoveAccount = {}, onBack = {})
    }
}

@Preview(name = "Accounts · Editing, long name", widthDp = 320, fontScale = 2f, showBackground = true)
@Composable
private fun AccountEditingPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        AccountSwitchScreen(
            accounts = listOf(
                AccountItem(GUEST_ACCOUNT_ID, "Guest"),
                AccountItem("sample", "A very long demonstration account name", "@long_sample_handle"),
            ),
            selectedAccountId = "sample",
            editing = true,
            snackbarHostState = remember { SnackbarHostState() },
            onSelectAccount = {},
            onEditingChange = {},
            onAddAccount = {},
            onRemoveAccount = {},
            onBack = {},
        )
    }
}

@Preview(name = "Accounts · Dynamic color", showBackground = true)
@Composable
private fun AccountDynamicColorPreview() {
    KIRAKIRATheme(dynamicColor = true) {
        AccountSwitchPage(DemoAccountState(), onSelectAccount = {}, onRemoveAccount = {}, onBack = {})
    }
}
