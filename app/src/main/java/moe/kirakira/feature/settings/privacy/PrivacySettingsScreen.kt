@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.kirakira.feature.settings.privacy

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.kirakira.R
import moe.kirakira.data.settings.PrivacyItem
import moe.kirakira.data.settings.PrivacyVisibility
import moe.kirakira.feature.settings.SettingsColumn
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.feature.settings.SettingsSection
import moe.kirakira.feature.settings.SettingsSectionHeader
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ConnectedListGroup
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.IconBadge
import moe.kirakira.ui.components.IconBadgeTone
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.ShadowRadioButton
import moe.kirakira.ui.components.ShadowToggleButton
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.messageRes
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
        onSelectAll = model::selectAll,
        onOpenSelector = model::openSelector,
        onCloseSelector = model::closeSelector,
        onSelect = model::select,
        onRequestReset = model::requestReset,
        onCancelReset = model::cancelReset,
        onReset = model::reset,
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
    onSelectAll: (PrivacyVisibility) -> Unit,
    onOpenSelector: (PrivacyItem?) -> Unit,
    onCloseSelector: () -> Unit,
    onSelect: (PrivacyVisibility) -> Unit,
    onRequestReset: () -> Unit,
    onCancelReset: () -> Unit,
    onReset: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.messageFailure?.let { stringResource(it.messageRes()) }
        ?: state.message?.let { stringResource(it) }
    LaunchedEffect(state.revision, message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onDismissMessage()
        }
    }
    val resetVisible = signedIn && !sessionBusy && state.original != null
    val resetLabel = stringResource(R.string.privacy_reset)
    val workingDescription = stringResource(R.string.auth_working)
    SettingsScaffold(
        title = stringResource(R.string.settings_privacy),
        onBack = onBack,
        shadingIcon = R.drawable.ic_symbol_shield,
        modifier = modifier,
        imePadding = true,
        actions = {
            if (resetVisible) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(resetLabel) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = onRequestReset,
                        enabled = state.canReset,
                        modifier = Modifier.semantics {
                            contentDescription = resetLabel
                            if (state.busy) stateDescription = workingDescription
                        },
                    ) {
                        if (state.busy) {
                            IndeterminateCircularProgressIndicator(
                                modifier = Modifier.size(24.dp).clearAndSetSemantics {},
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(painterResource(R.drawable.ic_symbol_history), null, Modifier.size(24.dp))
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
            SettingsColumn(padding = padding) {
                when {
                    state.confirmationPending -> ContentUnavailableView(
                        state = ContentUnavailableState.ERROR,
                        title = stringResource(R.string.privacy_confirmation_pending),
                        description = state.error?.let { stringResource(it.messageRes()) },
                        presentation = ContentUnavailablePresentation.INLINE,
                        primaryAction = ContentUnavailableAction(
                            stringResource(R.string.settings_retry_sync), onRefresh, enabled = !state.busy,
                        ),
                    )
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
    if (state.confirmReset && signedIn && !sessionBusy) {
        ActionDialog(
            onDismissRequest = onCancelReset,
            icon = { Icon(painterResource(R.drawable.ic_symbol_shield), contentDescription = null) },
            title = { Text(stringResource(R.string.privacy_reset_title)) },
            text = { Text(stringResource(R.string.privacy_reset_message)) },
            actions = {
                ShadowButton(
                    onClick = onReset,
                    enabled = state.canReset,
                    destructive = true,
                ) {
                    Text(stringResource(R.string.privacy_reset))
                }
                TextButton(
                    contentPadding = ButtonDefaults.ContentPadding,
                    onClick = onCancelReset,
                ) {
                    Text(stringResource(R.string.account_cancel))
                }
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
    val uniformVisibility = state.values.values.distinct().singleOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionHeader(stringResource(R.string.privacy_set_all))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            PrivacyVisibility.entries.forEachIndexed { index, visibility ->
                val checked = uniformVisibility == visibility
                val label = stringResource(visibility.titleRes())
                val shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    PrivacyVisibility.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                ShadowToggleButton(
                    checked = checked,
                    onCheckedChange = { onSelectAll(visibility) },
                    enabled = state.editable,
                    icon = { Icon(painterResource(visibility.iconRes()), contentDescription = null) },
                    colors = ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    // Preserve the connected contour on press while retaining selection feedback.
                    shapes = shapes.copy(pressedShape = if (checked) shapes.checkedShape else shapes.shape),
                    modifier = Modifier.weight(1f).semantics {
                        role = Role.RadioButton
                        selected = checked
                    },
                ) {
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
    SettingsSection(title = stringResource(title)) {
        items.forEachIndexed { index, item ->
            val visibility = state.values.getValue(item)
            val label = stringResource(visibility.titleRes())
            SegmentedListItem(
                onClick = { onSelect(item) },
                enabled = state.editable,
                shapes = connectedListItemShapes(index, items.size),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = label },
                leadingContent = {
                    Icon(painterResource(item.iconRes()), contentDescription = null, modifier = Modifier.size(24.dp))
                },
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
    val chosen = state.selectedItem?.let { state.values[it] }
        ?: state.values.values.distinct().singleOrNull()
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
    PrivacyItem.BIRTHDAY -> R.drawable.ic_symbol_cake
    PrivacyItem.AGE -> R.drawable.ic_symbol_calendar_today
    PrivacyItem.FOLLOWING -> R.drawable.ic_symbol_person_add
    PrivacyItem.FOLLOWERS -> R.drawable.ic_symbol_group
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
        PrivacySettingsScreen(
            state = PrivacySettingsState(),
            signedIn = false,
            sessionBusy = false,
            onBack = {},
            onLogin = {},
            onRefresh = {},
            onSelectAll = {},
            onOpenSelector = {},
            onCloseSelector = {},
            onSelect = {},
            onRequestReset = {},
            onCancelReset = {},
            onReset = {},
            onDismissMessage = {},
        )
    }
}
