package moe.kirakira.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import moe.kirakira.feature.imageviewer.ImageViewerCloseReason
import moe.kirakira.feature.imageviewer.ImageViewerDiagnostics
import moe.kirakira.feature.settings.privacy.PrivacySettingsViewModel
import moe.kirakira.feature.settings.profile.ProfileEditorViewModel
import moe.kirakira.feature.settings.security.SecuritySettingsViewModel

/** Composition-owned coordination; page guards and viewer attachments are never saved. */
internal class AppNavigationState(val backStack: NavBackStack<NavKey>) {
    var bottomBarHeightPx by mutableIntStateOf(0)
    val composedViewers = mutableStateMapOf<String, Int>()
    var profileBackGuard by mutableStateOf<ProfileEditorViewModel?>(null)
    var privacyBackGuard by mutableStateOf<PrivacySettingsViewModel?>(null)
    var securityBackGuard by mutableStateOf<SecuritySettingsViewModel?>(null)

    fun openFrom(source: NavKey, target: NavKey) {
        if (backStack.lastOrNull() != source) return
        val existingIndex = backStack.indexOf(target)
        if (existingIndex >= 0) {
            while (backStack.lastIndex > existingIndex) backStack.removeLastOrNull()
        } else {
            backStack.add(target)
        }
    }

    fun closeImageViewer(route: ImageViewerRoute, reason: ImageViewerCloseReason) {
        val accepted = backStack.size > 1 &&
            (backStack.lastOrNull() as? ImageViewerRoute)?.instanceId == route.instanceId
        ImageViewerDiagnostics.close(route.instanceId, reason, accepted)
        // A stale close callback must never pop a newer viewer instance.
        if (accepted) backStack.removeLastOrNull()
    }
}
