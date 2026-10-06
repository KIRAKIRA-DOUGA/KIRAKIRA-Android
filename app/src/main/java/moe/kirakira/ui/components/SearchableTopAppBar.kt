package moe.kirakira.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRATheme

/**
 * Replaces the supplied ordinary top bar with inline search. The caller owns query/expanded state,
 * exit policy, background and shadow. Both bars must use the same [windowInsets]; collapsible bars
 * should finish collapsing before the caller sets [expanded] and stay collapsed during search.
 */
@Composable
internal fun SearchableTopAppBar(
    query: String,
    expanded: Boolean,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = true,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    topBar: @Composable (onOpenSearch: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    var requestFocus by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val fieldHeight = with(density) {
        TextFieldDefaults.MinHeight + MaterialTheme.typography.bodyLarge.lineHeight.toDp() *
            (1f - 1f / fontScale).coerceAtLeast(0f)
    }

    fun dismissKeyboard() {
        if (fieldFocused) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }

    fun closeSearch() {
        requestFocus = false
        dismissKeyboard()
        onClose()
    }

    LaunchedEffect(expanded, enabled, isActive) {
        if (!expanded || !enabled || !isActive) {
            dismissKeyboard()
            if (!isActive || !enabled) requestFocus = false
        }
    }
    if (expanded && isActive && LocalNavigationEventDispatcherOwner.current != null) {
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = {
                if (keyboardVisible) {
                    keyboard?.hide()
                    if (fieldFocused) focus.clearFocus()
                } else {
                    closeSearch()
                }
            },
        )
    }

    AnimatedContent(
        targetState = expanded,
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopEnd,
        transitionSpec = {
            val enter = if (targetState) {
                fadeIn(effectsSpec) + expandHorizontally(spatialSpec, expandFrom = Alignment.End)
            } else fadeIn(effectsSpec)
            val exit = if (targetState) fadeOut(effectsSpec) else {
                fadeOut(effectsSpec) + shrinkHorizontally(spatialSpec, shrinkTowards = Alignment.End)
            }
            enter.togetherWith(exit).using(SizeTransform(clip = false) { _, _ -> spatialSpec })
        },
        label = "Top bar search",
    ) { showSearch ->
        // Outgoing bars remain composed for the animation, but must not receive input or semantics.
        val interactive = showSearch == expanded && isActive
        val barModifier = Modifier.fillMaxWidth().then(
            if (interactive) Modifier else Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
        )
        if (showSearch) {
            LaunchedEffect(requestFocus, interactive, enabled) {
                if (requestFocus && interactive && enabled) {
                    withFrameNanos { }
                    requester.requestFocus()
                    keyboard?.show()
                    requestFocus = false
                }
            }
            TopAppBar(
                modifier = barModifier,
                windowInsets = windowInsets,
                expandedHeight = maxOf(TopAppBarDefaults.TopAppBarExpandedHeight, fieldHeight),
                title = {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(requester)
                            .onFocusChanged { fieldFocused = it.isFocused }
                            .semantics { contentDescription = placeholder },
                        enabled = enabled && interactive,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            dismissKeyboard()
                            onSearch()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::closeSearch, enabled = interactive) {
                        Icon(painterResource(R.drawable.ic_symbol_arrow_back), stringResource(R.string.search_close))
                    }
                },
                actions = {
                    IconButton(
                        onClick = onClear,
                        modifier = if (query.isEmpty()) Modifier.clearAndSetSemantics {} else Modifier,
                        enabled = query.isNotEmpty() && enabled && interactive,
                    ) {
                        if (query.isNotEmpty()) {
                            Icon(painterResource(R.drawable.ic_symbol_close), stringResource(R.string.search_clear))
                        }
                    }
                },
                colors = appTopAppBarColors(),
            )
        } else {
            Box(barModifier) {
                topBar {
                    if (enabled && isActive && !expanded) {
                        requestFocus = true
                        onExpand()
                    }
                }
            }
        }
    }
}

@Composable
internal fun TopAppBarSearchButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Icon(painterResource(R.drawable.ic_symbol_search), contentDescription)
    }
}

@Preview(name = "Top bar search", showBackground = true)
@Preview(name = "Top bar search · Large text", fontScale = 1.5f, showBackground = true)
@Composable
private fun SearchableTopAppBarPreview() {
    TopBarSearchPreviewContent(initialExpanded = true)
}

@Preview(name = "Top bar search · Collapsed", showBackground = true)
@Composable
private fun SearchableTopAppBarCollapsedPreview() {
    TopBarSearchPreviewContent(initialExpanded = false)
}

@Composable
private fun TopBarSearchPreviewContent(initialExpanded: Boolean) {
    KIRAKIRATheme {
        var expanded by remember { mutableStateOf(initialExpanded) }
        var query by remember { mutableStateOf("") }
        SearchableTopAppBar(
            query = query,
            expanded = expanded,
            placeholder = stringResource(R.string.history_search),
            onQueryChange = { query = it },
            onExpand = { expanded = true },
            onClear = { query = "" },
            onClose = {
                expanded = false
                query = ""
            },
            onSearch = {},
        ) { openSearch ->
            TopAppBar(
                title = { Text(stringResource(R.string.me_history)) },
                actions = { TopAppBarSearchButton(openSearch, stringResource(R.string.history_search)) },
                colors = appTopAppBarColors(),
            )
        }
    }
}
