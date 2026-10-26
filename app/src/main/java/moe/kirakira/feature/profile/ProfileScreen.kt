package moe.kirakira.feature.profile

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.ui.components.BackdropIconButton
import moe.kirakira.ui.components.CollapsingHeaderBackground
import moe.kirakira.ui.components.CollapsingTabLayout
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.LocalNumberAnimationIdentity
import moe.kirakira.ui.components.PagerTabRow
import moe.kirakira.ui.components.appTopAppBarColors
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.components.rememberCollapsingTabState
import moe.kirakira.ui.components.video.VideoCardLayout
import moe.kirakira.ui.components.video.VideoCardRow
import moe.kirakira.ui.content.ContentState
import moe.kirakira.ui.theme.ThemeColorDefaults

@Composable
internal fun ProfileScreen(
    state: ProfileUiState,
    videos: ContentState<List<VideoSummary>>,
    pagerState: PagerState,
    videosListState: LazyListState,
    collectionsListState: LazyListState,
    bioExpanded: Boolean,
    snackbarHostState: SnackbarHostState,
    onTabChange: (ProfileTab) -> Unit,
    onBioExpandedChange: (Boolean) -> Unit,
    onFollowingChange: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onOpenFollowList: (FollowListKind) -> Unit,
    onUnavailableAction: (ProfileAction) -> Unit,
    onOpenAvatar: () -> Unit,
    onOpenVideo: (Int) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    videoCardLayout: VideoCardLayout = VideoCardLayout.GRID,
    profileError: ApiFailure? = null,
    statsError: ApiFailure? = null,
) {
    val videoRows = remember(videos, videoCardLayout) { videos.data.orEmpty().chunked(videoCardLayout.columns) }
    val layoutDirection = LocalLayoutDirection.current
    val background = ThemeColorDefaults.pageBackgroundColor()
    val coverHeight = 160.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerState = rememberCollapsingTabState()
    val tabsPinned = headerState.isCollapsed
    var tabRowHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val hazeState = rememberHazeState()
    val pinnedHazeState = rememberHazeState()
    Box(
        modifier
            .fillMaxSize()
            .background(background),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                Box {
                    TopAppBar(
                        title = {
                            if (tabsPinned) {
                                Text(
                                    text = state.profile.name.ifBlank { stringResource(R.string.content_unknown_author) },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = {
                            ProfileTopBarIconButton(
                                tabsPinned = tabsPinned,
                                hazeState = hazeState,
                                onClick = onBack,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_symbol_arrow_back),
                                    stringResource(R.string.navigate_back),
                                )
                            }
                        },
                        actions = {
                            if (!state.isSelf) {
                                ProfileTopBarIconButton(
                                    tabsPinned = tabsPinned,
                                    hazeState = hazeState,
                                    onClick = { onUnavailableAction(ProfileAction.MORE) },
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_symbol_more_horiz),
                                        stringResource(R.string.video_more),
                                    )
                                }
                            }
                        },
                        colors = appTopAppBarColors(),
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.hazeSource(hazeState) else Modifier,
                    )
                    .padding(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                    )
                    .consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                ContentPullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = onRetry,
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize(),
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val pagerHeight = (
                            maxHeight - innerPadding.calculateTopPadding() - with(density) { tabRowHeight.toDp() }
                            ).coerceAtLeast(0.dp)
                        CollapsingTabLayout(
                            state = headerState,
                            topPadding = innerPadding.calculateTopPadding(),
                            tabHeight = with(density) { tabRowHeight.toDp() },
                            modifier = Modifier.fillMaxSize(),
                            headerBackground = {
                                CollapsingHeaderBackground(
                                    state = headerState,
                                    hazeState = pinnedHazeState,
                                    modifier = Modifier.matchParentSize(),
                                ) {
                                    Box(
                                        Modifier.matchParentSize()
                                            .background(background)
                                            .then(
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.hazeSource(hazeState)
                                                else Modifier,
                                            ),
                                    ) {
                                        ProfileCover(coverHeight) { headerState.offset }
                                    }
                                }
                            },
                            header = {
                                Column {
                                    CompositionLocalProvider(LocalNumberAnimationIdentity provides state.profile.uid) {
                                        ProfileHeader(
                                            state = state,
                                            bioExpanded = bioExpanded,
                                            onBioExpandedChange = onBioExpandedChange,
                                            onFollowingChange = onFollowingChange,
                                            onEditProfile = onEditProfile,
                                            onOpenFollowList = onOpenFollowList,
                                            onOpenAvatar = onOpenAvatar,
                                            modifier = Modifier.fillMaxWidth(),
                                            coverRemainderHeight = (coverHeight - innerPadding.calculateTopPadding()).coerceAtLeast(
                                                0.dp,
                                            ),
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
                            },
                            tabs = {
                                PagerTabRow(
                                    pagerState = pagerState,
                                    titles = ProfileTab.entries.map { tab ->
                                        stringResource(
                                            if (tab == ProfileTab.VIDEOS) R.string.profile_videos
                                            else R.string.profile_collections,
                                        )
                                    },
                                    onTabChange = { onTabChange(ProfileTab.entries[it]) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { tabRowHeight = it.height },
                                    containerColor = Color.Transparent,
                                )
                            },
                        ) { topContentPadding ->
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            Modifier.hazeSource(pinnedHazeState)
                                        } else Modifier,
                                    ),
                                key = { ProfileTab.entries[it].name },
                            ) { page ->
                                val tab = ProfileTab.entries[page]
                                val statusHeight =
                                    (pagerHeight - innerPadding.calculateBottomPadding()).coerceAtLeast(0.dp)
                                if (tab == ProfileTab.VIDEOS) {
                                    LazyColumn(
                                        state = videosListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            top = topContentPadding,
                                            bottom = innerPadding.calculateBottomPadding(),
                                        ),
                                    ) {
                                        if (videos.error != null || videos.data.isNullOrEmpty()) {
                                            item(key = "profile_videos_status") {
                                                ContentStatus(
                                                    videos.copy(loading = videos.loading && videos.data == null),
                                                    onRetry,
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .then(
                                                            if (videos.data.isNullOrEmpty()) Modifier.heightIn(min = statusHeight) else Modifier,
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
                                                        top = if (index == 0) 16.dp else 12.dp,
                                                        bottom = if (index == videoRows.lastIndex) 16.dp else 0.dp,
                                                    ),
                                            ) {
                                                VideoCardRow(
                                                    videos = row,
                                                    layout = videoCardLayout,
                                                    onOpenVideo = onOpenVideo,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    showUploader = false,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        state = collectionsListState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            top = topContentPadding,
                                            bottom = innerPadding.calculateBottomPadding(),
                                        ),
                                    ) {
                                        item(key = "profile_collections_empty") {
                                            ContentUnavailableView(
                                                state = ContentUnavailableState.EMPTY,
                                                title = stringResource(R.string.content_not_available_yet),
                                                description = null,
                                                iconRes = R.drawable.ic_symbol_star,
                                                presentation = ContentUnavailablePresentation.INLINE,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = statusHeight),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTopBarIconButton(
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
private fun ProfileCover(height: Dp, offset: () -> Float) {
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
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
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
