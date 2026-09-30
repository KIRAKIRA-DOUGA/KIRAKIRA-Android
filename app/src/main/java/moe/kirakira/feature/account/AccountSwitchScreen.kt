package moe.kirakira.feature.account

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.auth.SessionOperation
import moe.kirakira.data.auth.SessionOperationType
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRATheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    busy: Boolean = false,
    operation: SessionOperation? = null,
) {
    val scrollBehavior = rememberCollapsibleTopAppBarScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
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
                            enabled = !busy,
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
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    // Bottom inset scrolls with the content so the viewport reaches behind the navigation bar.
                    .padding(
                        start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    accounts.forEachIndexed { index, account ->
                        key(account.id) {
                            val onSelect = {
                                swipedAccountId = null
                                if (!busy && account.id != selectedAccountId) onSelectAccount(account.id)
                            }
                            val onRemove = {
                                swipedAccountId = null
                                if (!busy) onRemoveAccount(account.id)
                            }
                            val content: @Composable (Modifier, () -> Unit) -> Unit = { rowModifier, onClick ->
                                AccountRow(
                                    account = account,
                                    isSelected = account.id == selectedAccountId,
                                    editing = editing,
                                    enabled = !busy,
                                    operationType = operation?.takeIf {
                                        (it.targetUuid ?: GUEST_ACCOUNT_ID) == account.id &&
                                            it.type in setOf(SessionOperationType.SWITCH, SessionOperationType.REMOVE)
                                    }?.type,
                                    index = index,
                                    count = accounts.size,
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
                    enabled = !editing && !busy,
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                    verticalAlignment = Alignment.CenterVertically,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountRow(
    account: AccountItem,
    isSelected: Boolean,
    editing: Boolean,
    enabled: Boolean,
    operationType: SessionOperationType?,
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
    val avatar: @Composable () -> Unit = { AccountAvatar(url = account.avatar, size = 48.dp) }
    val headline: @Composable () -> Unit = {
        Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val supportingText = when {
        operationType == SessionOperationType.REMOVE -> stringResource(R.string.account_removing)
        operationType != null -> stringResource(R.string.account_switching)
        account.id == GUEST_ACCOUNT_ID -> null
        editing && isSelected -> currentAccount
        else -> account.handle
    }
    val supporting: (@Composable () -> Unit)? = supportingText?.let { subtitle ->
        {
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (operationType != null || editing && isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (operationType != null) Modifier.testTag("account_status_${account.id}")
                    else Modifier,
            )
        }
    }
    val trailing: @Composable () -> Unit = {
        // Selection, progress and editing share a centered slot, including the non-removable guest.
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            when {
                operationType != null -> {
                    LoadingIndicator(modifier = Modifier.size(32.dp).testTag("account_loading_${account.id}"))
                }
                !editing -> {
                    RadioButton(selected = isSelected, onClick = null, enabled = enabled)
                }
                account.id != GUEST_ACCOUNT_ID -> {
                    IconButton(
                        onClick = onRemove,
                        enabled = enabled,
                        modifier = Modifier.testTag("account_remove_${account.id}"),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_symbol_delete),
                            contentDescription = stringResource(R.string.account_remove_description, account.name),
                            tint = if (enabled) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    }
                }
            }
        }
    }

    if (editing) {
        SegmentedListItem(
            shapes = shapes,
            verticalAlignment = Alignment.CenterVertically,
            modifier = rowModifier,
            leadingContent = avatar,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    } else {
        SegmentedListItem(
            selected = isSelected,
            enabled = enabled,
            onClick = onSelect,
            shapes = shapes,
            verticalAlignment = Alignment.CenterVertically,
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
        AccountPreviewContent()
    }
}

@Preview(name = "Accounts · Dynamic color", showBackground = true)
@Composable
private fun AccountDynamicColorPreview() {
    KIRAKIRATheme(dynamicColor = true) {
        AccountPreviewContent()
    }
}

@Composable
private fun AccountPreviewContent() {
    AccountSwitchScreen(
        accounts = listOf(
            AccountItem(id = GUEST_ACCOUNT_ID, name = stringResource(R.string.account_guest)),
        ),
        selectedAccountId = GUEST_ACCOUNT_ID,
        editing = false,
        busy = false,
        operation = null,
        snackbarHostState = remember { SnackbarHostState() },
        onSelectAccount = {},
        onEditingChange = {},
        onAddAccount = {},
        onRemoveAccount = {},
        onBack = {},
    )
}
