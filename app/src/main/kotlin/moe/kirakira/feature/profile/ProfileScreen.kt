package moe.kirakira.feature.profile

import android.os.Build
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.ui.adaptive.pageContentWidth
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
import moe.kirakira.ui.components.video.VideoCardLayout
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
