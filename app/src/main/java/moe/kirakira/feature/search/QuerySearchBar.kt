package moe.kirakira.feature.search

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import moe.kirakira.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuerySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    searchEnabled: Boolean = true,
    showClear: Boolean = query.isNotEmpty(),
    onClear: () -> Unit = {},
    readOnly: Boolean = false,
    searchDescription: String = stringResource(R.string.search_submit),
) {
    val leadingIcon: @Composable () -> Unit = {
        SearchIconButton(
            if (readOnly) stringResource(R.string.search_choose_tags) else searchDescription,
            if (readOnly) R.drawable.ic_symbol_label else R.drawable.ic_symbol_search,
            onSearch,
            enabled = enabled && searchEnabled,
        )
    }
    val trailingIcon: (@Composable () -> Unit)? = if (readOnly) {
        {
            SearchIconButton(
                stringResource(R.string.search_choose_tags), R.drawable.ic_symbol_add, onSearch, enabled = enabled,
            )
        }
    } else if (showClear) {
        {
            SearchIconButton(
                stringResource(R.string.search_clear), R.drawable.ic_symbol_close, onClear, enabled = enabled,
            )
        }
    } else null
    // The inline overload keeps the keyboard available without a separate expanded search surface.
    SearchBar(
        inputField = {
            if (readOnly) {
                SearchBarDefaults.InputField(
                    state = remember { TextFieldState() },
                    onSearch = { onSearch() },
                    expanded = false,
                    onExpandedChange = { if (it) onSearch() },
                    enabled = enabled,
                    readOnly = true,
                    placeholder = { Text(placeholder) },
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                )
            } else {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = { onSearch() },
                    expanded = false,
                    onExpandedChange = {},
                    enabled = enabled,
                    placeholder = { Text(placeholder) },
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                )
            }
        },
        expanded = false,
        onExpandedChange = {},
        modifier = modifier,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {}
}
