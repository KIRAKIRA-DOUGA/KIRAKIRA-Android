package moe.kirakira.feature.tag

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import moe.kirakira.R
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.data.content.VideoTag
import moe.kirakira.feature.settings.VideoCardLayout
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.VideoCardRow
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.connectedListItemShadow
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun TagPage(model: TagViewModel, onBack: () -> Unit, onOpenVideo: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tag by model.tag.collectAsStateWithLifecycle()
    val videos by model.videos.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    key(session.revision) {
        TagScreen(model.tagId, tag, videos, session.revision, model::refresh, model::refreshTag,
            model::refreshVideos, onBack, onOpenVideo, modifier)
    }
}

@Composable
internal fun TagScreen(
    tagId: Long,
    tag: ContentState<TagLookup>,
    videos: ContentState<List<VideoSummary>>,
    sessionRevision: Long,
    onRefresh: () -> Unit,
    onRetryTag: () -> Unit,
    onRetryVideos: () -> Unit,
    onBack: () -> Unit,
    onOpenVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    layout: VideoCardLayout = VideoCardLayout.GRID,
) {
    val language = LocalConfiguration.current.locales[0].toLanguageTag()
    val direction = LocalLayoutDirection.current
    val value = tag.data?.tag
    val name = value?.displayName(language) ?: "#$tagId"
    val scroll = rememberCollapsibleTopAppBarScrollBehavior()
    var showNames by rememberSaveable(tagId, sessionRevision) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val rows = videos.data.orEmpty().chunked(layout.columns)
    FrostedScaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            CollapsibleTopAppBar(stringResource(R.string.tag_title), onBack, scroll, actions = {
                IconButton(onClick = { showNames = true }, enabled = value != null) {
                    Icon(painterResource(R.drawable.ic_symbol_info), stringResource(R.string.tag_names))
                }
            })
        },
    ) { padding ->
        ContentPullToRefresh(
            isRefreshing = (tag.loading || videos.loading) && (tag.data != null || videos.data != null),
            onRefresh = onRefresh,
            indicatorTopPadding = padding.calculateTopPadding(),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = padding.calculateStartPadding(direction) + 20.dp,
                        end = padding.calculateEndPadding(direction) + 20.dp,
                        top = padding.calculateTopPadding() + 28.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item("identity") {
                        Column(
                            Modifier.fillMaxWidth().animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(name, style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
                            value?.originalName()?.takeIf { it != name }?.let {
                                Text(it, style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (tag.error != null || value == null) item("tag-status") {
                        ContentStatus(tag.copy(loading = tag.loading && tag.data == null), onRetryTag,
                            Modifier.fillMaxWidth(), emptyTitle = stringResource(R.string.tag_not_found),
                            emptyIconRes = R.drawable.ic_symbol_label, empty = tag.data != null && value == null)
                    }
                    item("count") {
                        Text(videos.data?.let { pluralStringResource(R.plurals.tag_video_count, it.size, it.size) }
                            ?: stringResource(R.string.tag_related_videos),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().semantics { heading() })
                    }
                    if (videos.error != null || videos.data.isNullOrEmpty()) item("video-status") {
                        ContentStatus(videos.copy(loading = videos.loading && videos.data == null), onRetryVideos,
                            Modifier.fillMaxWidth().heightIn(min = 180.dp), empty = videos.data.isNullOrEmpty(),
                            emptyTitle = stringResource(R.string.tag_videos_empty),
                            emptyIconRes = R.drawable.ic_symbol_video_library)
                    }
                    itemsIndexed(rows, key = { _, row -> row.first().id }) { _, row ->
                        VideoCardRow(row, layout, onOpenVideo, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (showNames && value != null) {
        TagNamesSheet(value, onDismiss = { showNames = false })
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TagNamesSheet(tag: VideoTag, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        LazyColumn(contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp)) {
            item {
                Text(stringResource(R.string.tag_names), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 20.dp).semantics { heading() })
            }
            if (tag.languages.isEmpty()) item { Text(stringResource(R.string.tag_names_empty)) }
            tag.languages.forEachIndexed { groupIndex, group ->
                item("language-$groupIndex") {
                    Text(tagLanguageName(group.language), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() })
                }
                if (group.names.isEmpty()) item("empty-$groupIndex") { Text(stringResource(R.string.tag_names_empty)) }
                itemsIndexed(group.names, key = { index, _ -> "$groupIndex-$index" }) { index, entry ->
                    val labels = listOfNotNull(
                        if (entry.default) stringResource(R.string.tag_default_name) else null,
                        if (entry.original) stringResource(R.string.tag_original_name) else null,
                    )
                    SegmentedListItem(
                        shapes = connectedListItemShapes(index, group.names.size),
                        modifier = Modifier.connectedListItemShadow(index, group.names.size),
                        supportingContent = if (labels.isEmpty()) null else {
                            { FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                labels.forEach { Text(it, color = MaterialTheme.colorScheme.primary) }
                            } }
                        },
                        content = { Text(entry.name.ifBlank { stringResource(R.string.tag_name_missing) }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun tagLanguageName(code: String): String {
    val locale = LocalConfiguration.current.locales[0]
    val language = when (code) { "zhs" -> "zh-Hans"; "zht" -> "zh-Hant"; else -> code }
    if (code == "other") return stringResource(R.string.tag_other_language)
    return Locale.forLanguageTag(language).getDisplayName(locale).takeIf { it.isNotBlank() } ?: code
}

@Preview(showBackground = true)
@Composable
private fun TagScreenPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        TagScreen(1, ContentState(), ContentState(emptyList()), 0, {}, {}, {}, {}, {})
    }
}
