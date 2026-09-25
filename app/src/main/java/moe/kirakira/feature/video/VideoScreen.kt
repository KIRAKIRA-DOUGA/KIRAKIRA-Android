package moe.kirakira.feature.video

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.PlaceholderAvatar
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.topAppBarShadow

@Composable
internal fun VideoScreen(
    state: VideoUiState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onTabChange: (VideoTab) -> Unit,
    onFollowingChange: (Boolean) -> Unit,
    onReactionChange: (VideoReaction) -> Unit,
    onSavedChange: (Boolean) -> Unit,
    onUnavailableAction: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val commentsStateHolder = rememberSaveableStateHolder()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = if (state.tab == VideoTab.INTRODUCTION) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentWidth = minOf(maxWidth, 840.dp)
            val videoHeight = minOf(contentWidth * 9f / 16f, maxHeight * 0.4f)
            Column(Modifier.width(contentWidth).fillMaxSize()) {
                Column(Modifier.topAppBarShadow()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(videoHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoArtwork(
                            modifier = Modifier.width(videoHeight * 16f / 9f).fillMaxSize(),
                            showPlayerLabel = true,
                        )
                        FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_symbol_arrow_back),
                                contentDescription = stringResource(R.string.navigate_back),
                            )
                        }
                    }
                    PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                        VideoTab.entries.forEach { tab ->
                            Tab(
                                selected = state.tab == tab,
                                onClick = { onTabChange(tab) },
                                text = {
                                    Text(
                                        stringResource(
                                            when (tab) {
                                                VideoTab.INTRODUCTION -> R.string.video_tab_introduction
                                                VideoTab.COMMENTS -> R.string.video_tab_comments
                                                VideoTab.DANMAKU -> R.string.video_tab_danmaku
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                if (state.tab == VideoTab.COMMENTS) {
                    commentsStateHolder.SaveableStateProvider("video_comments") {
                        VideoCommentsPage(
                            listState = listState,
                            bottomPadding = innerPadding.calculateBottomPadding(),
                            onUnavailableAction = onUnavailableAction,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(
                            start = if (state.tab == VideoTab.DANMAKU) 8.dp else 20.dp,
                            end = if (state.tab == VideoTab.DANMAKU) 8.dp else 20.dp,
                            top = if (state.tab == VideoTab.DANMAKU) 8.dp else 24.dp,
                            bottom = innerPadding.calculateBottomPadding() +
                                if (state.tab == VideoTab.DANMAKU) 16.dp else 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(
                            if (state.tab == VideoTab.DANMAKU) ListItemDefaults.SegmentedGap else 20.dp,
                        ),
                    ) {
                        when (state.tab) {
                            VideoTab.INTRODUCTION -> {
                                item(key = "author") {
                                    VideoAuthor(state.following, onFollowingChange)
                                }
                                item(key = "title") {
                                    SelectionContainer {
                                        Text(
                                            text = stringResource(R.string.video_demo_title),
                                            style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.semantics { heading() },
                                        )
                                    }
                                }
                                item(key = "metadata") {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        VideoMetadata(R.drawable.ic_symbol_play_circle, stringResource(R.string.video_views))
                                        VideoMetadata(R.drawable.ic_symbol_calendar_today, stringResource(R.string.video_publish_time))
                                        VideoMetadata(R.drawable.ic_symbol_category, stringResource(R.string.video_category))
                                    }
                                }
                                item(key = "description") {
                                    SelectionContainer {
                                        Text(
                                            text = stringResource(R.string.video_demo_description),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                                item(key = "actions") {
                                    VideoActions(state, onReactionChange, onSavedChange, onUnavailableAction)
                                }
                            }
                            VideoTab.COMMENTS -> Unit
                            VideoTab.DANMAKU -> {
                                item(key = "danmaku_count") {
                                    VideoListCount(R.plurals.video_danmaku_total, demoVideoDanmaku.size)
                                }
                                itemsIndexed(demoVideoDanmaku, key = { _, entry -> "danmaku_${entry.id}" }) { index, entry ->
                                    DanmakuListItem(
                                        time = stringResource(
                                            R.string.video_danmaku_time,
                                            entry.seconds / 60,
                                            entry.seconds % 60,
                                        ),
                                        text = stringResource(entry.bodyRes),
                                        index = index,
                                        count = demoVideoDanmaku.size,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Draw behind the transparent system bar while retaining the content's existing insets.
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Color.Black),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoAuthor(following: Boolean, onFollowingChange: (Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackButton = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f
        if (stackButton) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthorIdentity()
                FollowButton(following, onFollowingChange)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AuthorIdentity(Modifier.weight(1f))
                FollowButton(following, onFollowingChange)
            }
        }
    }
}

@Composable
private fun AuthorIdentity(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlaceholderAvatar(size = 48.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SelectionContainer {
                Text(stringResource(R.string.video_demo_author), style = MaterialTheme.typography.titleMedium)
            }
            SelectionContainer {
                Text(
                    stringResource(R.string.video_demo_handle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FollowButton(following: Boolean, onFollowingChange: (Boolean) -> Unit) {
    ToggleButton(
        checked = following,
        onCheckedChange = onFollowingChange,
        icon = {
            Icon(
                painterResource(if (following) R.drawable.ic_symbol_check else R.drawable.ic_symbol_add),
                contentDescription = null,
            )
        },
    ) {
        Text(stringResource(if (following) R.string.video_following else R.string.video_follow))
    }
}

@Composable
private fun VideoMetadata(@DrawableRes icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VideoActions(
    state: VideoUiState,
    onReactionChange: (VideoReaction) -> Unit,
    onSavedChange: (Boolean) -> Unit,
    onUnavailableAction: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ReactionButton(
            checked = state.reaction == VideoReaction.LIKE,
            onCheckedChange = { onReactionChange(VideoReaction.LIKE) },
            icon = R.drawable.ic_symbol_thumb_up,
            label = stringResource(R.string.video_like),
            count = 128 + if (state.reaction == VideoReaction.LIKE) 1 else 0,
        )
        ReactionButton(
            checked = state.reaction == VideoReaction.DISLIKE,
            onCheckedChange = { onReactionChange(VideoReaction.DISLIKE) },
            icon = R.drawable.ic_symbol_thumb_down,
            label = stringResource(R.string.video_dislike),
            count = if (state.reaction == VideoReaction.DISLIKE) 1 else 0,
        )
        ReactionButton(
            checked = state.saved,
            onCheckedChange = onSavedChange,
            icon = R.drawable.ic_symbol_star,
            label = stringResource(R.string.video_save),
            count = 32 + if (state.saved) 1 else 0,
        )
        VideoActionButton(R.drawable.ic_symbol_download, stringResource(R.string.video_download), onUnavailableAction)
        VideoActionButton(R.drawable.ic_symbol_share, stringResource(R.string.video_share), onUnavailableAction)
        VideoActionButton(R.drawable.ic_symbol_more_horiz, stringResource(R.string.video_more), onUnavailableAction)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReactionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes icon: Int,
    label: String,
    count: Int,
) {
    val description = stringResource(R.string.video_action_count, label, count)
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { contentDescription = description },
        icon = { Icon(painterResource(icon), contentDescription = null) },
    ) {
        Text(stringResource(R.string.video_count, count))
    }
}

@Composable
private fun VideoActionButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = label)
    }
}

@Preview(name = "Video · English", locale = "en", widthDp = 412, heightDp = 892)
@Preview(name = "视频 · 中文", locale = "zh", widthDp = 412, heightDp = 892)
@Preview(name = "Video · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 412, heightDp = 892)
@Preview(name = "Video · Large text", fontScale = 2f, widthDp = 320, heightDp = 640)
@Preview(name = "Video · Wide", widthDp = 1000, heightDp = 700)
@Composable
private fun VideoScreenPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        VideoPage(onBack = {})
    }
}
