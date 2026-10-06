package moe.kirakira.feature.profile

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.feature.settings.VideoCardLayout
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.VideoCardRow
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.PagerTabIndicator
import moe.kirakira.ui.components.ShadowFilledTonalIconButton
import moe.kirakira.ui.components.appTopAppBarColors
import moe.kirakira.ui.components.appTopAppBarTonalIconButtonColors
import moe.kirakira.ui.components.frostedBarBackground
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.theme.ThemeColorDefaults
import moe.kirakira.ui.theme.bottomEdgeShadow

private const val PROFILE_TABS_KEY = "profile_tabs"

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
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
    videoCardLayout: VideoCardLayout = VideoCardLayout.GRID,
    profileError: ApiFailure? = null,
    statsError: ApiFailure? = null,
) {
    val videoRows = remember(videos, videoCardLayout) { videos.data.orEmpty().chunked(videoCardLayout.columns) }
    val layoutDirection = LocalLayoutDirection.current
    val background = ThemeColorDefaults.pageBackgroundColor()
    val coverHeight = 160.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val profileListState = rememberLazyListState()
    val tabsPinned by remember(profileListState) {
        derivedStateOf {
            val layoutInfo = profileListState.layoutInfo
            val tabs = layoutInfo.visibleItemsInfo.firstOrNull { it.key == PROFILE_TABS_KEY }
            tabs != null && tabs.offset <= layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding
        }
    }
    var tabRowHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val headerScrollConnection = remember(profileListState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Collapse the profile before scrolling the selected page. Downward scroll
                // reaches the outer list naturally once the selected page is back at its top.
                if (available.y >= 0f) return Offset.Zero
                val consumed = profileListState.dispatchRawDelta(-available.y)
                return Offset(0f, -consumed)
            }
        }
    }

    val hazeState = rememberHazeState()
    Box(modifier
        .fillMaxSize()
        .background(background)) {
        Box(
            Modifier.matchParentSize().then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.hazeSource(hazeState) else Modifier,
            ),
        ) {
            ProfileCover(profileListState, coverHeight)
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                Box {
                    if (tabsPinned) {
                        Box(Modifier.matchParentSize().frostedBarBackground(hazeState = hazeState))
                    }
                    TopAppBar(
                        title = {
                            if (tabsPinned) {
                                Text(
                                    text = state.profile.name.ifBlank { stringResource(R.string.content_unknown_author) },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        navigationIcon = {
                            ProfileTopBarIconButton(
                                tabsPinned = tabsPinned,
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
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                    indicatorTopPadding = innerPadding.calculateTopPadding(),
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        // The pager fills the area below the toolbar and tabs, so the outer
                        // list stops with the tabs at the toolbar edge while drawing behind it.
                        val pagerHeight = (
                            maxHeight - innerPadding.calculateTopPadding() - with(density) { tabRowHeight.toDp() }
                        ).coerceAtLeast(0.dp)
                        LazyColumn(
                            state = profileListState,
                            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item(key = "profile_header") {
                                ProfileHeader(
                                    state = state,
                                    bioExpanded = bioExpanded,
                                    onBioExpandedChange = onBioExpandedChange,
                                    onFollowingChange = onFollowingChange,
                                    onEditProfile = onEditProfile,
                                    onOpenFollowList = onOpenFollowList,
                                    onOpenAvatar = onOpenAvatar,
                                    modifier = Modifier.fillMaxWidth(),
                                    coverRemainderHeight = (coverHeight - innerPadding.calculateTopPadding()).coerceAtLeast(0.dp),
                                )
                            }
                            if (profileError != null) {
                                item(key = "profile_error") {
                                    ContentUnavailableView(
                                        state = ContentUnavailableState.ERROR,
                                        title = stringResource(R.string.profile_load_failed),
                                        description = stringResource(profileError.messageRes()),
                                        onRetry = onRetry,
                                        presentation = ContentUnavailablePresentation.INLINE,
                                    )
                                }
                            }
                            if (statsError != null) {
                                item(key = "profile_stats_error") {
                                    ContentUnavailableView(
                                        state = ContentUnavailableState.ERROR,
                                        title = stringResource(R.string.profile_stats_load_failed),
                                        description = stringResource(statsError.messageRes()),
                                        onRetry = onRetry,
                                        presentation = ContentUnavailablePresentation.INLINE,
                                    )
                                }
                            }
                            stickyHeader(key = PROFILE_TABS_KEY) {
                                PrimaryTabRow(
                                    selectedTabIndex = pagerState.currentPage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(1f)
                                        .bottomEdgeShadow()
                                        .onSizeChanged { tabRowHeight = it.height },
                                    containerColor = background,
                                    indicator = { PagerTabIndicator(pagerState) },
                                    divider = {},
                                ) {
                                    ProfileTab.entries.forEachIndexed { index, tab ->
                                        Tab(
                                            selected = pagerState.currentPage == index,
                                            onClick = { onTabChange(tab) },
                                            selectedContentColor = MaterialTheme.colorScheme.primary,
                                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            text = {
                                                Text(
                                                    stringResource(
                                                        if (tab == ProfileTab.VIDEOS) R.string.profile_videos
                                                        else R.string.profile_collections,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                            item(key = "profile_pages") {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(pagerHeight)
                                        .background(background)
                                        .nestedScroll(headerScrollConnection),
                                    key = { ProfileTab.entries[it].name },
                                ) { page ->
                                    val tab = ProfileTab.entries[page]
                                    val statusHeight = (pagerHeight - innerPadding.calculateBottomPadding()).coerceAtLeast(0.dp)
                                    if (tab == ProfileTab.VIDEOS) {
                                        LazyColumn(
                                            state = videosListState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                                        ) {
                                            if (videos.error != null || videos.data.isNullOrEmpty()) {
                                                item(key = "profile_videos_status") {
                                                    ContentStatus(
                                                        videos.copy(loading = videos.loading && videos.data == null), onRetry,
                                                        Modifier.fillMaxWidth().then(
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
                                            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                                        ) {
                                            item(key = "profile_collections_empty") {
                                                ContentUnavailableView(
                                                    state = ContentUnavailableState.EMPTY,
                                                    title = stringResource(R.string.content_not_available_yet),
                                                    description = null,
                                                    iconRes = R.drawable.ic_symbol_star,
                                                    presentation = ContentUnavailablePresentation.INLINE,
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = statusHeight),
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
}

@Composable
private fun ProfileTopBarIconButton(
    tabsPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (tabsPinned) {
        IconButton(onClick = onClick, modifier = modifier, content = content)
    } else {
        ShadowFilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            colors = appTopAppBarTonalIconButtonColors(),
            content = content,
        )
    }
}

@Composable
private fun ProfileCover(listState: LazyListState, height: Dp) {
    val background = ThemeColorDefaults.pageBackgroundColor()
    val scrimHeight = with(LocalDensity.current) { (height - 48.dp).toPx() }
    Box(Modifier
        .fillMaxWidth()
        .height(height)) {
        Image(
            painter = painterResource(R.drawable.profile_banner_placeholder),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // Draw beyond the list viewport so the cover reaches behind the status bar and transparent toolbar.
                    translationY = -listState.firstVisibleItemScrollOffset.toFloat()
                    alpha = if (listState.firstVisibleItemIndex == 0) 1f else 0f
                },
        )
        // Keep the gradient stationary as the photo scrolls beneath the system icons.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(background.copy(alpha = 0.8f), Color.Transparent),
                        endY = scrimHeight,
                    ),
                ),
        )
    }
}
