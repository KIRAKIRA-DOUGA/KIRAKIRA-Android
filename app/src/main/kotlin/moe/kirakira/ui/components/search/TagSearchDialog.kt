package moe.kirakira.ui.components.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import moe.kirakira.R
import moe.kirakira.data.content.VideoTag
import moe.kirakira.ui.components.button.FlatTextButton
import moe.kirakira.ui.components.form.ObserveTextFieldBinding
import moe.kirakira.ui.components.form.TextFieldBinding
import moe.kirakira.ui.components.form.dismissKeyboardOnBlankTap
import moe.kirakira.ui.components.icon.ShadingIcon
import moe.kirakira.ui.components.scaffold.FrostedScaffold
import moe.kirakira.ui.components.scaffold.PageTopAppBar
import moe.kirakira.ui.content.TagSearchState

private enum class TagSearchExit { DISMISS, CONFIRM }

@Composable
internal fun TagSearchDialog(
    search: TagSearchState,
    selectedTags: List<VideoTag>,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onToggleTag: (VideoTag) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    confirmEnabled: Boolean = selectedTags.isNotEmpty(),
    confirming: Boolean = false,
    confirmOnExit: Boolean = false,
    sourceTags: List<VideoTag> = emptyList(),
    snackbarHost: @Composable () -> Unit = {},
) {
    val anchor = LocalTagSearchAnchor.current
    var expanded by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<TagSearchExit?>(null) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestConfirm by rememberUpdatedState(onConfirm)
    val dismiss = {
        if (!confirming && pendingExit == null) pendingExit = TagSearchExit.DISMISS
    }
    FullScreenSearchDialog(onDismiss = dismiss, dismissOnBackPress = false) {
        var rootOnScreen by remember { mutableStateOf<Offset?>(null) }
        val density = LocalDensity.current
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
        val hasSource = anchor?.boundsOnScreen != null && rootOnScreen != null
        val fieldShape = MaterialTheme.shapes.large
        DisposableEffect(anchor, hasSource) {
            if (hasSource) anchor.drawsInDialog = true
            onDispose {
                if (hasSource) {
                    anchor.drawsInDialog = false
                    anchor.returnTags = null
                }
            }
        }
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            onBackCompleted = {
                if (!confirming && pendingExit == null) {
                    if (keyboardVisible) {
                        focus.clearFocus()
                        keyboard?.hide()
                    } else {
                        dismiss()
                    }
                }
            },
        )
        SharedTransitionLayout(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val position = it.positionOnScreen()
                    if (position.isSpecified) rootOnScreen = position
                },
        ) {
            val transition = updateTransition(expanded, label = "Tag picker expansion")
            val sharedScope = this
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val settled = pendingExit == null && expanded && transition.currentState &&
                !transition.isRunning && !isTransitionActive
            LaunchedEffect(Unit) {
                // Allow the window origin and its source field to be placed before starting.
                withFrameNanos { }
                withFrameNanos { }
                if (pendingExit == null) expanded = true
            }
            LaunchedEffect(pendingExit, transition) {
                val event = pendingExit ?: return@LaunchedEffect
                focus.clearFocus()
                keyboard?.hide()
                if (event == TagSearchExit.CONFIRM && hasSource) {
                    anchor.returnTags = selectedTags
                    // Let the Activity lay out the destination with the tags that will be committed.
                    withFrameNanos { }
                    withFrameNanos { }
                }
                expanded = false
                snapshotFlow {
                    !transition.currentState && !transition.targetState &&
                        !transition.isRunning && !isTransitionActive
                }.first { it }
                when (event) {
                    TagSearchExit.DISMISS -> latestDismiss()
                    TagSearchExit.CONFIRM -> latestConfirm()
                }
            }
            transition.AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { ContentTransform(fadeIn(effects), fadeOut(effects), sizeTransform = null) },
                contentAlignment = Alignment.TopStart,
            ) { showingPicker ->
                val backgroundModifier = with(sharedScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("tag-search-background"),
                        animatedVisibilityScope = this@AnimatedContent,
                        boundsTransform = { _, _ -> spatial },
                        zIndexInOverlay = -1f,
                    )
                }
                val contentModifier = with(sharedScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState("tag-search-content"),
                        animatedVisibilityScope = this@AnimatedContent,
                        enter = fadeIn(effects),
                        exit = fadeOut(effects),
                        boundsTransform = { _, _ -> spatial },
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                            ContentScale.FillWidth,
                            Alignment.TopStart,
                        ),
                        clipInOverlayDuringTransition = OverlayClip(fieldShape),
                    )
                }
                if (showingPicker) {
                    TagSearchDialogContent(
                        search = search,
                        selectedTags = selectedTags,
                        onQueryChange = onQueryChange,
                        onRetry = onRetry,
                        onToggleTag = onToggleTag,
                        onDismiss = dismiss,
                        backgroundModifier = backgroundModifier,
                        contentModifier = contentModifier,
                        requestInputFocus = settled,
                        modifier = if (pendingExit == null) Modifier else Modifier.clearAndSetSemantics { },
                        enabled = enabled && !confirming && pendingExit == null,
                        snackbarHost = snackbarHost,
                        actions = {
                            FlatTextButton(
                                onClick = {
                                    if (pendingExit == null) {
                                        if (confirmOnExit) {
                                            pendingExit = TagSearchExit.CONFIRM
                                        } else {
                                            focus.clearFocus()
                                            keyboard?.hide()
                                            onConfirm()
                                        }
                                    }
                                },
                                enabled = enabled && confirmEnabled && pendingExit == null,
                                loading = confirming,
                            ) {
                                Text(confirmLabel)
                            }
                        },
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        val source = anchor?.boundsOnScreen
                        val origin = rootOnScreen
                        if (source != null && origin != null) {
                            TagSearchField(
                                tags = anchor.returnTags ?: sourceTags,
                                onAdd = {},
                                onRemove = {},
                                enabled = enabled,
                                scrollState = rememberScrollState(anchor.scrollState?.value ?: 0),
                                animateSize = false,
                                backgroundModifier = backgroundModifier,
                                contentModifier = contentModifier,
                                modifier = Modifier
                                    .absoluteOffset {
                                        IntOffset(
                                            (source.left - origin.x).roundToInt(),
                                            (source.top - origin.y).roundToInt(),
                                        )
                                    }
                                    .requiredSize(
                                        width = with(density) { source.width.toDp() },
                                        height = with(density) { source.height.toDp() },
                                    )
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
    search: TagSearchState,
    selectedTags: List<VideoTag>,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onToggleTag: (VideoTag) -> Unit,
    onDismiss: () -> Unit,
    backgroundModifier: Modifier,
    contentModifier: Modifier,
    requestInputFocus: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    snackbarHost: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val binding = remember { TextFieldBinding(search.query) }
    ObserveTextFieldBinding(binding, search.query, onQueryChange, enabled)
    val retry = {
        binding.publish(onQueryChange)
        onRetry()
    }
    val inputFocus = remember { FocusRequester() }
    val hasCandidates = !search.candidates.data.isNullOrEmpty()
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (hasCandidates) colors.surfaceContainerLow else colors.surface,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>(),
        label = "Tag picker background",
    )
    LaunchedEffect(requestInputFocus) {
        if (requestInputFocus) inputFocus.requestFocus()
    }
    FrostedScaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .dismissKeyboardOnBlankTap(),
        containerColor = containerColor,
        contentColor = colors.onSurface,
        snackbarHost = snackbarHost,
        topBar = {
            Column {
                Box {
                    ShadingIcon(
                        icon = R.drawable.ic_icon_tag,
                        modifier = Modifier.matchParentSize(),
                        alignment = Alignment.BottomEnd,
                        endPadding = 0.dp,
                        offset = DpOffset(32.dp, 32.dp),
                    )
                    PageTopAppBar(
                        title = stringResource(R.string.search_choose_tags),
                        onBack = onDismiss,
                        actions = actions,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal)),
                    contentAlignment = Alignment.Center,
                ) {
                    SearchFieldContainer(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        backgroundModifier = backgroundModifier,
                        contentModifier = contentModifier,
                    ) { fieldColor ->
                        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())) {
                            SearchQueryField(
                                state = binding.editor,
                                onSearch = retry,
                                placeholder = stringResource(R.string.management_search_tags),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(inputFocus)
                                    .focusProperties { canFocus = requestInputFocus },
                                enabled = enabled,
                                trailingIcon = {
                                    SearchFieldTrailingAction(visible = binding.editor.text.isNotEmpty()) {
                                        SearchIconButton(
                                            stringResource(R.string.search_clear),
                                            R.drawable.ic_icon_close,
                                            {
                                                binding.replace("")
                                                onQueryChange("")
                                            },
                                            enabled = enabled,
                                        )
                                    }
                                },
                            )
                            if (selectedTags.isNotEmpty()) {
                                SelectedSearchTags(
                                    tags = selectedTags,
                                    onRemove = onToggleTag,
                                    parentContainerColor = fieldColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    enabled = enabled,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            TagSearchCandidates(search, selectedTags, onToggleTag, retry, enabled = enabled, contentPadding = padding)
        }
    }
}
