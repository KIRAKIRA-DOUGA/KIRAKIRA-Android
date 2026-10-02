package moe.kirakira.feature.history

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import moe.kirakira.R
import moe.kirakira.data.history.HistoryEntry
import moe.kirakira.feature.search.QuerySearchBar
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.VideoArtwork
import moe.kirakira.feature.video.durationText
import moe.kirakira.feature.video.isReadyForContent
import moe.kirakira.ui.components.CollapsibleTopAppBar
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentUnavailableAction
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.connectedListItemShadow
import moe.kirakira.ui.components.connectedListItemShapes
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.components.rememberCollapsibleTopAppBarScrollBehavior
import moe.kirakira.ui.theme.KIRAKIRATheme

@Composable
internal fun HistoryPage(
    model: HistoryViewModel,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val snapshot by model.history.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    LaunchedEffect(isActive, session.revision, session.isReadyForContent) {
        if (isActive && session.isReadyForContent) model.refresh()
    }
    key(session.revision) {
        HistoryScreen(
            state = if (snapshot.revision == session.revision) {
                ContentState(snapshot.entries, snapshot.loading, snapshot.error)
            } else ContentState(loading = true),
            signedIn = session.activeUuid != null,
            ready = session.isReadyForContent,
            query = query.text.takeIf { query.revision == session.revision }.orEmpty(),
            onQueryChange = model::updateQuery,
            onRefresh = model::refresh,
            onBack = onBack,
            onLogin = onLogin,
            onOpenVideo = onOpenVideo,
            modifier = modifier,
            isActive = isActive,
        )
    }
}

@Composable
internal fun HistoryScreen(
    state: ContentState<List<HistoryEntry>>,
    signedIn: Boolean,
    ready: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenVideo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val scroll = rememberCollapsibleTopAppBarScrollBehavior()
    val list = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val direction = LocalLayoutDirection.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val sections = remember(state.data, query, zone) {
        val term = query.trim()
        state.data.orEmpty().filter {
            term.isEmpty() || it.video.title.contains(term, ignoreCase = true) ||
                it.video.author.contains(term, ignoreCase = true)
        }.sortedByDescending { it.updatedAt }.groupBy {
            Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate()
        }
    }
    LaunchedEffect(state.error, isActive) {
        if (isActive && state.data != null) state.error?.let {
            snackbar.showSnackbar(context.getString(it.messageRes()))
        }
    }
    FrostedScaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = { CollapsibleTopAppBar(stringResource(R.string.me_history), onBack, scroll) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().consumeWindowInsets(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContentPullToRefresh(
                isRefreshing = state.loading && state.data != null,
                onRefresh = onRefresh,
                enabled = ready && signedIn,
                indicatorTopPadding = padding.calculateTopPadding(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    val statusHeight = (maxHeight - padding.calculateTopPadding() - padding.calculateBottomPadding() - if (signedIn && ready) 112.dp else 32.dp).coerceAtLeast(0.dp)
                    LazyColumn(
                        state = list,
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = padding.calculateStartPadding(direction) + 16.dp,
                            end = padding.calculateEndPadding(direction) + 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 24.dp,
                        ),
                    ) {
                        if (signedIn && ready) item("search") {
                            QuerySearchBar(
                                query = query,
                                onQueryChange = onQueryChange,
                                onSearch = { focus.clearFocus() },
                                placeholder = stringResource(R.string.history_search),
                                onClear = { onQueryChange("") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            )
                        }
                        if (!ready || !signedIn || state.data == null || sections.isEmpty()) item("status") {
                            val statusModifier = Modifier.fillMaxWidth().heightIn(min = statusHeight)
                            if (ready && !signedIn) ContentUnavailableView(
                                state = ContentUnavailableState.EMPTY,
                                modifier = statusModifier,
                                title = stringResource(R.string.history_sign_in),
                                description = null,
                                iconRes = R.drawable.ic_symbol_history,
                                primaryAction = ContentUnavailableAction(stringResource(R.string.auth_sign_in), onLogin),
                                presentation = ContentUnavailablePresentation.INLINE,
                            ) else ContentStatus(
                                state = state.copy(loading = !ready || (state.loading && state.data == null),
                                    error = state.error.takeIf { state.data == null }),
                                onRetry = onRefresh,
                                modifier = statusModifier,
                                empty = sections.isEmpty(),
                                emptyTitle = stringResource(if (query.trim().isEmpty()) R.string.history_empty else R.string.history_no_results),
                                emptyIconRes = if (query.trim().isEmpty()) R.drawable.ic_symbol_history else R.drawable.ic_symbol_search,
                            )
                        }
                        if (ready && signedIn) sections.forEach { (day, entries) ->
                            item("day-$day") {
                                Text(
                                    DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(
                                        Date.from(day.atStartOfDay(zone).toInstant()),
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)
                                        .semantics { heading() },
                                )
                            }
                            itemsIndexed(entries, key = { _, entry -> "video-${entry.video.id}" }) { index, entry ->
                                HistoryRow(entry, index, entries.size, { onOpenVideo(entry.video.id) },
                                    Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, index: Int, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val time = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(entry.updatedAt))
    // Keeping artwork in the content slot lets the text use the full width with large fonts.
    SegmentedListItem(
        onClick = onClick,
        modifier = modifier.connectedListItemShadow(index, count),
        shapes = connectedListItemShapes(index, count),
        supportingContent = null,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val artwork: @Composable (Modifier) -> Unit = { artworkModifier ->
                Box(artworkModifier.aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium)) {
                    VideoArtwork(entry.video.image, Modifier.fillMaxSize())
                    entry.progress?.let { progress ->
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                            drawStopIndicator = {},
                        )
                    }
                }
            }
            val info: @Composable (Modifier) -> Unit = { infoModifier ->
                Column(infoModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.video.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (entry.video.author.isNotBlank()) Text("@${entry.video.author}",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HistoryMetadata(R.drawable.ic_symbol_history, time, stringResource(R.string.history_watched_at, time))
                        entry.positionMs?.let { position ->
                            val progress = if (entry.video.durationMs != null) stringResource(
                                R.string.history_position_duration, durationText(position), durationText(entry.video.durationMs),
                            ) else durationText(position)
                            HistoryMetadata(R.drawable.ic_symbol_play_circle, progress,
                                stringResource(R.string.history_playback_position, progress))
                        }
                    }
                }
            }
            if (maxWidth / fontScale < 300.dp) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                artwork(Modifier.fillMaxWidth())
                info(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                artwork(Modifier.weight(0.36f))
                info(Modifier.weight(0.64f))
            }
        }
    }
}

@Composable
private fun HistoryMetadata(@androidx.annotation.DrawableRes icon: Int, text: String, description: String) {
    Row(modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "History · Guest", showBackground = true)
@Preview(name = "历史记录 · 中文", locale = "zh", showBackground = true)
@Preview(name = "History · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "History · Large text", fontScale = 1.5f, showBackground = true)
@Composable
private fun HistoryPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        HistoryScreen(ContentState(), false, true, "", {}, {}, {}, {}, {})
    }
}
