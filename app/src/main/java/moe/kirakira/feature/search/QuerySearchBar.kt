package moe.kirakira.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import moe.kirakira.ui.components.textFieldInnerShadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuerySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showClear: Boolean = query.isNotEmpty(),
    onClear: () -> Unit = {},
    inputFieldModifier: Modifier = Modifier,
    clearDescription: String = stringResource(R.string.search_clear),
) {
    val trailingIcon: (@Composable () -> Unit)? = if (showClear) {
        {
            SearchIconButton(
                clearDescription, R.drawable.ic_icon_close, onClear, enabled = enabled,
            )
        }
    } else null
    SearchFieldContainer(modifier = modifier) {
        SearchBarDefaults.InputField(
            modifier = inputFieldModifier,
            query = query,
            onQueryChange = onQueryChange,
            onSearch = { onSearch() },
            expanded = false,
            onExpandedChange = {},
            enabled = enabled,
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_icon_search), contentDescription = null)
            },
            trailingIcon = trailingIcon,
        )
    }
}

/** Shared search surface for keyword input, selected tags, and the full-screen picker. */
@Composable
internal fun SearchFieldContainer(
    modifier: Modifier = Modifier,
    inputField: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.textFieldInnerShadow(shape), propagateMinConstraints = true) {
            inputField()
        }
    }
}
