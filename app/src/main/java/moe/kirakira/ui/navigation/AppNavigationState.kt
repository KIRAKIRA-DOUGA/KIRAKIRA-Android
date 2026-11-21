package moe.kirakira.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import moe.kirakira.feature.imageviewer.ImageViewerCloseReason
import moe.kirakira.feature.imageviewer.ImageViewerDiagnostics

internal class SettingsAnchorRequest(val route: NavKey, val entryId: String)

/** Composition-owned coordination; page guards and viewer attachments are never saved. */
internal class AppNavigationState(val backStack: NavBackStack<NavKey>) {
    var mainBottomBarHeight by mutableStateOf(0)
    var railEnterFromBottom: Boolean? by mutableStateOf(null)
        private set
    val composedViewers = mutableStateMapOf<String, Int>()
    private val backGuards = mutableStateMapOf<NavKey, NavigationBackGuard>()
    private val expandedSettings = mutableStateSetOf<NavKey>()
    val fullPageSettings: Set<NavKey> get() = expandedSettings.toSet()
    private var pendingSettingsLeave: Pair<NavKey, () -> Unit>? = null
    var settingsAnchorRequest by mutableStateOf<SettingsAnchorRequest?>(null)
        private set

    fun consumeSettingsAnchor(request: SettingsAnchorRequest) {
        if (settingsAnchorRequest === request) settingsAnchorRequest = null
    }

    fun setFullPageSetting(route: NavKey, expanded: Boolean) {
        if (route in backStack && expanded) expandedSettings.add(route) else expandedSettings.remove(route)
    }

    fun retainSettingsInStack() {
        if (expandedSettings.isNotEmpty()) expandedSettings.retainAll(backStack)
        if (settingsAnchorRequest?.route != backStack.lastOrNull()) settingsAnchorRequest = null
    }

    fun canNavigateBack(): Boolean = backGuards[backStack.lastOrNull()]?.canNavigateBack() ?: true

    fun requestBack(): Boolean = backGuards[backStack.lastOrNull()]?.requestBack() ?: true

    fun registerBackGuard(route: NavKey, guard: NavigationBackGuard) {
        backGuards[route] = guard
    }

    fun unregisterBackGuard(route: NavKey, guard: NavigationBackGuard) {
        // An outgoing composition must never remove a newer registration for the same route.
        if (backGuards[route] === guard) {
            backGuards.remove(route)
            cancelSettingsLeave(route)
        }
    }

    fun cancelSettingsLeave(route: NavKey) {
        if (pendingSettingsLeave?.first == route) pendingSettingsLeave = null
    }

    fun completeSettingsLeave(route: NavKey) {
        val pending = pendingSettingsLeave?.takeIf { it.first == route }
        cancelSettingsLeave(route)
        if (backStack.lastOrNull() != route) return
        if (pending != null) pending.second() else popFrom(route)
    }

    /** Runs an action from the visible directory through the current detail's leave guard. */
    private fun fromSettingsDirectory(source: NavKey, action: () -> Unit) {
        val root = backStack.indexOf(SettingsRoute)
        if (root < 0 || backStack.lastOrNull() != source || source in expandedSettings ||
            backStack.subList(root + 1, backStack.size).any { !it.isSettingsDetail }
        ) return
        if (pendingSettingsLeave?.first == source) return
        val guard = backGuards[source]
        if (guard?.requestSettingsLeave?.invoke() != false) {
            action()
        } else if (guard.awaitingDiscardConfirmation()) {
            pendingSettingsLeave = source to action
        }
    }

    fun selectSetting(source: NavKey, target: NavKey, isSearch: Boolean = false) {
        val root = backStack.indexOf(SettingsRoute)
        if (root < 0 || backStack.lastOrNull() != source) return
        val currentCategory = backStack.getOrNull(root + 1)
        val destination = currentCategory?.takeIf {
            it.settingsCategory != null && it.settingsCategory == target.settingsCategory
        } ?: target
        if (destination == source && !isSearch) return
        fromSettingsDirectory(source) {
            Snapshot.withMutableSnapshot {
                val destinationIndex = backStack.indexOf(destination)
                val keep = if (destinationIndex > root) destinationIndex else root
                while (backStack.lastIndex > keep) backStack.removeLastOrNull()
                if (destinationIndex <= root) backStack.add(destination)
                settingsAnchorRequest = if (destinationIndex > root && target is SettingsSearchTargetRoute) {
                    SettingsAnchorRequest(destination, target.entryId)
                } else null
                railEnterFromBottom = null
            }
        }
    }

    fun leaveSettings(
        source: NavKey,
        afterPathChange: () -> Unit = {},
        changePathFromDirectory: () -> Unit = {},
    ) {
        fromSettingsDirectory(source) {
            Snapshot.withMutableSnapshot {
                while (backStack.lastOrNull() != SettingsRoute) backStack.removeLastOrNull()
                railEnterFromBottom = null
                changePathFromDirectory()
            }
            afterPathChange()
        }
    }

    fun popFrom(source: NavKey) {
        if (backStack.size > 1 && backStack.lastOrNull() == source) {
            railEnterFromBottom = null
            backStack.removeLastOrNull()
        }
    }

    fun openFrom(source: NavKey, target: NavKey) {
        if (source == target || backStack.lastOrNull() != source) return
        railEnterFromBottom = null
        open(target)
    }

    private fun open(target: NavKey) {
        val existingIndex = backStack.indexOf(target)
        if (existingIndex >= 0) {
            while (backStack.lastIndex > existingIndex) backStack.removeLastOrNull()
        } else {
            backStack.add(target)
        }
    }

    fun openFromDesktopRail(source: NavKey, target: NavKey, enterFromBottom: Boolean?) {
        if (source == target || backStack.lastOrNull() != source) return
        Snapshot.withMutableSnapshot {
            railEnterFromBottom = enterFromBottom
            if (source.isDesktopRailChild() && target.isDesktopRailChild() && target !in backStack) {
                // Replace atomically so Main is not briefly activated between sibling pages.
                backStack[backStack.lastIndex] = target
            } else {
                open(target)
            }
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
