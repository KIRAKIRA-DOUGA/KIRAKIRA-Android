@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.kirakira.feature.settings.management

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.heightIn
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.ShadowFilledTonalButton
import moe.kirakira.ui.components.ShadowFloatingActionButton
import moe.kirakira.feature.settings.SettingsSectionHeader
import moe.kirakira.R
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.data.settings.RuleEntry
import moe.kirakira.data.settings.RulePage
import moe.kirakira.data.settings.RuleTag
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun BlockingOverviewPage(model: BlockingOverviewViewModel, onBack: () -> Unit, onLogin: () -> Unit, onCategory: (RuleCategory) -> Unit) {
    val state by model.counts.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    BlockingOverviewScreen(state, session.activeProfile != null && !session.isBusy, onBack, onLogin, model::refresh, onCategory)
}

@Composable
internal fun BlockingOverviewScreen(
    state: SettingsLoad<Map<RuleCategory, Int>>,
    signedIn: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onCategory: (RuleCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    ManagementFrame(
        title = stringResource(R.string.settings_blocking),
        shadingIcon = R.drawable.ic_symbol_block,
        signedIn = signedIn,
        loading = state.loading,
        loaded = state.data != null,
        error = state.error,
        onBack = onBack,
        onLogin = onLogin,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        listOf(
            R.string.management_people to listOf(RuleCategory.BLOCK, RuleCategory.HIDE),
            R.string.management_content to listOf(RuleCategory.TAG, RuleCategory.KEYWORD, RuleCategory.REGEX),
        ).forEach { (title, categories) ->
            item {
                SettingsSectionHeader(stringResource(title), modifier = Modifier.padding(bottom = 8.dp))
            }
            item("categories-$title") {
                ConnectedListGroup {
                    categories.forEachIndexed { index, category ->
                        SegmentedListItem(
                            onClick = { onCategory(category) },
                            shapes = connectedListItemShapes(index, categories.size),
                            leadingContent = {
                                Icon(
                                    painterResource(category.iconRes()),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    state.data?.get(category)?.let {
                                        Text(
                                            it.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Icon(painterResource(R.drawable.ic_symbol_chevron_right), null, Modifier.size(24.dp))
                                }
                            },
                            content = { Text(stringResource(category.titleRes())) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
internal fun RuleManagementPage(model: RuleManagementViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by model.rules.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val signedIn = session.activeProfile != null && !session.isBusy
    RuleManagementScreen(model.category, state, signedIn, busy, message, onBack, onLogin, model::refresh,
        model::more, model::openEditor, model::remove, model::dismissMessage)
    if (editor.open && signedIn) {
        RuleEditorSheet(model.category, editor, busy, model::input, model::submit, model::selectTag, model::closeEditor)
    }
}

@Composable
internal fun RuleManagementScreen(
    category: RuleCategory,
    state: SettingsLoad<RulePage>,
    signedIn: Boolean,
    busy: Boolean,
    message: Int?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (RuleEntry) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    ManagementFrame(
        title = stringResource(category.titleRes()), signedIn = signedIn, loading = state.loading,
        shadingIcon = category.iconRes(),
        loaded = state.data != null, error = state.error, onBack = onBack, onLogin = onLogin,
        onRefresh = onRefresh, modifier = modifier, appending = state.appending,
        message = message, onDismissMessage = onDismissMessage,
        onRetry = if (state.appending) onMore else onRefresh,
        floatingActionButton = {
            ShadowFloatingActionButton(
                onClick = onAdd,
                enabled = !busy,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_symbol_add),
                    contentDescription = stringResource(R.string.management_add),
                )
            }
        },
    ) {
        val page = state.data
        if (page != null) {
            item {
                SettingsSectionHeader(
                    stringResource(R.string.management_rule_count, page.total),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (page.entries.isEmpty()) item { ManagementEmpty(category.iconRes(), stringResource(R.string.management_no_rules)) }
            connectedItemsIndexed(
                groupKey = category.name,
                items = page.entries,
                key = { _, entry -> entry.value },
            ) { index, entry ->
                val user = category == RuleCategory.BLOCK || category == RuleCategory.HIDE
                val label = if (user) entry.name?.takeIf(String::isNotBlank) ?: entry.uid?.toString() ?: entry.value
                    else entry.tag?.displayName(language) ?: entry.value
                SegmentedListItem(
                    shapes = connectedListItemShapes(index, page.entries.size),
                    leadingContent = {
                        if (user) AccountAvatar(entry.avatar)
                        else Icon(painterResource(category.iconRes()), contentDescription = null, modifier = Modifier.size(24.dp))
                    },
                    supportingContent = {
                        Column {
                            if (user && entry.uid != null) Text(stringResource(R.string.management_uid, entry.uid))
                            entry.tag?.originalName()?.takeIf { it != label }?.let { Text(it) }
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.createdAt)))
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemove(entry) }, enabled = !busy && (!user || entry.uid != null)) {
                            Icon(painterResource(R.drawable.ic_symbol_close), stringResource(R.string.management_remove_named, label), Modifier.size(24.dp))
                        }
                    },
                    content = { Text(label, fontFamily = if (category == RuleCategory.REGEX) FontFamily.Monospace else null) },
                )
            }
            if (page.entries.size < page.total) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.loading && state.appending) IndeterminateCircularProgressIndicator()
                    else TextButton(
                        onClick = onMore,
                        enabled = !busy && !state.loading && (state.error == null || state.appending),
                    ) {
                        Text(stringResource(R.string.management_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleEditorSheet(
    category: RuleCategory,
    state: RuleEditor,
    busy: Boolean,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onTag: (RuleTag) -> Unit,
    onClose: () -> Unit,
) {
    val user = category == RuleCategory.BLOCK || category == RuleCategory.HIDE
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val currentBusy by rememberUpdatedState(busy)
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = if (state.profile != null || !state.tags.isNullOrEmpty()) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            confirmValueChange = { it != SheetValue.Hidden || !currentBusy },
        ),
        sheetGesturesEnabled = !busy,
    ) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(category.iconRes()), contentDescription = null, modifier = Modifier.size(24.dp))
            Text(stringResource(category.titleRes()), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = state.input, onValueChange = onInput, modifier = Modifier.fillMaxWidth(), enabled = !busy,
                singleLine = category != RuleCategory.REGEX, shape = MaterialTheme.shapes.large,
                label = { Text(stringResource(if (user) R.string.management_uid_label else category.titleRes())) },
                keyboardOptions = KeyboardOptions(keyboardType = if (user) KeyboardType.Number else KeyboardType.Text),
                isError = state.error != null,
                supportingText = {
                    if (state.error != null) Text(stringResource(state.error))
                    else if (category == RuleCategory.KEYWORD || category == RuleCategory.REGEX) {
                        Text(stringResource(R.string.management_character_count, state.input.trim().length))
                    }
                },
            )
            state.profile?.let { profile ->
                ConnectedListGroup {
                    SegmentedListItem(
                        shapes = connectedListItemShapes(0, 1),
                        leadingContent = { AccountAvatar(profile.avatar) },
                        supportingContent = { Text(stringResource(R.string.management_uid, profile.uid)) },
                        content = { Text(profile.name.ifBlank { profile.uid.toString() }) },
                    )
                }
            }
            if (busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IndeterminateCircularProgressIndicator()
                }
            }
            ShadowButton(
                onClick = onSubmit,
                enabled = !busy && state.input.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(stringResource(when {
                    user && state.profile == null -> R.string.management_lookup
                    category == RuleCategory.TAG -> R.string.management_search_tags
                    else -> R.string.management_confirm_add
                }))
            }
            state.tags?.let { tags ->
                if (tags.isEmpty()) Text(stringResource(R.string.management_no_tags))
                ConnectedListGroup {
                    tags.forEachIndexed { index, tag ->
                        SegmentedListItem(
                            onClick = { onTag(tag) },
                            enabled = !busy,
                            shapes = connectedListItemShapes(index, tags.size),
                            supportingContent = { Text("#${tag.id}") },
                            trailingContent = { Icon(painterResource(R.drawable.ic_symbol_add), null, Modifier.size(24.dp)) },
                            content = { Text(tag.displayName(language)) },
                        )
                    }
                }
            }
            ShadowFilledTonalButton(
                onClick = onClose,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Text(stringResource(R.string.account_cancel))
            }
        }
    }
}

@Preview(name = "Blocking · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Blocking · 中文", locale = "zh")
@Preview(name = "Blocking · Large text", fontScale = 2f)
@Composable
private fun BlockingPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        BlockingOverviewScreen(SettingsLoad(RuleCategory.entries.associateWith { 0 }), true, {}, {}, {}, {})
    }
}

@Preview(name = "Rules · Empty", locale = "zh")
@Preview(name = "Rules · Empty · Large text", fontScale = 2f)
@Composable
private fun EmptyRulesPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        RuleManagementScreen(RuleCategory.BLOCK, SettingsLoad(RulePage(emptyList(), 0, 1)), true, false, null,
            {}, {}, {}, {}, {}, {}, {})
    }
}
