package moe.kirakira.feature.imageviewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.SingletonImageLoader
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.saket.telephoto.zoomable.rememberZoomableImageState
import moe.kirakira.R
import moe.kirakira.ui.components.image.ImageViewerScreen
import moe.kirakira.ui.components.image.rememberImageViewerControlsState

@Composable
internal fun ImageViewerPage(
    image: ViewerImage,
    instanceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    controlsModifier: Modifier = Modifier,
    onDrawableChange: (Boolean) -> Unit = {},
    transitioning: Boolean = false,
    visibilityProgress: () -> Float = { 1f },
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val viewModel = viewModel { ImageViewerViewModel(context.applicationContext, image) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var permissionPending by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(instanceId, lifecycle) {
        ImageViewerDiagnostics.lifecycle(instanceId, lifecycle.currentState)
        val observer = LifecycleEventObserver { _, _ ->
            ImageViewerDiagnostics.lifecycle(instanceId, lifecycle.currentState)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(instanceId, transitioning) {
        ImageViewerDiagnostics.transition(instanceId, transitioning)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionPending = false
        if (granted) {
            viewModel.save()
        } else {
            scope.launch {
                val permanentlyDenied = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
                val result = snackbar.showSnackbar(
                    context.getString(R.string.image_storage_denied),
                    actionLabel = if (permanentlyDenied) context.getString(R.string.image_open_settings) else null,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                }
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(context.getString(message))
        }
    }
    key(instanceId, attempt) {
        val imageState = rememberZoomableImageState()
        val loader = remember(context, imageLoader, image, instanceId, attempt, viewport) {
            ViewerImageLoader(context, imageLoader, image, instanceId, attempt)
        }
        DisposableEffect(loader) {
            onDispose { loader.close() }
        }
        LaunchedEffect(loader, viewport) {
            if (viewport.width > 0 && viewport.height > 0) {
                coroutineScope {
                    launch { loader.loadThumbnail(viewport) }
                    loader.load(viewport)
                }
            }
        }
        LaunchedEffect(loader) { loader.observeDecoderFailures() }
        LaunchedEffect(loader, imageState) {
            snapshotFlow {
                val displayed = imageState.isImageDisplayed
                displayed to (!displayed && loader.placeholder != null)
            }.collect { (full, thumb) ->
                ImageViewerDiagnostics.display(instanceId, attempt, full, thumb)
                if (full) loader.markDisplayed()
            }
        }
        LaunchedEffect(loader, loader.requestSucceeded) {
            if (loader.requestSucceeded) {
                val displayed = withTimeoutOrNull(VIEWER_FIRST_FRAME_TIMEOUT_MILLIS) {
                    snapshotFlow { imageState.isImageDisplayed }.first { it }
                }
                if (displayed == null) loader.usePreview(ViewerImageFallback.FIRST_FRAME_TIMEOUT)
            }
        }
        val loadFailed = loader.phase == ViewerImagePhase.FAILED
        val keepControlsVisible = busy || permissionPending || loadFailed || !imageState.isImageDisplayed
        val controls = rememberImageViewerControlsState(
            autoHideEnabled = !transitioning && snackbar.currentSnackbarData == null,
            forceVisible = keepControlsVisible,
        )
        ImageViewerScreen(
            image = loader.source,
            description = image.description,
            imageState = imageState,
            loadFailed = loadFailed,
            busy = busy || permissionPending,
            transitioning = transitioning,
            controlsVisible = controls.visible || keepControlsVisible,
            snackbarHostState = snackbar,
            onBack = onBack,
            onSave = {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(
                        context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    if (!permissionPending) {
                        permissionPending = true
                        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                } else {
                    viewModel.save()
                }
            },
            onCopy = viewModel::copy,
            onRetry = { attempt++ },
            onToggleControls = { if (!keepControlsVisible) controls.toggle() },
            onInteractionChange = controls::onInteractionChange,
            modifier = modifier,
            imageModifier = imageModifier,
            controlsModifier = controlsModifier,
            placeholder = loader.placeholder,
            onViewportChange = { viewport = it },
            onDrawableChange = onDrawableChange,
            visibilityProgress = visibilityProgress,
        )
    }
}
