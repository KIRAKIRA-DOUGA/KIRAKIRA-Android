package moe.kirakira.feature.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first

internal class SettingsSearchTarget(
    val entry: SettingsSearchEntry,
    val title: String,
    completedState: MutableState<Boolean>,
) {
    var completed by completedState
}

internal val LocalSettingsSearchTarget = staticCompositionLocalOf<SettingsSearchTarget?> { null }

@Composable
internal fun SettingsSearchTargetProvider(
    entry: SettingsSearchEntry?,
    ready: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (entry == null || entry.topLevel || !ready) {
        content()
        return
    }
    val completed = rememberSaveable(entry.id) { mutableStateOf(false) }
    val title = stringResource(entry.title)
    val target = remember(entry, title) { SettingsSearchTarget(entry, title, completed) }
    CompositionLocalProvider(LocalSettingsSearchTarget provides target, content = content)
}

/** Attaches only to the requested static label; it never focuses or activates the control. */
@Composable
internal fun Modifier.settingsSearchAnchor(title: String): Modifier {
    val target = LocalSettingsSearchTarget.current
    if (target == null || target.title != title) return this
    val requester = remember { BringIntoViewRequester() }
    var placed by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(target, placed, lifecycle) {
        if (placed && lifecycle == Lifecycle.State.RESUMED && !target.completed) {
            withFrameNanos { }
            requester.bringIntoView()
            target.completed = true
        }
    }
    return bringIntoViewRequester(requester).onGloballyPositioned { placed = true }
}

/** Materializes the containing lazy item before its precise anchor can request visibility. */
@Composable
internal fun PrepareSettingsSearchList(state: LazyListState, itemKeys: List<String>, targetKey: String?) {
    val targetIndex = itemKeys.indexOf(targetKey).takeIf { it >= 0 }
    val target = LocalSettingsSearchTarget.current ?: return
    var prepared by rememberSaveable(target.entry.id) { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(target, targetIndex, lifecycle) {
        if (targetIndex != null && lifecycle == Lifecycle.State.RESUMED && !prepared && !target.completed) {
            snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > targetIndex }
            state.scrollToItem(targetIndex)
            prepared = true
        }
    }
}
