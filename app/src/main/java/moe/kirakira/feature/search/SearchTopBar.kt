package moe.kirakira.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import moe.kirakira.R
import moe.kirakira.ui.components.AppChipDefaults
import moe.kirakira.ui.components.search.LocalTagSearchAnchor
import moe.kirakira.ui.components.search.QuerySearchBar
import moe.kirakira.ui.components.search.TagSearchField

@Composable
internal fun SearchTopBar(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val tagAnchor = LocalTagSearchAnchor.current
    val motion = MaterialTheme.motionScheme
    val closeSearch = {
        focus.clearFocus()
        keyboard?.hide()
        onEvent(SearchEvent.Clear)
    }
    Box(
        modifier = modifier.fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
            Column(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnimatedContent(
                    targetState = state.mode,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                            .using(SizeTransform { _, _ -> motion.fastSpatialSpec() })
                    },
                    label = "Search mode field",
                ) { mode ->
                    val isCurrentMode = mode == state.mode
                    val fieldModifier = if (isCurrentMode) Modifier else Modifier.clearAndSetSemantics { }
                    val dispatch: (SearchEvent) -> Unit = { if (isCurrentMode) onEvent(it) }
                    val clear = { if (isCurrentMode) closeSearch() }
                    if (mode == SearchMode.KEYWORD) {
                        QuerySearchBar(
                            query = state.keyword,
                            onQueryChange = { dispatch(SearchEvent.KeywordChanged(it)) },
                            onSearch = {
                                focus.clearFocus()
                                dispatch(SearchEvent.Submit)
                            },
                            placeholder = stringResource(R.string.search_placeholder),
                            modifier = fieldModifier.fillMaxWidth(),
                            enabled = state.ready,
                            showClear = state.keyword.isNotEmpty() || state.submitted is SearchCriteria.Keyword,
                            onClear = clear,
                        )
                    } else {
                        val scrollState = rememberScrollState()
                        val returnTags = tagAnchor?.returnTags
                        DisposableEffect(tagAnchor, scrollState) {
                            onDispose {
                                if (tagAnchor != null && tagAnchor.scrollState === scrollState) {
                                    tagAnchor.boundsOnScreen = null
                                    tagAnchor.scrollState = null
                                }
                            }
                        }
                        TagSearchField(
                            tags = returnTags ?: state.tags,
                            onAdd = {
                                focus.clearFocus()
                                dispatch(SearchEvent.OpenTags)
                            },
                            onRemove = { dispatch(SearchEvent.RemoveTag(it.id)) },
                            modifier = fieldModifier.fillMaxWidth()
                                .graphicsLayer { alpha = if (tagAnchor?.drawsInDialog == true) 0f else 1f }
                                .onGloballyPositioned {
                                    val position = it.positionOnScreen()
                                    if (isCurrentMode && tagAnchor != null && position.isSpecified &&
                                        it.size.width > 0 && it.size.height > 0
                                    ) {
                                        tagAnchor.boundsOnScreen = Rect(position, it.size.toSize())
                                        tagAnchor.scrollState = scrollState
                                    }
                                },
                            enabled = state.ready,
                            onClear = clear,
                            scrollState = scrollState,
                            animateSize = returnTags == null,
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SearchMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick = {
                                focus.clearFocus()
                                onEvent(SearchEvent.ModeChanged(mode))
                            },
                            enabled = state.ready,
                            label = { Text(stringResource(mode.label)) },
                            shape = CircleShape,
                            border = null,
                            colors = AppChipDefaults.filterColors(),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(
                                        if (mode == SearchMode.KEYWORD) R.drawable.ic_symbol_match_word
                                        else R.drawable.ic_icon_tag,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
