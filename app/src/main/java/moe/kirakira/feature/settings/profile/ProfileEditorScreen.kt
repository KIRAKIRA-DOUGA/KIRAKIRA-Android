package moe.kirakira.feature.settings.profile

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import moe.kirakira.ui.components.ShadowFilledIconButton
import moe.kirakira.feature.settings.SettingsColumn
import moe.kirakira.feature.settings.SettingsDefaults
import moe.kirakira.feature.settings.SettingsErrorCard
import moe.kirakira.feature.settings.SettingsFormCard
import moe.kirakira.feature.settings.SettingsSaveToolbar
import moe.kirakira.feature.settings.SettingsScaffold
import moe.kirakira.feature.settings.SettingsSection
import moe.kirakira.R
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.connectedListItemShadow
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorDefaults

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_profile),
        onBack = onBack,
        modifier = modifier,
        imePadding = true,
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = if (state.draft != null) 72.dp else 0.dp))
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                IndeterminateCircularProgressIndicator()
            }
            state.draft == null && state.error != null -> ContentUnavailableView(
                ContentUnavailableState.ERROR, description = stringResource(state.error.messageRes()), onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            state.draft == null -> ContentUnavailableView(
                ContentUnavailableState.EMPTY,
                title = stringResource(R.string.profile_sign_in), description = null,
                iconRes = R.drawable.ic_symbol_person,
                primaryAction = ContentUnavailableAction(stringResource(R.string.auth_sign_in), onLogin),
                modifier = Modifier.padding(padding),
            )
            else -> SettingsColumn(
                padding = padding,
                bottomClearance = SettingsDefaults.FloatingToolbarClearance,
                overlay = {
                    SettingsSaveToolbar(
                        label = stringResource(if (state.completionOnly) R.string.profile_finish_save else R.string.profile_save),
                        onSave = onSave,
                        enabled = state.dirty && !state.busy && !state.preparingImage,
                        busy = state.busy,
                        icon = R.drawable.ic_symbol_save,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                    )
                },
            ) {
                ProfileIdentity(state, onPickAvatar)
                if (state.error != null) SettingsErrorCard(stringResource(
                    if (state.completionOnly) R.string.profile_saved_pending else state.error.messageRes()))
                SettingsSection(stringResource(R.string.profile_basic)) {
                    SettingsFormCard {
                        ProfileTextField(state, "username", state.draft.username,
                            stringResource(R.string.auth_username), R.drawable.ic_symbol_alternate_email, onEdit)
                        ProfileTextField(state, "nickname", state.draft.nickname,
                            stringResource(R.string.auth_nickname), R.drawable.ic_symbol_person, onEdit)
                        ProfileTextField(state, "signature", state.draft.signature,
                            stringResource(R.string.profile_bio), R.drawable.ic_symbol_edit, onEdit, multiline = true)
                    }
                }
                PersonalInformation(state, onEdit)
                ProfileLabels(state, onAddLabel, onRemoveLabel, onEdit)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileIdentity(state: ProfileEditorState, onPick: () -> Unit) {
    val draft = state.draft ?: return
    val scheme = MaterialTheme.colorScheme
    val ring = ThemeColorDefaults.settingsBackgroundColor()
    val bannerShape = MaterialTheme.shapes.extraLarge
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.profile_edit_avatar)) } },
                    state = rememberTooltipState(), modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    // The ring keeps the button legible over both the avatar and the banner.
                    Box(Modifier.background(ring, CircleShape).padding(AvatarRing)) {
                        ShadowFilledIconButton(onClick = onPick, enabled = state.editable && !state.preparingImage,
                            shapes = IconButtonDefaults.shapes()) {
                            if (state.preparingImage) {
                                IndeterminateCircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                ProfileIcon(R.drawable.ic_symbol_edit, stringResource(R.string.profile_edit_avatar))
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
        if (draft.username.isNotBlank()) Text("@${draft.username}",
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
            color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    OutlinedTextField(
        value = value, onValueChange = { onEdit(field, it) },
        modifier = Modifier.fillMaxWidth(), enabled = state.editable,
        label = { Text(label) }, leadingIcon = { ProfileIcon(icon) },
        shape = MaterialTheme.shapes.large,
        singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 6 else 1,
        isError = error != null,
        supportingText = if (error != null || multiline) ({
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (error != null) Text(stringResource(error), Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (multiline) Text(stringResource(R.string.profile_character_count, value.length, 200))
            }
        }) else null,
        keyboardOptions = KeyboardOptions(imeAction = if (multiline) ImeAction.Default else ImeAction.Next),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PersonalInformation(state: ProfileEditorState, onEdit: (String, String) -> Unit) {
    val draft = state.draft ?: return
    var showBirthday by remember { mutableStateOf(false) }
    var showGender by remember { mutableStateOf(false) }
    val birthday = runCatching { LocalDate.parse(draft.birthday) }.getOrNull()
    val locale = LocalConfiguration.current.locales[0]
    val genderValues = listOf("", "male", "female", "other")
    val genderLabels = listOf(R.string.profile_not_set, R.string.profile_male, R.string.profile_female, R.string.profile_other)
    if (showBirthday) BirthdayDialog(draft.birthday, onDismiss = { showBirthday = false }, onSelect = {
        onEdit("birthday", it); showBirthday = false
    })
    SettingsSection(stringResource(R.string.profile_personal)) {
        SegmentedListItem(
            onClick = { showBirthday = true }, enabled = state.editable,
            shapes = connectedListItemShapes(0, 2),
            modifier = Modifier.connectedListItemShadow(0, 2),
            leadingContent = { ProfileListIcon(R.drawable.ic_symbol_calendar_today) },
            supportingContent = { Text(birthday?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
                ?: draft.birthday.ifBlank { stringResource(R.string.profile_not_set) }) },
            trailingContent = { ProfileListIcon(R.drawable.ic_symbol_chevron_right) },
            content = { Text(stringResource(R.string.profile_birthday)) },
        )
        Box {
            SegmentedListItem(
                onClick = { showGender = true }, enabled = state.editable,
                shapes = connectedListItemShapes(1, 2),
                modifier = Modifier.connectedListItemShadow(1, 2),
                leadingContent = { ProfileListIcon(R.drawable.ic_symbol_person) },
                supportingContent = { Text(genderValues.indexOf(draft.gender).takeIf { it >= 0 }?.let {
                    stringResource(genderLabels[it])
                } ?: draft.gender) },
                trailingContent = { ProfileListIcon(R.drawable.ic_symbol_chevron_right) },
                content = { Text(stringResource(R.string.profile_gender)) },
            )
            DropdownMenu(expanded = showGender, onDismissRequest = { showGender = false }) {
                genderValues.forEachIndexed { index, value ->
                    DropdownMenuItem(text = { Text(stringResource(genderLabels[index])) }, onClick = {
                        onEdit("gender", value); showGender = false
                    }, trailingIcon = if (draft.gender == value) ({ ProfileIcon(R.drawable.ic_symbol_check) }) else null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayDialog(value: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initial,
        yearRange = 1900..LocalDate.now().year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= today
        })
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            dateState.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
        }, enabled = dateState.selectedDateMillis != null) { Text(stringResource(R.string.profile_done)) } },
        dismissButton = { Row {
            TextButton(onClick = { onSelect("") }) { Text(stringResource(R.string.profile_clear)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.account_cancel)) }
        } },
    ) { DatePicker(dateState, showModeToggle = true) }
}

@Composable
private fun ProfileLabels(state: ProfileEditorState, onAdd: (String) -> Unit, onRemove: (Int) -> Unit,
    onEdit: (String, String) -> Unit) {
    val input = state.labelInput
    fun add() { onAdd(input) }
    SettingsSection(stringResource(R.string.profile_labels)) { SettingsFormCard {
        if (!state.draft?.labels.isNullOrEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.draft.labels.forEachIndexed { index, label ->
                key(label.id, label.labelName) {
                    InputChip(selected = false, onClick = { onRemove(index) }, enabled = state.editable,
                        label = { Text(label.labelName, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = { ProfileIcon(R.drawable.ic_symbol_close,
                            stringResource(R.string.profile_remove_label, label.labelName)) })
                }
            }
        }
        OutlinedTextField(input, { onEdit("labelInput", it) }, modifier = Modifier.fillMaxWidth(), enabled = state.editable,
            label = { Text(stringResource(R.string.profile_add_label)) }, singleLine = true,
            shape = MaterialTheme.shapes.large, leadingIcon = { ProfileIcon(R.drawable.ic_symbol_label) },
            trailingIcon = { IconButton(onClick = ::add, enabled = state.editable && input.isNotBlank()) {
                ProfileIcon(R.drawable.ic_symbol_add, stringResource(R.string.profile_add_label))
            } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }))
    } }
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
