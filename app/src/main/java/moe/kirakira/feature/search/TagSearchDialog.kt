package moe.kirakira.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import moe.kirakira.R
import moe.kirakira.ui.components.FrostedScaffold
import kotlin.math.roundToInt

@Composable
internal fun TagSearchDialog(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchor = LocalTagSearchAnchor.current
    var expanded by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<SearchEvent?>(null) }
    val latestEvent by rememberUpdatedState(onEvent)
    val dispatch: (SearchEvent) -> Unit = { event ->
        if (pendingExit == null) {
            when (event) {
                SearchEvent.CloseTags, SearchEvent.ApplyTags -> {
                    pendingExit = event
                    expanded = false
                }

                else -> onEvent(event)
            }
        }
    }
    Dialog(
        onDismissRequest = { dispatch(SearchEvent.CloseTags) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
        ),
    ) {
        val view = LocalView.current
        // The Compose transition owns both directions; a platform fade would jump or run twice.
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setWindowAnimations(0)
                setDimAmount(0f)
            }
        }
        var rootOnScreen by remember { mutableStateOf<Offset?>(null) }
        val density = LocalDensity.current
        SharedTransitionLayout(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOnScreen = it.positionOnScreen() },
        ) {
            val transition = updateTransition(expanded, label = "Tag picker expansion")
            val sharedScope = this
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val settled = expanded && transition.currentState && !transition.isRunning && !isTransitionActive
            LaunchedEffect(rootOnScreen != null) {
                if (rootOnScreen != null) {
                    // Give the measured source one frame before changing the AnimatedContent target.
                    withFrameNanos { }
                    if (pendingExit == null) expanded = true
                }
            }
            LaunchedEffect(pendingExit, transition.currentState, transition.isRunning, isTransitionActive, expanded) {
                val event = pendingExit
                if (event != null && !expanded && !transition.currentState &&
                    !transition.isRunning && !isTransitionActive
                ) latestEvent(event)
            }
            transition.AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { ContentTransform(fadeIn(effects), fadeOut(effects), sizeTransform = null) },
                contentAlignment = Alignment.TopStart,
            ) { showingPicker ->
                val fieldModifier = with(sharedScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState("tag-search-field"),
                        animatedVisibilityScope = this@AnimatedContent,
                        boundsTransform = { _, _ -> spatial },
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    )
                }
                if (showingPicker) {
                    TagSearchDialogContent(
                        state = state,
                        onEvent = dispatch,
                        fieldModifier = fieldModifier,
                        requestInputFocus = settled,
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        val source = anchor?.boundsOnScreen
                        val origin = rootOnScreen
                        if (source != null && origin != null) {
                            TagSearchField(
                                tags = if (pendingExit == SearchEvent.ApplyTags) state.draftTags else state.tags,
                                onAdd = {},
                                onRemove = {},
                                modifier = Modifier
                                    .absoluteOffset {
                                        IntOffset(
                                            (source.left - origin.x).roundToInt(),
                                            (source.top - origin.y).roundToInt(),
                                        )
                                    }
                                    .requiredWidth(with(density) { source.width.toDp() })
                                    .then(fieldModifier)
                                    .clearAndSetSemantics { },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSearchDialogContent(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    fieldModifier: Modifier,
    requestInputFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val inputFocus = remember { FocusRequester() }
    val close = {
        focus.clearFocus()
        keyboard?.hide()
        onEvent(SearchEvent.CloseTags)
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = {
            if (keyboardVisible) {
                keyboard?.hide()
                focus.clearFocus()
            } else {
                close()
            }
        },
    )
    LaunchedEffect(requestInputFocus) {
        if (requestInputFocus) inputFocus.requestFocus()
    }
    FrostedScaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = if (state.candidates.data.isNullOrEmpty()) {
            MaterialTheme.colorScheme.surface
        } else MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchIconButton(
                            stringResource(R.string.search_close_tags), R.drawable.ic_symbol_close, close,
                        )
                        Text(
                            stringResource(R.string.search_choose_tags),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(
                            onClick = {
                                focus.clearFocus()
                                keyboard?.hide()
                                onEvent(SearchEvent.ApplyTags)
                            },
                            enabled = state.ready && (state.draftTags.isNotEmpty() || state.tags.isNotEmpty()),
                        ) { Text(stringResource(R.string.search_apply_tags)) }
                    }
                    SearchFieldContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .then(fieldModifier),
                    ) {
                        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())) {
                            SearchBarDefaults.InputField(
                                query = state.tagQuery,
                                onQueryChange = { onEvent(SearchEvent.TagQueryChanged(it)) },
                                onSearch = { onEvent(SearchEvent.RetryTags) },
                                expanded = false,
                                onExpandedChange = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(inputFocus),
                                enabled = state.ready,
                                placeholder = { Text(stringResource(R.string.management_search_tags)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_symbol_search), null, Modifier.size(24.dp))
                                },
                                trailingIcon = if (state.tagQuery.isNotEmpty()) {
                                    {
                                        SearchIconButton(
                                            stringResource(R.string.search_clear),
                                            R.drawable.ic_symbol_close,
                                            { onEvent(SearchEvent.TagQueryChanged("")) },
                                            enabled = state.ready,
                                        )
                                    }
                                } else null,
                            )
                            if (state.draftTags.isNotEmpty()) {
                                SelectedSearchTags(
                                    tags = state.draftTags,
                                    onRemove = { onEvent(SearchEvent.ToggleTag(it)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
                                        .heightIn(max = SearchBarDefaults.InputFieldHeight * 2)
                                        .verticalScroll(rememberScrollState()),
                                    enabled = state.ready,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            TagSearchCandidates(state, onEvent, contentPadding = padding)
        }
    }
}
