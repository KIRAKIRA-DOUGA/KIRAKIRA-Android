@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.kirakira.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.PageSearchBar
import moe.kirakira.ui.components.SettingsSearchLogo

/** Both official search surfaces share the same saved text, expansion and results position. */
@Composable
internal fun SettingsSearchBar(
    signedIn: Boolean,
    isActive: Boolean,
    onSelect: (SettingsSearchEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextFieldState()
    val state = rememberSearchBarState()
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
    PageSearchBar(
        text = text,
        state = state,
        placeholder = stringResource(R.string.settings_search),
        isActive = isActive,
        modifier = modifier,
    ) {
        SettingsSearchResults(
            query = query,
            results = results,
            onSelect = { entry ->
                keyboard?.hide()
                if (entry.page == SettingsSearchPage.HOME) {
                    scope.launch {
                        state.animateToCollapsed()
                        onSelect(entry)
                    }
                } else onSelect(entry)
            },
            modifier = Modifier.fillMaxSize().imePadding(),
            listState = resultsState,
        )
    }
}

@Composable
private fun SettingsSearchResults(
    query: String,
    results: List<SettingsSearchResult>,
    onSelect: (SettingsSearchEntry) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    if (query.isBlank()) {
        Box(modifier)
    } else if (results.isEmpty()) {
        ContentUnavailableView(
            state = ContentUnavailableState.EMPTY,
            title = stringResource(R.string.settings_search_empty),
            description = null,
            modifier = modifier,
            icon = { SettingsSearchLogo() },
        )
    } else LazyColumn(
        state = listState,
        modifier = modifier,
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
                    painter = painterResource(R.drawable.ic_symbol_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(segment)
            }
        }
    }
}
