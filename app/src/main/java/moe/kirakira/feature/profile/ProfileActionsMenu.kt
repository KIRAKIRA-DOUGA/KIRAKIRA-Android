package moe.kirakira.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.ui.components.ExpressiveMenuPopup

internal data class ProfileMenuState(
    val accountRevision: Long,
    val blocked: Boolean = false,
    val hidden: Boolean = false,
    val enabled: Boolean = false,
    val canRetry: Boolean = false,
)

@Composable
internal fun ProfileActionsMenu(
    state: ProfileMenuState,
    onChangeRule: (RuleCategory) -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    showRelationshipActions: Boolean,
    modifier: Modifier = Modifier,
    anchor: @Composable (onClick: () -> Unit) -> Unit,
) {
    var expanded by remember(state.accountRevision) { mutableStateOf(false) }
    Box(modifier) {
        anchor { expanded = true }
        ExpressiveMenuPopup(expanded, onDismissRequest = { expanded = false }) {
            val groupCount = if (showRelationshipActions) 2 else 1
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = groupCount),
                containerColor = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                SelectableDropdownMenuItem(
                    selected = false,
                    onClick = {
                        expanded = false
                        onShare()
                    },
                    text = { Text(stringResource(R.string.share)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_symbol_share), contentDescription = null) },
                    shapes = MenuDefaults.itemShape(index = 0, count = 1),
                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                )
            }
            if (showRelationshipActions) {
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                ProfileRelationshipMenuGroup(state, onChangeRule = {
                    expanded = false
                    onChangeRule(it)
                }, onRetry = {
                    expanded = false
                    onRetry()
                })
            }
        }
    }
}

@Composable
private fun ProfileRelationshipMenuGroup(
    state: ProfileMenuState,
    onChangeRule: (RuleCategory) -> Unit,
    onRetry: () -> Unit,
) {
    DropdownMenuGroup(
        shapes = MenuDefaults.groupShape(index = 1, count = 2),
        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        val categories = listOf(RuleCategory.HIDE, RuleCategory.BLOCK)
        val count = if (state.canRetry) 3 else 2
        categories.forEachIndexed { index, category ->
            val block = category == RuleCategory.BLOCK
            val selected = if (block) state.blocked else state.hidden
            val label = if (block) {
                if (selected) R.string.profile_unblock_user else R.string.profile_block_user
            } else {
                if (selected) R.string.profile_unhide_user else R.string.profile_hide_user
            }
            val foreground = if (block && !selected) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            SelectableDropdownMenuItem(
                selected = selected,
                onClick = { onChangeRule(category) },
                enabled = state.enabled,
                text = { Text(stringResource(label)) },
                leadingIcon = {
                    Icon(
                        painterResource(if (block) R.drawable.ic_symbol_block else R.drawable.ic_symbol_visibility_off),
                        contentDescription = null,
                    )
                },
                trailingContent = if (selected) {
                    { Icon(painterResource(R.drawable.ic_symbol_check), contentDescription = null) }
                } else null,
                shapes = MenuDefaults.itemShape(index = index, count = count),
                colors = MenuDefaults.selectableItemColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    textColor = foreground,
                    leadingIconColor = foreground,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTrailingContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
        if (state.canRetry) {
            SelectableDropdownMenuItem(
                selected = false,
                onClick = onRetry,
                text = { Text(stringResource(R.string.content_unavailable_retry)) },
                shapes = MenuDefaults.itemShape(index = 2, count = count),
            )
        }
    }
}
