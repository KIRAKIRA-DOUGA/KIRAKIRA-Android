package moe.kirakira.feature.settings.profile

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageView
import java.io.File
import moe.kirakira.ui.components.ShadowFilledTonalIconButton
import moe.kirakira.R
import moe.kirakira.feature.settings.SettingsPrimaryButton
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.theme.ThemeColorDefaults

/** Only the proven crop engine is a View; controls, state and surrounding UI are Compose. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AvatarCropScreen(
    state: ProfileEditorState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onLoaded: (File, Boolean) -> Unit,
    onFinished: (File, File, Boolean) -> Unit,
    onRememberViewport: (File, Parcelable?) -> Unit,
    onStartCrop: (File) -> Boolean,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val source = state.cropSource ?: return
    var cropper by remember(source) { mutableStateOf<CropImageView?>(null) }
    var runningOutput by remember(source) { mutableStateOf<File?>(null) }
    val currentLoaded by rememberUpdatedState(onLoaded)
    val currentFinished by rememberUpdatedState(onFinished)
    val currentViewport by rememberUpdatedState(onRememberViewport)
    val output = state.cropOutput
    LaunchedEffect(output, cropper, state.cropReady) {
        val view = cropper
        if (output != null && view != null && state.cropReady) {
            if (!onStartCrop(output)) return@LaunchedEffect
            runningOutput = output
            view.setOnCropImageCompleteListener { _, result ->
                runningOutput = null
                currentFinished(source, output, result.error == null && result.uriContent != null)
            }
            view.croppedImageAsync(saveCompressFormat = Bitmap.CompressFormat.JPEG,
                saveCompressQuality = 90, reqWidth = 1024, reqHeight = 1024,
                options = CropImageView.RequestSizeOptions.RESIZE_INSIDE, customOutputUri = Uri.fromFile(output))
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_edit_avatar), fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ThemeColorDefaults.appBarContainerColor(),
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
            navigationIcon = { IconButton(onClick = onCancel, enabled = !state.cropBusy, shapes = IconButtonDefaults.shapes()) {
                ProfileIcon(R.drawable.ic_symbol_close, stringResource(R.string.account_cancel))
            } }) },
        snackbarHost = { SnackbarHost(snackbar) },
        // Same tone as the top bar so the crop canvas reads as one framed surface.
        bottomBar = { Surface(color = ThemeColorDefaults.appBarContainerColor()) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.profile_rotate)) } },
                    state = rememberTooltipState()) {
                    ShadowFilledTonalIconButton(
                        onClick = { cropper?.rotateImage(90) },
                        enabled = state.cropReady && !state.cropBusy,
                        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(painterResource(R.drawable.ic_symbol_rotate_right), stringResource(R.string.profile_rotate))
                    }
                }
                SettingsPrimaryButton(
                    label = stringResource(R.string.profile_done),
                    onClick = onConfirm,
                    icon = R.drawable.ic_symbol_check,
                    enabled = state.cropReady && !state.cropBusy,
                    busy = state.cropBusy,
                    modifier = Modifier.weight(1f),
                )
            }
        } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context -> CropImageView(context).apply {
                    setAspectRatio(1, 1)
                    setFixedAspectRatio(true)
                    cropShape = CropImageView.CropShape.OVAL
                    guidelines = CropImageView.Guidelines.ON_TOUCH
                    isShowProgressBar = false
                    isSaveEnabled = false
                    setOnSetImageUriCompleteListener { _, _, error -> currentLoaded(source, error == null) }
                    val viewport = state.cropViewState
                    if (viewport is Bundle) onRestoreInstanceState(viewport)
                    else setImageUriAsync(Uri.fromFile(source))
                    cropper = this
                } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    val viewport = view.onSaveInstanceState()
                    // Force URI re-decoding; the library's weak bitmap cache would reference a recycled bitmap.
                    (viewport as? Bundle)?.remove("LOADED_IMAGE_STATE_BITMAP_KEY")
                    currentViewport(source, viewport)
                    view.setOnSetImageUriCompleteListener { loadedView, _, _ ->
                        loadedView.clearImage()
                        loadedView.setOnSetImageUriCompleteListener(null)
                    }
                    val pendingOutput = runningOutput
                    if (pendingOutput != null) {
                        view.setOnCropImageCompleteListener { completedView, result ->
                            runningOutput = null
                            currentFinished(source, pendingOutput, result.error == null && result.uriContent != null)
                            completedView.clearImage()
                            completedView.setOnCropImageCompleteListener(null)
                        }
                    } else {
                        view.setOnCropImageCompleteListener(null)
                        view.clearImage()
                    }
                    cropper = null
                },
            )
            if (state.cropBusy) Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            })
            if (!state.cropReady || state.cropBusy) IndeterminateCircularProgressIndicator()
        }
    }
}
