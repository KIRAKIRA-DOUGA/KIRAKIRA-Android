@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import moe.kirakira.R

/** Dialog-backed search for pages whose results navigate without a shared media source. */
@Composable
internal fun PageSearchBar(
    text: TextFieldState,
    state: SearchBarState,
    placeholder: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxContentWidth: Dp = 640.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    StatusBarScrollToTopPauseEffect(
        isActive && (state.currentValue != SearchBarValue.Collapsed || state.targetValue != SearchBarValue.Collapsed),
    )
    LaunchedEffect(enabled) {
        if (!enabled) state.animateToCollapsed()
    }
    val collapsedShape = MaterialTheme.shapes.large
    val dialogExpansionShape = remember(collapsedShape, state) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                // Material 3 switches from a custom collapsed shape to a rectangle at half progress.
                val progress = (state.progress * 2f).coerceAtMost(1f)
                return searchExpansionShape(collapsedShape, progress).createOutline(size, layoutDirection, density)
            }
        }
    }
    PageSearchBarAnchor(text, state, placeholder, modifier, enabled, maxContentWidth)
    if (isActive && enabled) ExpandedFullScreenSearchBar(
        state = state,
        inputField = { PageSearchInputField(text, state, placeholder, enabled = enabled) },
        collapsedShape = dialogExpansionShape,
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            dividerColor = Color.Transparent,
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = {
            // Resolve focus and keyboard controllers inside the search dialog's own composition.
            Column(Modifier.fillMaxSize().dismissKeyboardOnBlankTap(), content = content)
        },
    )
}

/** The title owns top insets; the persistent search bar only owns horizontal insets. */
@Composable
internal fun PageSearchBarAnchor(
    text: TextFieldState,
    state: SearchBarState,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxContentWidth: Dp = 640.dp,
    onBoundsChanged: ((Rect) -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(SearchBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.widthIn(max = maxContentWidth).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            SearchBar(
                state = state,
                inputField = {
                    PageSearchInputField(
                        text,
                        state,
                        placeholder,
                        modifier = Modifier.textFieldInnerShadow(shape),
                        enabled = enabled,
                    )
                },
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    onBoundsChanged?.invoke(
                        Rect(
                            position.x,
                            position.y,
                            position.x + coordinates.size.width,
                            position.y + coordinates.size.height,
                        ),
                    )
                },
                shape = shape,
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    dividerColor = Color.Transparent,
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            )
        }
    }
}

internal fun searchExpansionShape(collapsedShape: Shape, progress: Float): Shape =
    Interpolatable.lerp(collapsedShape, RectangleShape, progress) as? Shape ?: collapsedShape

/**
 * Activity-owned full-screen search. Keep it mounted beneath a video destination so both the
 * source list and the retained playback viewport always belong to the same window and scene.
 */
@Composable
internal fun PageSearchOverlay(
    text: TextFieldState,
    state: SearchBarState,
    placeholder: String,
    isActive: Boolean,
    collapsedBoundsProvider: () -> Rect?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    LaunchedEffect(enabled) {
        if (!enabled) state.animateToCollapsed()
    }
    if (state.currentValue == SearchBarValue.Collapsed && state.targetValue == SearchBarValue.Collapsed) return
    StatusBarScrollToTopPauseEffect(paused = isActive)
    val scope = rememberCoroutineScope()
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    var fieldFocused by remember { mutableStateOf(false) }
    val currentIsActive by rememberUpdatedState(isActive)
    LaunchedEffect(state.targetValue) {
        if (state.targetValue == SearchBarValue.Expanded && currentIsActive) {
            withFrameNanos { }
            if (currentIsActive) {
                requester.requestFocus()
                keyboard?.show()
            }
        } else if (fieldFocused) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    LaunchedEffect(isActive) {
        if (!isActive && fieldFocused) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    if (isActive && LocalNavigationEventDispatcherOwner.current != null) NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = {
            if (keyboardVisible) {
                focus.clearFocus()
                keyboard?.hide()
            } else scope.launch { state.animateToCollapsed() }
        },
    )
    PageSearchOverlayLayout(
        state = state,
        collapsedBoundsProvider = collapsedBoundsProvider,
        inputField = {
            PageSearchInputField(
                text = text,
                state = state,
                placeholder = placeholder,
                modifier = Modifier.fillMaxWidth().focusRequester(requester)
                    .onFocusChanged { fieldFocused = it.isFocused },
                enabled = enabled,
            )
        },
        modifier = modifier.fillMaxSize().dismissKeyboardOnBlankTap(enabled = isActive && enabled),
        content = content,
    )
}

@Composable
private fun PageSearchInputField(
    text: TextFieldState,
    state: SearchBarState,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    SearchBarDefaults.InputField(
        textFieldState = text,
        searchBarState = state,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        onSearch = { keyboard?.hide() },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            if (state.currentValue == SearchBarValue.Expanded) {
                IconButton(onClick = { scope.launch { state.animateToCollapsed() } }, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_icon_arrow_left), stringResource(R.string.navigate_back))
                }
            } else Icon(painterResource(R.drawable.ic_icon_search), contentDescription = null)
        },
        trailingIcon = {
            if (text.text.isNotEmpty()) IconButton(onClick = { text.setTextAndPlaceCursorAtEnd("") }, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_icon_close), stringResource(R.string.search_clear))
            }
        },
    )
}
