package moe.kirakira.feature.settings.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.kirakira.R
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ShadowButton

@Composable
internal fun ProfileEditorPage(model: ProfileEditorViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    var pickerRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        model.prepareAvatar(uri, pickerRevision)
        pickerRevision = null
    }
    LaunchedEffect(model) { model.clearStaleImageCache() }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            model.dismissMessage()
            snackbar.showSnackbar(resources.getString(message))
        }
    }
    if (state.confirmDiscard) ActionDialog(
        onDismissRequest = model::dismissDiscard,
        icon = { Icon(painterResource(R.drawable.ic_symbol_edit), contentDescription = null) },
        title = { Text(stringResource(R.string.profile_discard_title)) },
        text = { Text(stringResource(if (state.completionOnly) R.string.profile_saved_pending else R.string.profile_discard_message)) },
        actions = {
            ShadowButton(
                onClick = onBack,
                destructive = true,
            ) { Text(stringResource(R.string.profile_discard)) }
            TextButton(
                onClick = model::dismissDiscard,
            ) {
                Text(stringResource(R.string.profile_keep_editing))
            }
        },
    )
    val source = state.cropSource
    if (source != null) {
        AvatarCropScreen(state, model::cancelCrop, model::beginCrop, model::cropLoaded, model::finishCrop,
            model::rememberCropViewport, model::claimCrop,
            snackbar = snackbar)
    } else ProfileEditorScreen(
        state = state,
        onBack = { if (model.requestBack()) onBack() },
        onLogin = onLogin, onRetry = model::load, onSave = model::save, onEdit = model::edit,
        onPickAvatar = {
            pickerRevision = model.accountRevision
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onAddLabel = model::addLabel, onRemoveLabel = model::removeLabel, snackbar = snackbar,
    )
}
