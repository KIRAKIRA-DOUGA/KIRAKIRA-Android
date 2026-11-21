package moe.kirakira.ui.components.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.SearchFieldTrailingAction
import moe.kirakira.ui.components.textFieldInnerShadow
import moe.kirakira.ui.theme.ThemeColorDefaults

@Composable
internal fun QuerySearchBar(
    state: TextFieldState,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showClear: Boolean = false,
    onClear: () -> Unit = {},
    inputFieldModifier: Modifier = Modifier,
    clearDescription: String = stringResource(R.string.search_clear),
) {
    SearchFieldContainer(modifier = modifier) {
        SearchQueryField(
            state = state,
            onSearch = onSearch,
            placeholder = placeholder,
            modifier = inputFieldModifier,
            enabled = enabled,
            trailingIcon = {
                SearchFieldTrailingAction(visible = showClear || state.text.isNotEmpty()) {
                    SearchIconButton(
                        clearDescription, R.drawable.ic_icon_close, onClear, enabled = enabled,
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchQueryField(
    state: TextFieldState,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val searchDescription = stringResource(R.string.nav_search)
    TextField(
        state = state,
        modifier = modifier
            .heightIn(min = SearchBarDefaults.InputFieldHeight)
            .semantics { contentDescription = searchDescription },
        enabled = enabled,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { onSearch() },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Box(Modifier.offset(x = 4.dp)) {
                Icon(painterResource(R.drawable.ic_icon_search), contentDescription = null)
            }
        },
        trailingIcon = trailingIcon?.let { icon ->
            { Box(Modifier.offset(x = (-4).dp)) { icon() } }
        },
        colors = SearchBarDefaults.inputFieldColors().copy(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/** Shared inset surface for keyword and tag search. */
@Composable
internal fun SearchFieldContainer(
    modifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    inputField: @Composable (containerColor: Color) -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val containerColor = ThemeColorDefaults.controlContainerColor()
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColorFor(containerColor),
    ) {
        Box(propagateMinConstraints = true) {
            Box(
                Modifier.matchParentSize().then(backgroundModifier)
                    .background(containerColor, shape)
                    .textFieldInnerShadow(shape),
            )
            Box(contentModifier, propagateMinConstraints = true) {
                inputField(containerColor)
            }
        }
    }
}

@Composable
internal fun SearchIconButton(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SearchTooltip(label) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Icon(painterResource(icon), label)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}
