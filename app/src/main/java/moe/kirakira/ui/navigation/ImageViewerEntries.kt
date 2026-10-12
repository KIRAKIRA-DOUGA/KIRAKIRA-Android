package moe.kirakira.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import moe.kirakira.feature.imageviewer.ImageViewerCloseReason
import moe.kirakira.feature.imageviewer.ImageViewerDiagnostics
import moe.kirakira.feature.imageviewer.ImageViewerPage
import moe.kirakira.feature.imageviewer.ImageExportViewModel

internal fun registerImageViewerEntries(
    scope: EntryProviderScope<NavKey>,
    navigation: AppNavigationState,
    imageMetadata: Map<String, Any>,
    imageExports: ImageExportViewModel?,
) {
    with(scope) {
        entry<ImageViewerRoute>(metadata = imageMetadata) { route ->
            // Keep light system-bar icons until the viewer's exit animation is disposed.
            DisposableEffect(route.instanceId) {
                navigation.composedViewers[route.instanceId] = navigation.composedViewers.getOrDefault(route.instanceId, 0) + 1
                ImageViewerDiagnostics.composition(route.instanceId, attached = true)
                onDispose {
                    val remaining = navigation.composedViewers.getOrDefault(route.instanceId, 1) - 1
                    if (remaining == 0) navigation.composedViewers.remove(route.instanceId)
                    else navigation.composedViewers[route.instanceId] = remaining
                    ImageViewerDiagnostics.composition(route.instanceId, attached = false)
                }
            }
            var drawable by remember(route.instanceId) { mutableStateOf(false) }
            val visibility = rememberImageVisibility()
            ImageViewerPage(
                image = route.image,
                instanceId = route.instanceId,
                exports = checkNotNull(imageExports),
                onBack = { navigation.closeImageViewer(route, ImageViewerCloseReason.CLOSE_BUTTON) },
                imageModifier = Modifier.imageSharedBounds(
                    route.image.sharedKey, viewer = true, drawable = drawable,
                ),
                controlsModifier = Modifier.imageControlsOverlay(),
                onDrawableChange = { drawable = it },
                transitioning = visibility.value != 1f,
                visibilityProgress = { visibility.value },
            )
        }
    }
}
