package moe.kirakira.feature.profile

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.ui.adaptive.LocalAppWindowLayout
import moe.kirakira.ui.adaptive.pageContentWidth
import moe.kirakira.ui.components.button.BackdropIconButton
import moe.kirakira.ui.components.illustration.FavoritesLogo
import moe.kirakira.ui.components.scaffold.CollapsingHeaderBackground
import moe.kirakira.ui.components.scaffold.CollapsingTabLayout
import moe.kirakira.ui.components.scaffold.LocalPageBackButtonVisible
import moe.kirakira.ui.components.scaffold.PagerTabRow
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopEffect
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopHeader
import moe.kirakira.ui.components.scaffold.appTopAppBarColors
import moe.kirakira.ui.components.scaffold.rememberCollapsingTabState
import moe.kirakira.ui.components.scaffold.topAppBarContentWidth
import moe.kirakira.ui.components.status.ContentPullToRefresh
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
    actions: @Composable (Boolean, HazeState) -> Unit,
    onOpenAvatar: () -> Unit,
    onOpenBanner: () -> Unit,
    onOpenVideo: (Int) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    videoCardLayout: VideoCardLayout = VideoCardLayout.GRID,
    profileError: ApiFailure? = null,
    statsError: ApiFailure? = null,
) {
    val layoutDirection = LocalLayoutDirection.current
    val background = ThemeColorDefaults.pageBackgroundColor()
    val coverHeight = 160.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerState = rememberCollapsingTabState()
    val tabsPinned = headerState.isCollapsed
    var tabRowHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val hazeState = rememberHazeState()
    val pinnedHazeState = rememberHazeState()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(background),
    ) {
        val pageWidth = profilePageAvailableWidth(maxWidth)
        val infoWidth = profileInfoPaneWidth(pageWidth, maxWidth)
        val currentTabListState =
            if (pagerState.currentPage == ProfileTab.VIDEOS.ordinal) videosListState else collectionsListState
        if (infoWidth == null) {
            StatusBarScrollToTopHeader(headerState, enabled = !pagerState.isScrollInProgress) {
                StatusBarScrollToTopEffect(
                    currentTabListState,
                    enabled = !pagerState.isScrollInProgress,
                )
            }
        } else {
            StatusBarScrollToTopEffect(
                currentTabListState,
                enabled = !pagerState.isScrollInProgress,
            )
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                Box {
                    // In two panes the back button stays over the banner; the actions sit over the plain list pane.
                    val wide = infoWidth != null
                    val backPinned = !wide && tabsPinned
                    TopAppBar(
                        title = {
                            if (backPinned) {
                                Text(
                                    text = state.profile.name.ifBlank { stringResource(R.string.content_unknown_author) },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = {
                            if (LocalPageBackButtonVisible.current) {
                                ProfileTopBarIconButton(
                                    tabsPinned = backPinned,
                                    hazeState = hazeState,
                                    onClick = onBack,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_icon_arrow_left),
                                        stringResource(R.string.navigate_back),
                                    )
                                }
                            }
                        },
                        actions = { actions(wide || tabsPinned, hazeState) },
                        colors = appTopAppBarColors(),
                        modifier = if (wide) {
                            Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal))
                        } else {
                            Modifier.topAppBarContentWidth()
                        },
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
                    // Two panes reach the window edges; each pane applies its own side inset.
                    .then(
                        if (infoWidth == null) {
                            Modifier.padding(
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                            )
                        } else Modifier,
                    )
                    .consumeWindowInsets(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (infoWidth == null) {
                    ContentPullToRefresh(
                        isRefreshing = isRefreshing,
                        onRefresh = onRetry,
                        modifier = Modifier
                            .pageContentWidth(ProfileTwoPaneMinWidth)
                            .fillMaxSize(),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
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
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.hazeSource(
                                                        hazeState,
                                                    )
                                                    else Modifier,
                                                ),
                                        ) {
                                            ProfileCover(state, coverHeight) { headerState.offset }
                                        }
                                    }
                                },
                                header = {
                                    Column {
                                        ProfileInfoContent(
                                            state = state,
                                            bioExpanded = bioExpanded,
                                            onBioExpandedChange = onBioExpandedChange,
                                            onFollowingChange = onFollowingChange,
                                            onEditProfile = onEditProfile,
                                            onOpenFollowList = onOpenFollowList,
                                            onOpenAvatar = onOpenAvatar,
                                            onOpenBanner = onOpenBanner,
                                            onRetry = onRetry,
                                            coverRemainderHeight = (
                                                coverHeight - innerPadding.calculateTopPadding()
                                                ).coerceAtLeast(0.dp),
                                            profileError = profileError,
                                            statsError = statsError,
                                        )
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
                                    ProfileTabPage(
                                        tab = tab,
                                        page = page,
                                        videos = videos,
                                        columns = videoCardLayout.columns,
                                        layout = videoCardLayout,
                                        pagerState = pagerState,
                                        videosListState = videosListState,
                                        collectionsListState = collectionsListState,
                                        listContentPadding = PaddingValues(
                                            top = topContentPadding,
                                            bottom = innerPadding.calculateBottomPadding(),
                                        ),
                                        viewportModifier = Modifier.padding(
                                            bottom = with(density) {
                                                (headerState.headerHeight - headerState.offset).toDp()
                                            },
                                        ),
                                        onRetry = onRetry,
                                        onOpenVideo = onOpenVideo,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val startInset = innerPadding.calculateStartPadding(layoutDirection)
                    val endInset = innerPadding.calculateEndPadding(layoutDirection)
                    val rowWidth = pageWidth - infoWidth - 32.dp
                    val columns = if (videoCardLayout == VideoCardLayout.LIST) {
                        videoCardLayout.columns
                    } else {
                        ((rowWidth + 16.dp) / (180.dp + 16.dp)).toInt().coerceIn(2, 6)
                    }
                    val infoScroll = rememberScrollState()
                    ContentPullToRefresh(
                        isRefreshing = isRefreshing,
                        onRefresh = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            Column(
                                Modifier
                                    .width(startInset + infoWidth)
                                    .fillMaxHeight()
                                    .verticalScroll(infoScroll),
                            ) {
                                Box(Modifier.fillMaxWidth()) {
                                    ProfileCover(state, coverHeight) { infoScroll.value.toFloat() }
                                    Column(
                                        Modifier.padding(start = startInset, top = innerPadding.calculateTopPadding()),
                                    ) {
                                        ProfileInfoContent(
                                            state = state,
                                            bioExpanded = bioExpanded,
                                            onBioExpandedChange = onBioExpandedChange,
                                            onFollowingChange = onFollowingChange,
                                            onEditProfile = onEditProfile,
                                            onOpenFollowList = onOpenFollowList,
                                            onOpenAvatar = onOpenAvatar,
                                            onOpenBanner = onOpenBanner,
                                            onRetry = onRetry,
                                            coverRemainderHeight = (
                                                coverHeight - innerPadding.calculateTopPadding()
                                                ).coerceAtLeast(0.dp),
                                            profileError = profileError,
                                            statsError = statsError,
                                        )
                                        Spacer(Modifier.height(innerPadding.calculateBottomPadding()))
                                    }
                                }
                            }
                            Column(Modifier.weight(1f).fillMaxHeight().padding(end = endInset)) {
                                Spacer(Modifier.height(innerPadding.calculateTopPadding()))
                                PagerTabRow(
                                    pagerState = pagerState,
                                    titles = ProfileTab.entries.map { tab ->
                                        stringResource(
                                            if (tab == ProfileTab.VIDEOS) R.string.profile_videos
                                            else R.string.profile_collections,
                                        )
                                    },
                                    onTabChange = { onTabChange(ProfileTab.entries[it]) },
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = Color.Transparent,
                                )
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .then(
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                Modifier.hazeSource(pinnedHazeState)
                                            } else Modifier,
                                        ),
                                    key = { ProfileTab.entries[it].name },
                                ) { page ->
                                    val tab = ProfileTab.entries[page]
                                    ProfileTabPage(
                                        tab = tab,
                                        page = page,
                                        videos = videos,
                                        columns = columns,
                                        layout = videoCardLayout,
                                        pagerState = pagerState,
                                        videosListState = videosListState,
                                        collectionsListState = collectionsListState,
                                        listContentPadding = PaddingValues(
                                            bottom = innerPadding.calculateBottomPadding(),
                                        ),
                                        viewportModifier = Modifier,
                                        onRetry = onRetry,
                                        onOpenVideo = onOpenVideo,
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

/** The single column widens up to this width; wider pages switch to two panes, so no side gaps remain. */
private val ProfileTwoPaneMinWidth = 720.dp

/** The content width after horizontal safe insets. */
@Composable
private fun profilePageAvailableWidth(maxWidth: Dp): Dp {
    val direction = LocalLayoutDirection.current
    val insets = ScaffoldDefaults.contentWindowInsets
    val horizontal = with(LocalDensity.current) {
        (insets.getLeft(this, direction) + insets.getRight(this, direction)).toDp()
    }
    return (maxWidth - horizontal).coerceAtLeast(0.dp)
}

/** Width of the start-side profile pane in the two-pane layout, or null for the narrow layout. */
@Composable
private fun profileInfoPaneWidth(available: Dp, maxWidth: Dp): Dp? {
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
private fun ProfileInfoContent(
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
private fun ProfileTabPage(
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
private fun ProfileCover(state: ProfileUiState, height: Dp, offset: () -> Float) {
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
