package moe.kirakira.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.ui.components.MenuDivider
import moe.kirakira.ui.components.MenuPopup
import moe.kirakira.ui.components.menuItemColors
import moe.kirakira.ui.components.menuItemShapes

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
        MenuPopup(expanded, onDismissRequest = { expanded = false }) {
            SelectableDropdownMenuItem(
                selected = false,
                onClick = {
                    expanded = false
                    onShare()
                },
                text = { Text(stringResource(R.string.share)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_icon_share), contentDescription = null) },
                shapes = menuItemShapes(),
                colors = menuItemColors(),
            )
            if (showRelationshipActions) {
                MenuDivider()
                ProfileRelationshipMenuItems(state, onChangeRule = {
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
private fun ProfileRelationshipMenuItems(
    state: ProfileMenuState,
    onChangeRule: (RuleCategory) -> Unit,
    onRetry: () -> Unit,
) {
    val shapes = menuItemShapes()
    listOf(RuleCategory.HIDE, RuleCategory.BLOCK).forEach { category ->
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
                    painterResource(if (block) R.drawable.ic_icon_block else R.drawable.ic_icon_visibility_off),
                    contentDescription = null,
                )
            },
            trailingContent = if (selected) {
                { Icon(painterResource(R.drawable.ic_icon_check), contentDescription = null) }
            } else null,
            shapes = shapes,
            colors = menuItemColors(textColor = foreground, leadingIconColor = foreground),
        )
    }
    if (state.canRetry) {
        SelectableDropdownMenuItem(
            selected = false,
            onClick = onRetry,
            text = { Text(stringResource(R.string.content_unavailable_retry)) },
            shapes = shapes,
            colors = menuItemColors(),
        )
    }
}
