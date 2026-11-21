package moe.kirakira.feature.settings.profile

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import moe.kirakira.R
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.feature.settings.SettingsColumn
import moe.kirakira.feature.settings.SettingsErrorCard
import moe.kirakira.feature.settings.SettingsFormCard
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.feature.settings.SettingsSection
import moe.kirakira.feature.settings.settingsSearchAnchor
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.AppChipDefaults
import moe.kirakira.ui.components.CharacterCount
import moe.kirakira.ui.components.ContentLoadingView
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.FilledTextField
import moe.kirakira.ui.components.FlatTextButton
import moe.kirakira.ui.components.LocalTextFieldEdits
import moe.kirakira.ui.components.MenuPopup
import moe.kirakira.ui.components.ObserveTextFieldBinding
import moe.kirakira.ui.components.ShadowFilledIconButton
import moe.kirakira.ui.components.TextFieldBinding
import moe.kirakira.ui.components.TextFieldEdits
import moe.kirakira.ui.components.UsernameText
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.dateOnlyText
import moe.kirakira.ui.components.dismissKeyboardOnBlankTap
import moe.kirakira.ui.components.menuItemColors
import moe.kirakira.ui.components.menuItemShapes
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.components.rememberBoundTextField
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorDefaults

@Composable
internal fun ProfileEditorScreen(
    state: ProfileEditorState,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onEdit: (String, String) -> Unit,
    onPickAvatar: () -> Unit,
    onAddLabel: (String) -> Unit,
    onRemoveLabel: (Int) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    edits: TextFieldEdits = remember { TextFieldEdits() },
) {
    val labelInput = remember { TextFieldBinding(state.labelInput) }
    fun addLabel() {
        if (!state.editable) return
        onAddLabel(labelInput.editor.text.toString())
        labelInput.replace("")
    }
    val saveLabel = stringResource(
        when {
            state.saveUnconfirmed -> R.string.security_check_status
            state.completionOnly -> R.string.settings_retry_sync
            else -> R.string.profile_save
        },
    )
    val saveDescription = if (state.completionOnly && !state.saveUnconfirmed) stringResource(R.string.profile_finish_save) else null
    SettingsScaffold(
        title = stringResource(R.string.settings_profile),
        onBack = {
            edits.flush()
            onBack()
        },
        shadingIcon = R.drawable.ic_icon_badge,
        modifier = modifier.dismissKeyboardOnBlankTap(),
        imePadding = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (state.draft != null) {
                FlatTextButton(
                    onClick = {
                        edits.flush()
                        if (labelInput.editor.text.isNotBlank()) addLabel()
                        onSave()
                    },
                    enabled = (state.dirty || labelInput.editor.text.isNotBlank()) && !state.loading && !state.preparingImage,
                    loading = state.busy,
                ) {
                    Text(
                        text = saveLabel,
                        maxLines = 1,
                        softWrap = false,
                        modifier = if (saveDescription == null) Modifier else Modifier.clearAndSetSemantics {
                            contentDescription = saveDescription
                        },
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.loading && state.draft == null -> ContentLoadingView(modifier = Modifier.fillMaxSize().padding(padding))
            state.draft == null && state.error != null -> ContentUnavailableView(
                ContentUnavailableState.ERROR, description = stringResource(state.error.messageRes()), onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            state.draft == null -> ContentUnavailableView(
                ContentUnavailableState.EMPTY,
                title = stringResource(R.string.profile_sign_in), description = null,
                iconRes = R.drawable.ic_icon_badge,
                primaryAction = ContentUnavailableAction(stringResource(R.string.auth_sign_in), onLogin),
                modifier = Modifier.padding(padding),
            )
            else -> CompositionLocalProvider(LocalTextFieldEdits provides edits) {
                SettingsColumn(padding = padding) {
                    ProfileIdentity(state) {
                        edits.flush()
                        onPickAvatar()
                    }
                    if (state.error != null) SettingsErrorCard(stringResource(
                        when {
                            state.saveUnconfirmed -> R.string.profile_save_unconfirmed
                            state.completionOnly -> R.string.profile_saved_pending
                            else -> state.error.messageRes()
                        }))
                    SettingsSection {
                        SettingsFormCard {
                            ProfileTextField(state, "username", state.draft.username,
                                stringResource(R.string.auth_username), R.drawable.ic_icon_at, onEdit)
                            ProfileTextField(state, "nickname", state.draft.nickname,
                                stringResource(R.string.auth_nickname), R.drawable.ic_icon_person, onEdit)
                            ProfileTextField(state, "signature", state.draft.signature,
                                stringResource(R.string.profile_bio), R.drawable.ic_icon_edit, onEdit, multiline = true)
                        }
                    }
                    PersonalInformation(state, onEdit)
                    ProfileLabels(state, labelInput, ::addLabel, onRemoveLabel, onEdit)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileIdentity(state: ProfileEditorState, onPick: () -> Unit) {
    val draft = state.draft ?: return
    val scheme = MaterialTheme.colorScheme
    val ring = ThemeColorDefaults.settingsBackgroundColor()
    val bannerShape = MaterialTheme.shapes.large
    Column(Modifier.settingsSearchAnchor(stringResource(R.string.profile_edit_avatar)).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(BannerHeight + AvatarSize / 2)) {
            Image(
                painter = painterResource(R.drawable.profile_banner_placeholder),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(BannerHeight).clip(bannerShape),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.align(Alignment.BottomCenter).size(AvatarSize + AvatarRing * 2)) {
                Box(Modifier.matchParentSize().background(ring, CircleShape).padding(AvatarRing)) {
                    AccountAvatar(draft.avatar, size = AvatarSize)
                    state.avatar?.let {
                        AsyncImage(it, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    }
                }
                // TooltipBox applies its modifier to an internal anchor, so align its outer container.
                Box(Modifier.align(Alignment.BottomEnd)) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.profile_edit_avatar)) } },
                        state = rememberTooltipState(),
                    ) {
                        // The ring keeps the button legible over both the avatar and the banner.
                        Box(Modifier.background(ring, CircleShape).padding(AvatarRing)) {
                            ShadowFilledIconButton(
                                onClick = onPick,
                                enabled = state.editable,
                                loading = state.preparingImage,
                                shapes = IconButtonShapes(CircleShape, CircleShape),
                            ) {
                                ProfileIcon(R.drawable.ic_icon_edit, stringResource(R.string.profile_edit_avatar))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(draft.nickname.ifBlank { draft.username }.ifBlank { stringResource(R.string.settings_profile) },
            Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        UsernameText(
            username = draft.username,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val BannerHeight = 144.dp
private val AvatarSize = 112.dp
private val AvatarRing = 4.dp

@Composable
private fun ProfileTextField(
    state: ProfileEditorState, field: String, value: String, label: String,
    @DrawableRes icon: Int, onEdit: (String, String) -> Unit, multiline: Boolean = false,
) {
    val error = state.fieldErrors[field]
    FilledTextField(
        state = rememberBoundTextField(value, { onEdit(field, it) }, state.editable),
        modifier = Modifier.settingsSearchAnchor(label).fillMaxWidth(), enabled = state.editable,
        label = { Text(label) }, leadingIcon = { ProfileIcon(icon) },
        lineLimits = if (multiline) TextFieldLineLimits.MultiLine(3, 6) else TextFieldLineLimits.SingleLine,
        errorMessage = error?.let { stringResource(it) },
        supportingText = if (error != null || multiline) ({
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (error != null) Text(stringResource(error), Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (multiline) CharacterCount(count = value.length, limit = 200)
            }
        }) else null,
        keyboardOptions = KeyboardOptions(imeAction = if (multiline) ImeAction.Default else ImeAction.Next),
    )
}

@Composable
private fun PersonalInformation(state: ProfileEditorState, onEdit: (String, String) -> Unit) {
    val draft = state.draft ?: return
    var showBirthday by remember { mutableStateOf(false) }
    var showGender by remember { mutableStateOf(false) }
    val birthday = runCatching { LocalDate.parse(draft.birthday) }.getOrNull()
    val genderValues = listOf("", "male", "female", "other")
    val genderLabels = listOf(R.string.profile_not_set, R.string.profile_male, R.string.profile_female, R.string.profile_other)
    if (showBirthday) BirthdayDialog(draft.birthday, onDismiss = { showBirthday = false }, onSelect = {
        onEdit("birthday", it); showBirthday = false
    })
    SettingsSection {
        SegmentedListItem(
            onClick = { showBirthday = true }, enabled = state.editable,
            shapes = connectedListItemShapes(0, 2),
            modifier = Modifier.settingsSearchAnchor(stringResource(R.string.profile_birthday)),
            leadingContent = { ProfileListIcon(R.drawable.ic_icon_calendar) },
            supportingContent = {
                Text(
                    birthday?.let { dateOnlyText(it) }
                        ?: draft.birthday.ifBlank { stringResource(R.string.profile_not_set) },
                )
            },
            trailingContent = { ProfileListIcon(R.drawable.ic_icon_chevron_right) },
            content = { Text(stringResource(R.string.profile_birthday)) },
        )
        SegmentedListItem(
            onClick = { showGender = true }, enabled = state.editable,
            shapes = connectedListItemShapes(1, 2),
            modifier = Modifier.settingsSearchAnchor(stringResource(R.string.profile_gender)),
            leadingContent = { ProfileListIcon(R.drawable.ic_icon_person) },
            supportingContent = {
                Text(
                    genderValues.indexOf(draft.gender).takeIf { it >= 0 }?.let {
                        stringResource(genderLabels[it])
                    } ?: draft.gender,
                )
            },
            trailingContent = {
                Box {
                    Icon(
                        painter = painterResource(R.drawable.ic_icon_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).rotate(90f),
                    )
                    MenuPopup(
                        expanded = showGender,
                        onDismissRequest = { showGender = false },
                    ) {
                        val colors = menuItemColors()
                        val shapes = menuItemShapes()
                        genderValues.forEachIndexed { index, value ->
                            SelectableDropdownMenuItem(
                                selected = draft.gender == value,
                                text = { Text(stringResource(genderLabels[index])) },
                                onClick = {
                                    onEdit("gender", value)
                                    showGender = false
                                },
                                shapes = shapes,
                                colors = colors,
                                trailingContent = if (draft.gender == value) {
                                    { ProfileIcon(R.drawable.ic_icon_check) }
                                } else null,
                            )
                        }
                    }
                }
            },
            content = { Text(stringResource(R.string.profile_gender)) },
        )
    }
}

@Composable
private fun BirthdayDialog(value: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initial,
        yearRange = 1900..LocalDate.now().year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= today
        })
    val dateColors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    DatePickerDialog(onDismissRequest = onDismiss,
        colors = dateColors,
        confirmButton = { FlatTextButton(onClick = {
            dateState.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
        }, enabled = dateState.selectedDateMillis != null) { Text(stringResource(R.string.profile_done)) } },
        dismissButton = { Row {
            FlatTextButton(onClick = { onSelect("") }) { Text(stringResource(R.string.profile_clear)) }
            FlatTextButton(onClick = onDismiss) { Text(stringResource(R.string.account_cancel)) }
        } },
    ) {
        DatePicker(
            state = dateState,
            showModeToggle = true,
            colors = dateColors,
        )
    }
}

@Composable
private fun ProfileLabels(
    state: ProfileEditorState,
    binding: TextFieldBinding,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onEdit: (String, String) -> Unit,
) {
    val labels = state.draft?.labels.orEmpty()
    ObserveTextFieldBinding(binding, state.labelInput, { onEdit("labelInput", it) }, state.editable)
    SettingsSection {
        // The first chip row already reserves space above its visible pills for touch targets.
        SettingsFormCard(topPadding = if (labels.isEmpty()) 16.dp else 8.dp) {
            if (labels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    labels.forEachIndexed { index, label ->
                        key(label.id, label.labelName) {
                            InputChip(
                                selected = false,
                                onClick = { onRemove(index) },
                                enabled = state.editable,
                                shape = CircleShape,
                                border = null,
                                colors = AppChipDefaults.inputColors(),
                                label = { Text(label.labelName, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    ProfileIcon(R.drawable.ic_icon_close,
                                        stringResource(R.string.profile_remove_label, label.labelName))
                                },
                            )
                        }
                    }
                }
            }
            FilledTextField(
                state = binding.editor,
                modifier = Modifier.settingsSearchAnchor(stringResource(R.string.profile_add_label)).fillMaxWidth(),
                enabled = state.editable,
                label = { Text(stringResource(R.string.profile_add_label)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                leadingIcon = { ProfileIcon(R.drawable.ic_icon_tag) },
                trailingIcon = {
                    IconButton(onClick = onAdd, enabled = state.editable && binding.editor.text.isNotBlank()) {
                        ProfileIcon(R.drawable.ic_icon_add, stringResource(R.string.profile_add_label))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onKeyboardAction = { onAdd() },
            )
        }
    }
}

@Composable
private fun ProfileListIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
}

@Composable
internal fun ProfileIcon(@DrawableRes icon: Int, description: String? = null) {
    Icon(painterResource(icon), description)
}

@Preview(name = "Profile · English", locale = "en", showBackground = true)
@Preview(name = "资料 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Profile · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Profile · Large text", fontScale = 2f, showBackground = true)
@Composable
private fun ProfileEditorPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        val profile = AccountProfile("", 0, "")
        ProfileEditorScreen(ProfileEditorState(profile, profile, loading = false), {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
    }
}
