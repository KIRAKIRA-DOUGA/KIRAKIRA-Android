@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.kirakira.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.MutableWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.ui.theme.ThemeColorDefaults

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
    content: @Composable ColumnScope.(PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var anchorOnScreen by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled && state.isOpen) state.animateToCollapsed()
    }
    PageSearchBarAnchor(
        text, state, placeholder, modifier, enabled, maxContentWidth,
        onBoundsChanged = { anchorOnScreen = it },
    )
    if (!isActive || !enabled || !state.isOpen) return

    Dialog(
        onDismissRequest = { scope.launch { state.animateToCollapsed() } },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        SearchDialogWindowEffect()
        var dialogOnScreen by remember { mutableStateOf<Offset?>(null) }
        Box(Modifier.fillMaxSize().onGloballyPositioned { dialogOnScreen = it.positionOnScreen() }) {
            val origin = dialogOnScreen
            if (origin != null) PageSearchOverlay(
                text = text,
                state = state,
                placeholder = placeholder,
                isActive = isActive,
                collapsedBoundsProvider = { anchorOnScreen?.translate(-origin) },
                predictiveBack = true,
                content = content,
            )
        }
    }
}

private val SearchBarState.isOpen: Boolean
    get() = targetValue == SearchBarValue.Expanded || currentValue == SearchBarValue.Expanded || isAnimating

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
                    val position = coordinates.positionOnScreen()
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
                    containerColor = ThemeColorDefaults.controlContainerColor(),
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
 * Full-screen search for a Dialog or an Activity scene. History keeps it mounted beneath video
 * destinations so the source list and playback viewport stay in the same window.
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
    predictiveBack: Boolean = false,
    content: @Composable ColumnScope.(PaddingValues) -> Unit,
) {
    LaunchedEffect(enabled) {
        if (!enabled && state.isOpen) state.animateToCollapsed()
    }
    if (!state.isOpen) return
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
            // Focusing the Material field can restart expansion and bring up the IME.
            snapshotFlow { state.progress == 1f && !state.isAnimating }.first { it }
            withFrameNanos { }
            if (currentIsActive && state.targetValue == SearchBarValue.Expanded) {
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
    val backPreview = remember { Animatable(0f) }
    if (isActive && LocalNavigationEventDispatcherOwner.current != null) {
        val backState = rememberNavigationEventState(NavigationEventInfo.None)
        val backSettleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        if (predictiveBack && !keyboardVisible) LaunchedEffect(backState) {
            snapshotFlow {
                (backState.transitionState as? NavigationEventTransitionState.InProgress)?.latestEvent?.progress
            }.filterNotNull().collect { backPreview.snapTo(it) }
        }
        NavigationBackHandler(
            state = backState,
            onBackCancelled = {
                if (predictiveBack) scope.launch { backPreview.animateTo(0f, backSettleSpec) }
            },
            onBackCompleted = {
                if (keyboardVisible) {
                    focus.clearFocus()
                    keyboard?.hide()
                    if (predictiveBack) scope.launch { backPreview.animateTo(0f, backSettleSpec) }
                } else scope.launch { state.animateToCollapsed() }
            },
        )
    }
    val progress = { state.progress * (1f - 0.1f * backPreview.value.coerceIn(0f, 1f)) }
    PageSearchOverlayLayout(
        progress = progress,
        collapsedBoundsProvider = collapsedBoundsProvider,
        inputField = {
            ExpandedPageSearchInput(progress) {
                PageSearchInputField(
                    text = text,
                    state = state,
                    placeholder = placeholder,
                    modifier = Modifier.fillMaxWidth().focusRequester(requester)
                        .onFocusChanged { fieldFocused = it.isFocused },
                    enabled = enabled,
                    expansionProgress = progress,
                )
            }
        },
        modifier = modifier.fillMaxSize().dismissKeyboardOnBlankTap(enabled = isActive && enabled),
    ) {
        PageSearchResults(content = content)
    }
}

@Composable
private fun ExpandedPageSearchInput(progress: () -> Float, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val containerColor = ThemeColorDefaults.controlContainerColor()
    Box(propagateMinConstraints = true) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = 1f - progress() }
                .background(containerColor, shape)
                .textFieldInnerShadow(shape),
        )
        content()
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PageSearchResults(
    content: @Composable ColumnScope.(PaddingValues) -> Unit,
) {
    val bottomInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    val unconsumedBottomInsets = remember { MutableWindowInsets() }
    Column(
        Modifier.fillMaxSize().clipToBounds()
            .imePadding()
            .onConsumedWindowInsetsChanged { unconsumedBottomInsets.insets = bottomInsets.exclude(it) },
    ) {
        content(unconsumedBottomInsets.asPaddingValues())
    }
}

@Composable
internal fun SearchDialogWindowEffect() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val colors = MaterialTheme.colorScheme
    val lightBars = colors.surface.luminance() > colors.onSurface.luminance()
    SideEffect {
        // The search transition owns the window's entry and exit.
        window.setWindowAnimations(0)
        window.setDimAmount(0f)
        WindowCompat.enableEdgeToEdge(window)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}

@Composable
private fun PageSearchInputField(
    text: TextFieldState,
    state: SearchBarState,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expansionProgress: (() -> Float)? = null,
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
            if (expansionProgress == null) {
                Icon(painterResource(R.drawable.ic_icon_search), contentDescription = null)
            } else {
                PageSearchLeadingIcon(
                    progress = expansionProgress,
                    enabled = enabled,
                    onCollapse = { scope.launch { state.animateToCollapsed() } },
                )
            }
        },
        trailingIcon = {
            SearchFieldTrailingAction(visible = text.text.isNotEmpty()) {
                IconButton(onClick = { text.setTextAndPlaceCursorAtEnd("") }, enabled = enabled) {
                    Icon(painterResource(R.drawable.ic_icon_close), stringResource(R.string.search_clear))
                }
            }
        },
    )
}

@Composable
private fun PageSearchLeadingIcon(progress: () -> Float, enabled: Boolean, onCollapse: () -> Unit) {
    val backActionVisible by remember(progress) { derivedStateOf { progress() >= 0.5f } }
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val endPadding = (4.dp * progress()).roundToPx()
            val icon = measurable.measure(constraints.offset(horizontal = -endPadding))
            layout(constraints.constrainWidth(icon.width + endPadding), icon.height) {
                icon.placeRelative(0, 0)
            }
        }.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_icon_search),
            contentDescription = null,
            modifier = Modifier.graphicsLayer { alpha = 1f - progress() },
        )
        Icon(
            painter = painterResource(R.drawable.ic_icon_arrow_left),
            contentDescription = null,
            modifier = Modifier.graphicsLayer { alpha = progress() },
        )
        if (backActionVisible) {
            val description = stringResource(R.string.navigate_back)
            IconButton(
                onClick = onCollapse,
                enabled = enabled,
                modifier = Modifier.matchParentSize().semantics { contentDescription = description },
                content = {},
            )
        }
    }
}

@Composable
internal fun SearchFieldTrailingAction(visible: Boolean, content: @Composable () -> Unit) {
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            modifier = if (visible) Modifier else Modifier.clearAndSetSemantics { },
            enter = fadeIn(effects),
            exit = fadeOut(effects),
            content = { content() },
        )
    }
}
