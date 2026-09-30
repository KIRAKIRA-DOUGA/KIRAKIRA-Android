package moe.kirakira.ui.components.image

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.saket.telephoto.zoomable.EnabledZoomGestures
import me.saket.telephoto.zoomable.ZoomableImageState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import moe.kirakira.R
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.theme.KIRAKIRATheme

/** Stateless navigation/operation surface; zoom state and image requests can be shared by callers. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageViewerScreen(
    model: Any,
    description: String,
    imageState: ZoomableImageState,
    loadFailed: Boolean,
    busy: Boolean,
    transitioning: Boolean,
    controlsVisible: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onToggleControls: () -> Unit,
    onInteractionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
    controlsModifier: Modifier = Modifier,
    placeholder: Painter? = null,
    visibilityProgress: () -> Float = { 1f },
) {
    val available = imageState.isImageDisplayed && !loadFailed && !busy && !transitioning
    val toggleLabel = stringResource(
        if (controlsVisible) R.string.image_hide_controls else R.string.image_show_controls,
    )
    var touching by remember { mutableStateOf(false) }
    var appBarFocused by remember { mutableStateOf(false) }
    var toolbarFocused by remember { mutableStateOf(false) }
    val reportInteraction by rememberUpdatedState(onInteractionChange)
    LaunchedEffect(touching, appBarFocused, toolbarFocused) {
        reportInteraction(touching || appBarFocused || toolbarFocused)
    }
    DisposableEffect(Unit) {
        onDispose { reportInteraction(false) }
    }
    Box(
        modifier.fillMaxSize().pointerInput(Unit) {
            // Observe touches without consuming them or competing with Telephoto's gestures.
            try {
                awaitPointerEventScope {
                    while (true) {
                        touching = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                    }
                }
            } finally {
                touching = false
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().then(backgroundModifier)) {
            drawRect(Color.Black, alpha = visibilityProgress().coerceIn(0f, 1f))
        }
        Box(imageModifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
            if (!imageState.isImageDisplayed && !loadFailed && placeholder != null) {
                Image(placeholder, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            ZoomableAsyncImage(
                model = model,
                contentDescription = description,
                state = imageState,
                gestures = if (transitioning) EnabledZoomGestures.None else EnabledZoomGestures.ZoomAndPan,
                clipToBounds = false,
                onClick = { if (!transitioning) onToggleControls() },
                modifier = Modifier
                    .fillMaxSize()
                    .imageReturnTransform(imageState, visibilityProgress)
                    .semantics(mergeDescendants = true) {
                        onClick(label = toggleLabel) {
                            if (!transitioning) onToggleControls()
                            !transitioning
                        }
                    },
            )
        }
        if (loadFailed) {
            ContentUnavailableView(
                state = ContentUnavailableState.ERROR,
                title = stringResource(R.string.image_load_failed),
                description = null,
                onRetry = onRetry,
                retryEnabled = !transitioning,
                presentation = ContentUnavailablePresentation.MEDIA,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 80.dp, bottom = 96.dp),
            )
        } else if (busy || (!imageState.isImageDisplayed && !transitioning)) {
            LoadingIndicator(color = Color.White)
        }
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier.align(Alignment.TopCenter).then(controlsModifier),
        ) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    FilledIconButton(
                        onClick = onBack,
                        enabled = controlsVisible && !transitioning,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF242424),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF242424),
                            disabledContentColor = Color.White,
                        ),
                    ) {
                        Icon(painterResource(R.drawable.ic_symbol_close), stringResource(R.string.image_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                ),
                modifier = Modifier.onFocusChanged { appBarFocused = it.hasFocus },
            )
        }
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom))
                .padding(end = 16.dp, bottom = 16.dp)
                .then(controlsModifier),
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = Color(0xFF242424),
                    toolbarContentColor = Color.White,
                ),
                modifier = Modifier.onFocusChanged { toolbarFocused = it.hasFocus }.focusGroup(),
            ) {
                IconButton(onClick = onSave, enabled = controlsVisible && available) {
                    Icon(painterResource(R.drawable.ic_symbol_download), stringResource(R.string.image_save))
                }
                IconButton(onClick = onCopy, enabled = controlsVisible && available) {
                    Icon(painterResource(R.drawable.ic_symbol_content_copy), stringResource(R.string.image_copy))
                }
            }
        }
        SnackbarHost(
            snackbarHostState,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (controlsVisible) 88.dp else 16.dp),
        )
    }
}

@Preview
@Composable
private fun ImageViewerPreview() {
    KIRAKIRATheme {
        ImageViewerScreen(
            model = R.drawable.ic_symbol_video_library,
            description = "Avatar",
            imageState = rememberZoomableImageState(),
            loadFailed = false,
            busy = false,
            transitioning = false,
            controlsVisible = true,
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onSave = {},
            onCopy = {},
            onRetry = {},
            onToggleControls = {},
            onInteractionChange = {},
            placeholder = painterResource(R.drawable.ic_symbol_video_library),
        )
    }
}
