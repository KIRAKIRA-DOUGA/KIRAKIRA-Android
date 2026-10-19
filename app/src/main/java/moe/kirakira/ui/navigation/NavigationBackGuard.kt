package moe.kirakira.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavKey

/** Preview permission is read-only; a completed back request may show a discard confirmation. */
internal class NavigationBackGuard(
    val canNavigateBack: () -> Boolean,
    val requestBack: () -> Boolean,
)

/** Keeps business guards at their page boundary and releases them with that composition. */
@Composable
internal fun RegisterNavigationBackGuard(
    navigation: AppNavigationState,
    route: NavKey,
    canNavigateBack: () -> Boolean,
    requestBack: () -> Boolean,
) {
    val currentCanNavigateBack by rememberUpdatedState(canNavigateBack)
    val currentRequestBack by rememberUpdatedState(requestBack)
    val guard = remember(navigation, route) {
        NavigationBackGuard(
            canNavigateBack = { currentCanNavigateBack() },
            requestBack = { currentRequestBack() },
        )
    }
    DisposableEffect(navigation, route, guard) {
        navigation.registerBackGuard(route, guard)
        onDispose { navigation.unregisterBackGuard(route, guard) }
    }
}
