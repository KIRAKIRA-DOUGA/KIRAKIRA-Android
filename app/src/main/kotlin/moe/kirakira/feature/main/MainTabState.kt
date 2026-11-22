package moe.kirakira.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

internal data class MainTabSelection(val destination: AppDestination, val animate: Boolean = true)

private val MainTabSelectionSaver = Saver<MainTabSelection, AppDestination>(
    save = { it.destination },
    restore = { MainTabSelection(it) },
)

/** The host saves the tab; MainScreen owns the scroll states and supplies a live reselect action. */
internal class MainTabState(private val state: MutableState<MainTabSelection>) {
    val selection by state
    val destination: AppDestination get() = selection.destination
    var reselect: ((Int?) -> Unit)? = null

    fun select(tab: AppDestination, reselectCurrent: Boolean = false, animate: Boolean = true) {
        val changed = destination != tab
        reselect?.invoke(tab.ordinal.takeIf { !changed && reselectCurrent })
        if (changed) state.value = MainTabSelection(tab, animate)
    }
}

@Composable
internal fun rememberMainTabState(): MainTabState {
    val selection = rememberSaveable(stateSaver = MainTabSelectionSaver) {
        mutableStateOf(MainTabSelection(AppDestination.HOME))
    }
    return remember(selection) { MainTabState(selection) }
}
