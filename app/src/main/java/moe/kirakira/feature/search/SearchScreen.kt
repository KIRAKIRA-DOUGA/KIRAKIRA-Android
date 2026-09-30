package moe.kirakira.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()

    Column(
        modifier = modifier.fillMaxSize().testTag("search_screen").padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SearchBar(
            state = searchBarState,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            inputField = {
                SearchBarDefaults.InputField(
                    textFieldState = textFieldState,
                    searchBarState = searchBarState,
                    onSearch = {},
                    // This milestone only presents the layout; search is not available yet.
                    enabled = false,
                    readOnly = true,
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_symbol_search), contentDescription = null)
                    },
                )
            },
        )
        ContentUnavailableView(
            state = ContentUnavailableState.EMPTY,
            title = stringResource(R.string.content_not_available_yet),
            description = null,
            iconRes = R.drawable.ic_symbol_search,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(locale = "zh", showBackground = true)
@Composable
private fun SearchPreview() {
    KIRAKIRATheme(dynamicColor = false) { SearchScreen() }
}
