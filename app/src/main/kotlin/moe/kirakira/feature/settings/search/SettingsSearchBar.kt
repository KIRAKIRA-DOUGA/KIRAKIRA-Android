@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.kirakira.feature.settings.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.ui.components.illustration.SettingsSearchLogo
import moe.kirakira.ui.components.search.PageSearchBar
import moe.kirakira.ui.components.search.PageSearchBarAnchor
import moe.kirakira.ui.components.search.PageSearchOverlay
import moe.kirakira.ui.components.search.isOpen
import moe.kirakira.ui.components.status.ContentUnavailableState
import moe.kirakira.ui.components.status.ContentUnavailableView
import moe.kirakira.ui.navigation.blockNavigationInput

/** Keep a single search session as the directory moves between a page and a pane. */
@Composable
internal fun SettingsSearchLayout(
    signedIn: Boolean,
    isActive: Boolean,
    onSelect: (SettingsSearchEntry) -> Unit,
    twoPane: Boolean,
    hasDetail: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (@Composable () -> Unit) -> Unit,
) {
    val text = rememberTextFieldState()
    val state = rememberSearchBarState()
    LaunchedEffect(twoPane) {
        if (twoPane && hasDetail && state.targetValue == SearchBarValue.Expanded) {
            state.animateToCollapsed()
        }
    }
    val resultsState = rememberLazyListState()
    var previousQuery by rememberSaveable { mutableStateOf("") }
    val query = text.text.toString()
    LaunchedEffect(query) {
        if (previousQuery != query) {
            resultsState.scrollToItem(0)
            previousQuery = query
        }
    }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val results = searchSettings(LocalResources.current, query, signedIn)
    val searchResults: @Composable ColumnScope.(PaddingValues) -> Unit = { contentPadding ->
        SettingsSearchResults(
            query = query,
            results = results,
            isActive = state.targetValue == SearchBarValue.Expanded,
            onSelect = { entry ->
                keyboard?.hide()
                if (twoPane || entry.page == SettingsSearchPage.HOME) {
                    scope.launch {
                        state.animateToCollapsed()
                        onSelect(entry)
                    }
                } else onSelect(entry)
            },
            contentPadding = contentPadding,
            listState = resultsState,
        )
    }
    val placeholder = stringResource(R.string.settings_search)
    var anchor by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionOnScreen() }) {
        Box(if (twoPane) Modifier.blockNavigationInput(state.isOpen) else Modifier) {
            content {
                if (twoPane) PageSearchBarAnchor(text, state, placeholder, onBoundsChanged = { anchor = it })
                else PageSearchBar(
                    text, state, placeholder, isActive,
                    content = searchResults,
                )
            }
        }
        if (twoPane) PageSearchOverlay(
            text, state, placeholder, isActive,
            collapsedBoundsProvider = { anchor?.translate(-origin) },
            predictiveBack = true,
            content = searchResults,
        )
    }
}

@Composable
private fun SettingsSearchResults(
    query: String,
    results: List<SettingsSearchResult>,
    isActive: Boolean,
    onSelect: (SettingsSearchEntry) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val modifier = Modifier.fillMaxSize()
    if (query.isBlank()) {
        Box(modifier)
    } else if (results.isEmpty()) {
        ContentUnavailableView(
            state = ContentUnavailableState.EMPTY,
            title = stringResource(R.string.settings_search_empty),
            description = null,
            modifier = modifier.padding(contentPadding),
            icon = { SettingsSearchLogo(isActive = isActive) },
        )
    } else LazyColumn(
        state = listState,
        modifier = modifier.consumeWindowInsets(contentPadding),
        contentPadding = contentPadding,
    ) {
        items(results, key = { it.entry.id }, contentType = { "settings_search_result" }) { result ->
            ListItem(
                content = { Text(result.title) },
                supportingContent = if (result.path.isEmpty()) null else {
                    { SettingsSearchBreadcrumb(result.path) }
                },
                leadingContent = { Icon(painterResource(result.entry.icon), contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                // Match SearchBarSamples: the full row handles clicks before its content inset.
                modifier = Modifier
                    .clickable { onSelect(result.entry) }
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingsSearchBreadcrumb(path: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        path.forEachIndexed { index, segment ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (index > 0) Icon(
                    painter = painterResource(R.drawable.ic_icon_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(segment)
            }
        }
    }
}
