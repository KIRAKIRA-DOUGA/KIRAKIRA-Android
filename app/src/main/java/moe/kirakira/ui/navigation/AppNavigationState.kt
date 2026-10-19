package moe.kirakira.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import moe.kirakira.feature.imageviewer.ImageViewerCloseReason
import moe.kirakira.feature.imageviewer.ImageViewerDiagnostics

/** Composition-owned coordination; page guards and viewer attachments are never saved. */
internal class AppNavigationState(val backStack: NavBackStack<NavKey>) {
    var bottomBarHeightPx by mutableIntStateOf(0)
    val composedViewers = mutableStateMapOf<String, Int>()
    private val backGuards = mutableStateMapOf<NavKey, NavigationBackGuard>()

    fun canNavigateBack(): Boolean = backGuards[backStack.lastOrNull()]?.canNavigateBack() ?: true

    fun requestBack(): Boolean = backGuards[backStack.lastOrNull()]?.requestBack() ?: true

    fun registerBackGuard(route: NavKey, guard: NavigationBackGuard) {
        backGuards[route] = guard
    }

    fun unregisterBackGuard(route: NavKey, guard: NavigationBackGuard) {
        // An outgoing composition must never remove a newer registration for the same route.
        if (backGuards[route] === guard) backGuards.remove(route)
    }

    fun popFrom(source: NavKey) {
        if (backStack.size > 1 && backStack.lastOrNull() == source) backStack.removeLastOrNull()
    }

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
        if (accepted) popFrom(route)
    }
}
