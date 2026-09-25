package moe.kirakira.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import moe.kirakira.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CommentToolbar(
    currentPage: Int,
    totalPages: Int,
    sort: CommentSort,
    ascending: Boolean,
    onOpenJump: () -> Unit,
    onSortChange: (CommentSort, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pageDescription = stringResource(R.string.video_comments_page_description, currentPage, totalPages)
    val direction = stringResource(
        if (ascending) R.string.video_comments_ascending else R.string.video_comments_descending,
    )
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onOpenJump, modifier = Modifier.semantics { contentDescription = pageDescription }) {
                Text(stringResource(R.string.video_comments_page_count, currentPage, totalPages))
            }
            Box {
                TextButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.semantics { stateDescription = direction },
                ) {
                    Icon(
                        painterResource(
                            if (ascending) R.drawable.ic_symbol_arrow_upward else R.drawable.ic_symbol_arrow_downward,
                        ),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(sort.labelRes))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    CommentSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            modifier = Modifier.semantics { selected = sort == option },
                            onClick = {
                                onSortChange(option, ascending)
                                menuExpanded = false
                            },
                            trailingIcon = {
                                if (sort == option) Icon(painterResource(R.drawable.ic_symbol_check), null)
                            },
                        )
                    }
                    HorizontalDivider()
                    listOf(true, false).forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (option) R.string.video_comments_ascending else R.string.video_comments_descending,
                                    ),
                                )
                            },
                            modifier = Modifier.semantics { selected = ascending == option },
                            onClick = {
                                onSortChange(sort, option)
                                menuExpanded = false
                            },
                            trailingIcon = {
                                if (ascending == option) Icon(painterResource(R.drawable.ic_symbol_check), null)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommentJumpDialog(
    currentPage: Int,
    totalPages: Int,
    maxFloor: Int,
    onDismiss: () -> Unit,
    onJump: (Boolean, Int) -> Unit,
) {
    var byFloor by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf(currentPage.toString()) }
    val limit = if (byFloor) maxFloor else totalPages
    val target = input.toIntOrNull()
    val valid = target != null && target in 1..limit
    val range = stringResource(R.string.video_comments_jump_range, limit)
    val jump = { if (valid) onJump(byFloor, target) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_comments_jump_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false, true).forEach { floorMode ->
                        FilterChip(
                            selected = byFloor == floorMode,
                            onClick = {
                                if (byFloor != floorMode) {
                                    byFloor = floorMode
                                    input = ""
                                }
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (floorMode) R.string.video_comments_floor else R.string.video_comments_page,
                                    ),
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            stringResource(if (byFloor) R.string.video_comments_floor else R.string.video_comments_page),
                        )
                    },
                    singleLine = true,
                    isError = input.isNotEmpty() && !valid,
                    supportingText = { Text(range) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { jump() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = jump, enabled = valid) { Text(stringResource(R.string.video_comments_jump)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.video_comments_cancel)) }
        },
    )
}
