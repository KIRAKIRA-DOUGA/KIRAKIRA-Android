package moe.kirakira.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.ui.adaptive.LocalAppWindowLayout
import moe.kirakira.ui.components.button.BackdropIconButton
import moe.kirakira.ui.components.illustration.FavoritesLogo
import moe.kirakira.ui.components.status.ContentStatus
import moe.kirakira.ui.components.status.ContentStatusViewport
import moe.kirakira.ui.components.status.ContentUnavailablePresentation
import moe.kirakira.ui.components.status.ContentUnavailableState
import moe.kirakira.ui.components.status.ContentUnavailableView
import moe.kirakira.ui.components.status.messageRes
import moe.kirakira.ui.components.text.LocalNumberAnimationIdentity
import moe.kirakira.ui.components.video.VideoCardLayout
import moe.kirakira.ui.components.video.VideoCardRow
import moe.kirakira.ui.components.video.VideoCardRowSpacing
import moe.kirakira.ui.content.ContentState
import moe.kirakira.ui.navigation.imageSharedBounds
import moe.kirakira.ui.navigation.rememberImageContentScale
import moe.kirakira.ui.theme.ThemeColorDefaults

/** The single column widens up to this width; wider pages switch to two panes, so no side gaps remain. */
internal val ProfileTwoPaneMinWidth = 720.dp

/** The content width after horizontal safe insets. */
@Composable
internal fun profilePageAvailableWidth(maxWidth: Dp): Dp {
    val direction = LocalLayoutDirection.current
    val insets = ScaffoldDefaults.contentWindowInsets
    val horizontal = with(LocalDensity.current) {
        (insets.getLeft(this, direction) + insets.getRight(this, direction)).toDp()
    }
    return (maxWidth - horizontal).coerceAtLeast(0.dp)
}

/** Width of the start-side profile pane in the two-pane layout, or null for the narrow layout. */
@Composable
internal fun profileInfoPaneWidth(available: Dp, maxWidth: Dp): Dp? {
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    val window = LocalAppWindowLayout.current
    window.bookFold?.let { seam ->
        // As in VideoFeedGrid, measure the end side from the window's safe end edge; the
        // start side is the remaining content width before the crease.
        val insets = ScaffoldDefaults.contentWindowInsets
        val endWidth = with(density) {
            if (direction == LayoutDirection.Ltr) {
                (window.size.width - seam - insets.getRight(this, direction)).toDp()
            } else {
                (seam - insets.getLeft(this, direction)).toDp()
            }
        }
        val startPadding = with(density) {
            if (direction == LayoutDirection.Ltr) {
                insets.getLeft(this, direction)
            } else {
                insets.getRight(this, direction)
            }.toDp()
        }
        val foldWidth = maxWidth - endWidth - startPadding
        if (foldWidth >= 320.dp && available - foldWidth >= 360.dp) return foldWidth
    }
    return if (available > ProfileTwoPaneMinWidth) {
        (available * 0.34f).coerceIn(320.dp, 400.dp)
    } else {
        null
    }
}

@Composable
internal fun ProfileInfoContent(
    state: ProfileUiState,
    bioExpanded: Boolean,
    onBioExpandedChange: (Boolean) -> Unit,
    onFollowingChange: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onOpenFollowList: (FollowListKind) -> Unit,
    onOpenAvatar: () -> Unit,
    onOpenBanner: () -> Unit,
    onRetry: () -> Unit,
    coverRemainderHeight: Dp,
    profileError: ApiFailure?,
    statsError: ApiFailure?,
) {
    CompositionLocalProvider(LocalNumberAnimationIdentity provides state.profile.uid) {
        ProfileHeader(
            state = state,
            bioExpanded = bioExpanded,
            onBioExpandedChange = onBioExpandedChange,
            onFollowingChange = onFollowingChange,
            onEditProfile = onEditProfile,
            onOpenFollowList = onOpenFollowList,
            onOpenAvatar = onOpenAvatar,
            onOpenBanner = onOpenBanner,
            modifier = Modifier.fillMaxWidth(),
            coverRemainderHeight = coverRemainderHeight,
        )
    }
    if (profileError != null) {
        ContentUnavailableView(
            state = ContentUnavailableState.ERROR,
            title = stringResource(R.string.profile_load_failed),
            description = stringResource(profileError.messageRes()),
            onRetry = onRetry,
            presentation = ContentUnavailablePresentation.INLINE,
        )
    }
    if (statsError != null) {
        ContentUnavailableView(
            state = ContentUnavailableState.ERROR,
            title = stringResource(R.string.profile_stats_load_failed),
            description = stringResource(statsError.messageRes()),
            onRetry = onRetry,
            presentation = ContentUnavailablePresentation.INLINE,
        )
    }
}

@Composable
internal fun ProfileTabPage(
    tab: ProfileTab,
    page: Int,
    videos: ContentState<List<VideoSummary>>,
    columns: Int,
    layout: VideoCardLayout,
    pagerState: PagerState,
    videosListState: LazyListState,
    collectionsListState: LazyListState,
    listContentPadding: PaddingValues,
    viewportModifier: Modifier,
    onRetry: () -> Unit,
    onOpenVideo: (Int) -> Unit,
) {
    val videoRows = remember(videos, columns) { videos.data.orEmpty().chunked(columns) }
    ContentStatusViewport(
        modifier = Modifier.fillMaxSize().then(viewportModifier),
        contentPadding = listContentPadding,
    ) { statusModifier ->
        if (tab == ProfileTab.VIDEOS) {
            LazyColumn(
                state = videosListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = listContentPadding,
            ) {
                if (videos.error != null || videos.data.isNullOrEmpty()) {
                    item(key = "profile_videos_status") {
                        ContentStatus(
                            videos.copy(loading = videos.loading && videos.data == null),
                            onRetry,
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (videos.data.isNullOrEmpty()) statusModifier else Modifier,
                                ),
                            videos.data.isNullOrEmpty(),
                        )
                    }
                }
                itemsIndexed(
                    items = videoRows,
                    key = { _, row -> "profile_video_row_${row.first().id}" },
                ) { index, row ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = if (index == 0) 16.dp else VideoCardRowSpacing,
                                bottom = if (index == videoRows.lastIndex) 16.dp else 0.dp,
                            ),
                    ) {
                        VideoCardRow(
                            videos = row,
                            layout = layout,
                            onOpenVideo = onOpenVideo,
                            modifier = Modifier.fillMaxWidth(),
                            showUploader = false,
                            columns = columns,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = collectionsListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = listContentPadding,
            ) {
                item(key = "profile_collections_empty") {
                    ContentUnavailableView(
                        state = ContentUnavailableState.EMPTY,
                        title = stringResource(R.string.content_not_available_yet),
                        description = null,
                        icon = { FavoritesLogo(isActive = pagerState.settledPage == page) },
                        presentation = ContentUnavailablePresentation.INLINE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(statusModifier),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileTopBarIconButton(
    tabsPinned: Boolean,
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (tabsPinned) {
        IconButton(onClick = onClick, modifier = modifier, content = content)
    } else {
        BackdropIconButton(
            onClick = onClick,
            hazeState = hazeState,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
internal fun ProfileCover(state: ProfileUiState, height: Dp, offset: () -> Float) {
    val background = ThemeColorDefaults.pageBackgroundColor()
    val scrimHeight = with(LocalDensity.current) { (height - 48.dp).toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .height(height),
    ) {
        Image(
            painter = painterResource(R.drawable.profile_banner_placeholder),
            contentDescription = null,
            contentScale = rememberImageContentScale(state.bannerKey, restingContentScale = ContentScale.Crop),
            modifier = Modifier
                .matchParentSize()
                .imageSharedBounds(
                    state.bannerKey,
                    viewer = false,
                    circularSource = false,
                    sourceContentScale = ContentScale.Crop,
                ),
        )
        // Keep the gradient stationary as the photo scrolls beneath the system icons.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { translationY = offset() }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(background.copy(alpha = 0.8f), Color.Transparent),
                        endY = scrimHeight,
                    ),
                ),
        )
    }
}
