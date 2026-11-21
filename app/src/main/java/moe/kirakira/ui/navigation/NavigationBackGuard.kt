package moe.kirakira.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavKey

/** Preview permission is read-only; a completed back request may show a discard confirmation. */
internal class NavigationBackGuard(
    val canNavigateBack: () -> Boolean,
    val requestBack: () -> Boolean,
    val requestSettingsLeave: () -> Boolean,
    val awaitingDiscardConfirmation: () -> Boolean,
)

/** Keeps business guards at their page boundary and releases them with that composition. */
@Composable
internal fun RegisterNavigationBackGuard(
    navigation: AppNavigationState,
    route: NavKey,
    canNavigateBack: () -> Boolean,
    requestBack: () -> Boolean,
    requestSettingsLeave: () -> Boolean = { canNavigateBack() && requestBack() },
    awaitingDiscardConfirmation: () -> Boolean = { false },
) {
    val currentCanNavigateBack by rememberUpdatedState(canNavigateBack)
    val currentRequestBack by rememberUpdatedState(requestBack)
    val currentRequestSettingsLeave by rememberUpdatedState(requestSettingsLeave)
    val currentAwaitingDiscard by rememberUpdatedState(awaitingDiscardConfirmation)
    val guard = remember(navigation, route) {
        NavigationBackGuard(
            canNavigateBack = { currentCanNavigateBack() },
            requestBack = { currentRequestBack() },
            requestSettingsLeave = { currentRequestSettingsLeave() },
            awaitingDiscardConfirmation = { currentAwaitingDiscard() },
        )
    }
    DisposableEffect(navigation, route, guard) {
        navigation.registerBackGuard(route, guard)
        onDispose { navigation.unregisterBackGuard(route, guard) }
    }
}

@Composable
internal fun RegisterFullPageSetting(navigation: AppNavigationState, route: NavKey, fullPage: Boolean) {
    // A covering route can dispose this composition while keeping this entry on the back stack.
    SideEffect { navigation.setFullPageSetting(route, fullPage) }
}
