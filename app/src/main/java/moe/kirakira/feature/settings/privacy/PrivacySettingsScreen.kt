@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.kirakira.feature.settings.privacy

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.kirakira.R
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.data.settings.PrivacyItem
import moe.kirakira.data.settings.PrivacyVisibility
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.IconBadge
import moe.kirakira.ui.components.IconBadgeTone
import moe.kirakira.feature.settings.SettingsColumn
import moe.kirakira.feature.settings.SettingsDefaults
import moe.kirakira.feature.settings.SettingsSaveToolbar
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.feature.settings.SettingsSection
import moe.kirakira.feature.settings.SettingsSectionHeader
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun PrivacySettingsPage(model: PrivacySettingsViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val currentState = if (state.revision == session.revision) state
        else PrivacySettingsState(loading = session.activeProfile != null)
    PrivacySettingsScreen(
        state = currentState,
        signedIn = session.activeProfile != null,
        sessionBusy = session.isLoading || session.isBusy,
        onBack = { if (model.requestBack()) onBack() },
        onLogin = onLogin,
        onRefresh = model::refresh,
        onSave = model::save,
        onSelectAll = model::selectAll,
        onOpenSelector = model::openSelector,
        onCloseSelector = model::closeSelector,
        onSelect = model::select,
        onCancelDiscard = model::cancelDiscard,
        onDiscard = { model.discard(); onBack() },
        onDismissMessage = model::dismissMessage,
    )
}

@Composable
internal fun PrivacySettingsScreen(
    state: PrivacySettingsState,
    signedIn: Boolean,
    sessionBusy: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onSelectAll: (PrivacyVisibility) -> Unit,
    onOpenSelector: (PrivacyItem?) -> Unit,
    onCloseSelector: () -> Unit,
    onSelect: (PrivacyVisibility) -> Unit,
    onCancelDiscard: () -> Unit,
    onDiscard: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.messageFailure?.let { stringResource(it.messageRes()) }
        ?: state.message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onDismissMessage()
        }
    }
    val toolbarVisible = signedIn && state.original != null
    SettingsScaffold(
        title = stringResource(R.string.settings_privacy),
        onBack = onBack,
        shadingIcon = R.drawable.ic_symbol_shield,
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(
                snackbar,
                modifier = Modifier.padding(bottom = if (toolbarVisible) 72.dp else 0.dp),
            )
        },
    ) { padding ->
        val initialLoading = sessionBusy || (state.loading && state.original == null)
        if (initialLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                IndeterminateCircularProgressIndicator()
            }
        } else if (!signedIn) {
            ContentUnavailableView(
                state = ContentUnavailableState.EMPTY,
                modifier = Modifier.padding(padding),
                title = stringResource(R.string.management_sign_in),
                description = null,
                iconRes = R.drawable.ic_symbol_shield,
                presentation = ContentUnavailablePresentation.PAGE,
                primaryAction = ContentUnavailableAction(stringResource(R.string.management_login), onLogin),
            )
        } else {
            SettingsColumn(
                padding = padding,
                bottomClearance = if (toolbarVisible) SettingsDefaults.FloatingToolbarClearance else 0.dp,
                overlay = {
                    AnimatedVisibility(
                        visible = toolbarVisible,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 },
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                            slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    ) {
                        SettingsSaveToolbar(
                            label = stringResource(R.string.privacy_apply),
                            onSave = onSave,
                            enabled = state.editable && state.dirty,
                            busy = state.saving,
                            resetLabel = stringResource(R.string.privacy_reset),
                            resetBusy = state.loading,
                            resetEnabled = !state.busy,
                            onReset = onRefresh,
                        )
                    }
                },
            ) {
                when {
                    state.error != null -> ContentUnavailableView(
                        state = ContentUnavailableState.ERROR,
                        description = stringResource(state.error.messageRes()),
                        presentation = ContentUnavailablePresentation.INLINE,
                        onRetry = onRefresh,
                    )
                    state.original != null -> {
                        PrivacySetAllGroup(state, onSelectAll)
                        PrivacyGroup(
                            R.string.privacy_personal_information,
                            listOf(PrivacyItem.BIRTHDAY, PrivacyItem.AGE), state, onOpenSelector,
                        )
                        PrivacyGroup(
                            R.string.privacy_social_collections,
                            listOf(PrivacyItem.FOLLOWING, PrivacyItem.FOLLOWERS, PrivacyItem.FAVORITES),
                            state, onOpenSelector,
                        )
                    }
                }
            }
        }
    }
    if (state.sheetOpen && state.editable && signedIn && !sessionBusy) {
        PrivacyVisibilitySheet(state, onSelect, onCloseSelector)
    }
    if (state.confirmDiscard) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.privacy_discard_title)) },
            text = { Text(stringResource(R.string.privacy_discard_message)) },
            confirmButton = {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.privacy_discard)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelDiscard) { Text(stringResource(R.string.account_cancel)) }
            },
        )
    }
}

@Composable
private fun PrivacyVisibilityBadge(visibility: PrivacyVisibility, enabled: Boolean = true) {
    IconBadge(
        icon = visibility.iconRes(),
        shape = when (visibility) {
            PrivacyVisibility.PUBLIC -> MaterialShapes.Cookie9Sided
            PrivacyVisibility.FOLLOWING -> MaterialShapes.Clover4Leaf
            PrivacyVisibility.PRIVATE -> MaterialShapes.Circle
        }.toShape(),
        tone = when (visibility) {
            PrivacyVisibility.PUBLIC -> IconBadgeTone.PRIMARY
            PrivacyVisibility.FOLLOWING -> IconBadgeTone.SECONDARY
            PrivacyVisibility.PRIVATE -> IconBadgeTone.NEUTRAL
        },
        enabled = enabled,
    )
}

@Composable
private fun PrivacySetAllGroup(state: PrivacySettingsState, onSelectAll: (PrivacyVisibility) -> Unit) {
    val uniformVisibility = state.draft.values.distinct().singleOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionHeader(stringResource(R.string.privacy_set_all))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            PrivacyVisibility.entries.forEachIndexed { index, visibility ->
                val checked = uniformVisibility == visibility
                val label = stringResource(visibility.titleRes())
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { onSelectAll(visibility) },
                    enabled = state.editable,
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        PrivacyVisibility.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f).semantics {
                        role = Role.RadioButton
                        selected = checked
                    },
                ) {
                    Icon(painterResource(visibility.iconRes()), contentDescription = null)
                    Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                    Text(label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PrivacyGroup(
    title: Int,
    items: List<PrivacyItem>,
    state: PrivacySettingsState,
    onSelect: (PrivacyItem) -> Unit,
) {
    SettingsSection(stringResource(title)) {
        items.forEachIndexed { index, item ->
            val visibility = state.draft.getValue(item)
            val label = stringResource(visibility.titleRes())
            SegmentedListItem(
                onClick = { onSelect(item) },
                enabled = state.editable,
                shapes = connectedListItemShapes(index, items.size),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = label },
                leadingContent = { PrivacyVisibilityBadge(visibility, state.editable) },
                supportingContent = {
                    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = visibility,
                        transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
                        label = "visibilityLabel",
                    ) { v ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(v.iconRes()), contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(v.titleRes()))
                        }
                    }
                },
                trailingContent = { Icon(painterResource(R.drawable.ic_symbol_chevron_right), null, Modifier.size(24.dp)) },
                content = { Text(stringResource(item.titleRes())) },
            )
        }
    }
}

@Composable
private fun PrivacyVisibilitySheet(
    state: PrivacySettingsState,
    onSelect: (PrivacyVisibility) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = state.selectedItem?.let { state.draft[it] }
        ?: state.draft.values.distinct().singleOrNull()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(state.selectedItem?.titleRes() ?: R.string.privacy_set_all),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ConnectedListGroup(
                Modifier.selectableGroup(),
            ) {
                PrivacyVisibility.entries.forEachIndexed { index, visibility ->
                    val interactionSource = remember { MutableInteractionSource() }
                    SegmentedListItem(
                        onClick = { onSelect(visibility) },
                        interactionSource = interactionSource,
                        shapes = connectedListItemShapes(index, PrivacyVisibility.entries.size),
                        modifier = Modifier.fillMaxWidth().semantics {
                            role = Role.RadioButton
                            selected = chosen == visibility
                        },
                        leadingContent = { PrivacyVisibilityBadge(visibility) },
                        trailingContent = {
                            ShadowRadioButton(
                                selected = chosen == visibility,
                                onClick = null,
                                interactionSource = interactionSource,
                            )
                        },
                        content = { Text(stringResource(visibility.titleRes())) },
                    )
                }
            }
        }
    }
}

private fun PrivacyItem.titleRes(): Int = when (this) {
    PrivacyItem.BIRTHDAY -> R.string.profile_birthday
    PrivacyItem.AGE -> R.string.privacy_age
    PrivacyItem.FOLLOWING -> R.string.me_following_count
    PrivacyItem.FOLLOWERS -> R.string.me_followers_count
    PrivacyItem.FAVORITES -> R.string.me_favorites
}
private fun PrivacyItem.iconRes(): Int = when (this) {
    PrivacyItem.BIRTHDAY -> R.drawable.ic_symbol_calendar_today
    PrivacyItem.AGE -> R.drawable.ic_symbol_schedule
    PrivacyItem.FOLLOWING -> R.drawable.ic_symbol_person_add
    PrivacyItem.FOLLOWERS -> R.drawable.ic_symbol_person
    PrivacyItem.FAVORITES -> R.drawable.ic_symbol_star
}
private fun PrivacyVisibility.titleRes(): Int = when (this) {
    PrivacyVisibility.PUBLIC -> R.string.privacy_public
    PrivacyVisibility.FOLLOWING -> R.string.privacy_followers_only
    PrivacyVisibility.PRIVATE -> R.string.privacy_hidden
}
private fun PrivacyVisibility.iconRes(): Int = when (this) {
    PrivacyVisibility.PUBLIC -> R.drawable.ic_symbol_visibility
    PrivacyVisibility.FOLLOWING -> R.drawable.ic_symbol_person_add
    PrivacyVisibility.PRIVATE -> R.drawable.ic_symbol_visibility_off
}

@Preview(name = "Privacy · Guest", locale = "en", showBackground = true)
@Preview(name = "隐私 · 游客", locale = "zh", showBackground = true)
@Preview(name = "Privacy · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Privacy · Large text", fontScale = 2f, showBackground = true)
@Composable
private fun PrivacySettingsPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        PrivacySettingsScreen(PrivacySettingsState(), false, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}
