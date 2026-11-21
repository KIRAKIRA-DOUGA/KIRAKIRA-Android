package moe.kirakira.feature.main

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.feature.me.MeScreen
import moe.kirakira.feature.search.SearchScreen
import moe.kirakira.feature.search.SearchTopBar
import moe.kirakira.feature.search.SearchUiState
import moe.kirakira.ui.adaptive.browsePageMaxWidth
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentStatusViewport
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.FollowingLogo
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.LocalStatusBarScrollToTopEnabled
import moe.kirakira.ui.components.ShadingIcon
import moe.kirakira.ui.components.StatusBarScrollToTopEffect
import moe.kirakira.ui.components.appTopAppBarColors
import moe.kirakira.ui.components.dismissKeyboardOnBlankTap
import moe.kirakira.ui.components.rememberTabReselectHandler
import moe.kirakira.ui.components.smoothScrollToTop
import moe.kirakira.ui.components.topAppBarContentWidth
import moe.kirakira.ui.components.video.FeedVideoCard
import moe.kirakira.ui.components.video.VideoCardLayout
import moe.kirakira.ui.components.video.VideoCardRowSpacing
import moe.kirakira.ui.components.video.VideoFeedGrid
import moe.kirakira.ui.content.ContentState
import moe.kirakira.ui.navigation.LocalDestinationActive
import moe.kirakira.ui.navigation.blockNavigationFocus
import moe.kirakira.ui.navigation.desktopRailTabIndex
import moe.kirakira.ui.navigation.rememberNavigationMotion
import moe.kirakira.ui.theme.BrandTitleFontFamily
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorDefaults

@Composable
internal fun MainScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenVideo: (Int) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    profile: AccountProfile? = null,
    videos: ContentState<List<VideoSummary>> = ContentState(),
    onRefreshVideos: () -> Unit = {},
    videosLayout: VideoCardLayout = VideoCardLayout.GRID,
    tabs: MainTabState = rememberMainTabState(),
    showBottomBar: Boolean = true,
    showHomeWordmark: Boolean = true,
    showHomeAvatar: Boolean = true,
    onBottomBarHeightChange: (Int) -> Unit = {},
    searchTopBar: @Composable () -> Unit = { SearchTopBar(SearchUiState(ready = true), {}) },
    searchContent: @Composable (PaddingValues, ((suspend () -> Unit)?) -> Unit) -> Unit = { padding, registerScrollToTop ->
        SearchScreen(
            SearchUiState(ready = true), {}, onOpenVideo, contentPadding = padding,
            onScrollToTopActionChange = registerScrollToTop,
        )
    },
) {
    val selection = tabs.selection
    val destination = selection.destination
    val meScrollState = rememberScrollState()
    val homeScrollState = rememberLazyGridState()
    var searchScrollToTop by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val reselectTab = rememberTabReselectHandler { page ->
        when (AppDestination.entries[page]) {
            AppDestination.HOME -> homeScrollState.smoothScrollToTop()
            AppDestination.SEARCH -> searchScrollToTop?.invoke()
            AppDestination.ME -> meScrollState.smoothScrollToTop()
            AppDestination.FOLLOWING -> Unit
        }
    }
    DisposableEffect(tabs, reselectTab) {
        tabs.reselect = reselectTab
        onDispose { if (tabs.reselect === reselectTab) tabs.reselect = null }
    }
    val registerSearchScrollToTop = remember(tabs, reselectTab) {
        { action: (suspend () -> Unit)? ->
            if (action == null && tabs.destination == AppDestination.SEARCH) reselectTab(null)
            searchScrollToTop = action
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    val navigationMotion = rememberNavigationMotion()
    val tabStateHolder = rememberSaveableStateHolder()

    var bottomBarHeight by remember(showBottomBar) { mutableIntStateOf(0) }
    SideEffect { onBottomBarHeightChange(bottomBarHeight) }
    FrostedScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ThemeColorDefaults.pageBackgroundColor(),
        contentWindowInsets = if (showBottomBar) WindowInsets(0, 0, 0, 0)
            else WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        bottomBar = {
            if (showBottomBar) MainBottomBar(
                destination = destination,
                onDestinationChange = { tabs.select(it, reselectCurrent = true) },
                onDestinationReselect = { tabs.select(it, reselectCurrent = true) },
                modifier = Modifier.onSizeChanged { bottomBarHeight = it.height },
            )
        },
    ) { bottomBarPadding ->
        AnimatedContent(
            targetState = selection,
            modifier = Modifier.fillMaxSize().clipToBounds(),
            contentAlignment = Alignment.TopStart,
            transitionSpec = {
                fun index(tab: AppDestination) = if (showBottomBar) tab.ordinal else desktopRailTabIndex(tab)
                val targetIndex = index(targetState.destination)
                val forward = targetIndex > index(initialState.destination)
                val transition = when {
                    !targetState.animate -> EnterTransition.None togetherWith ExitTransition.None
                    !showBottomBar -> navigationMotion.vertical(enterFromBottom = forward, isPop = !forward)
                    forward -> navigationMotion.forward
                    else -> navigationMotion.backward
                }
                ContentTransform(
                    targetContentEnter = transition.targetContentEnter,
                    initialContentExit = transition.initialContentExit,
                    // Stable page ordering also keeps interrupted and reversed transitions layered correctly.
                    targetContentZIndex = targetIndex.toFloat(),
                    sizeTransform = null,
                )
            },
            contentKey = { it.destination },
            label = "Main tab transition",
        ) { tab ->
            val page = tab.destination
            val selected = page == destination
            tabStateHolder.SaveableStateProvider(page.name) {
                CompositionLocalProvider(
                    LocalDestinationActive provides (LocalDestinationActive.current && selected),
                    LocalStatusBarScrollToTopEnabled provides (
                        LocalStatusBarScrollToTopEnabled.current && selected &&
                            transition.currentState == EnterExitState.Visible &&
                            transition.targetState == EnterExitState.Visible && !transition.isRunning
                        ),
                ) {
                    when (page) {
                        AppDestination.HOME -> StatusBarScrollToTopEffect(homeScrollState)
                        AppDestination.ME -> StatusBarScrollToTopEffect(meScrollState)
                        AppDestination.SEARCH, AppDestination.FOLLOWING -> Unit
                    }
                    FrostedScaffold(
                        modifier = Modifier.fillMaxSize().blockNavigationFocus(!selected)
                            .dismissKeyboardOnBlankTap(enabled = selected && page == AppDestination.SEARCH),
                        containerColor = ThemeColorDefaults.pageBackgroundColor(),
                        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                        ),
                        topBar = {
                            MainTopBar(
                                avatar = profile?.avatar,
                                destination = page,
                                showHomeWordmark = showHomeWordmark,
                                onOpenMe = if (showHomeAvatar) ({ tabs.select(AppDestination.ME) }) else null,
                                searchTopBar = searchTopBar,
                            )
                        },
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = if (page == AppDestination.FOLLOWING) innerPadding.calculateTopPadding() else 0.dp,
                                    start = innerPadding.calculateStartPadding(layoutDirection),
                                    end = innerPadding.calculateEndPadding(layoutDirection),
                                    bottom = if (page != AppDestination.FOLLOWING) {
                                        0.dp
                                    } else {
                                        bottomBarPadding.calculateBottomPadding()
                                    },
                                )
                                .consumeWindowInsets(innerPadding)
                                .consumeWindowInsets(bottomBarPadding),
                        ) {
                            when (page) {
                                AppDestination.HOME -> ContentPullToRefresh(
                                    isRefreshing = videos.loading && videos.data != null,
                                    onRefresh = onRefreshVideos,
                                    indicatorTopPadding = innerPadding.calculateTopPadding(),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    val items = videos.data.orEmpty()
                                    val bottomContentPadding = if (items.isEmpty()) 16.dp else 28.dp
                                    val listContentPadding = PaddingValues(
                                        top = innerPadding.calculateTopPadding() + 16.dp,
                                        bottom = bottomBarPadding.calculateBottomPadding() + bottomContentPadding,
                                        start = 16.dp,
                                        end = 16.dp,
                                    )
                                    ContentStatusViewport(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = listContentPadding,
                                        leadingContentHeight = 0.dp,
                                    ) { statusModifier ->
                                        VideoFeedGrid(
                                            layout = videosLayout,
                                            state = homeScrollState,
                                            modifier = Modifier.testTag("home_screen"),
                                            contentPadding = listContentPadding,
                                        ) { columns ->
                                            item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                                                if (videos.error != null || videos.data.isNullOrEmpty()) {
                                                    ContentStatus(
                                                        videos.copy(loading = videos.loading && videos.data == null), onRefreshVideos,
                                                        Modifier.fillMaxWidth().then(
                                                            if (videos.data.isNullOrEmpty()) statusModifier else Modifier,
                                                        ),
                                                        videos.data.isNullOrEmpty(),
                                                    )
                                                }
                                            }
                                            itemsIndexed(items, key = { _, video -> video.id }) { index, video ->
                                                val rowSpacing = if (index / columns < items.lastIndex / columns) VideoCardRowSpacing else 0.dp
                                                FeedVideoCard(
                                                    video, videosLayout, onOpenVideo,
                                                    modifier = Modifier.fillMaxWidth().padding(bottom = rowSpacing),
                                                    showUploadTime = false,
                                                )
                                            }
                                        }
                                    }
                                }

                                AppDestination.SEARCH -> searchContent(
                                    PaddingValues(
                                        top = innerPadding.calculateTopPadding(),
                                        bottom = bottomBarPadding.calculateBottomPadding(),
                                    ),
                                    registerSearchScrollToTop,
                                )
                                AppDestination.FOLLOWING -> ContentUnavailableView(
                                    state = ContentUnavailableState.EMPTY,
                                    title = stringResource(R.string.content_not_available_yet),
                                    description = null,
                                    icon = { FollowingLogo() },
                                    modifier = Modifier.fillMaxSize().testTag("following_screen"),
                                )

                                AppDestination.ME -> MeScreen(
                                    profile = profile,
                                    onOpenSettings = onOpenSettings,
                                    onOpenProfile = onOpenProfile,
                                    onOpenHistory = onOpenHistory,
                                    onOpenFavorites = onOpenFavorites,
                                    scrollState = meScrollState,
                                    contentPadding = PaddingValues(
                                        top = innerPadding.calculateTopPadding(),
                                        bottom = bottomBarPadding.calculateBottomPadding(),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTopBar(
    destination: AppDestination,
    showHomeWordmark: Boolean,
    onOpenMe: (() -> Unit)?,
    avatar: String?,
    searchTopBar: @Composable () -> Unit,
) {
    when (destination) {
        AppDestination.HOME -> HomeTopBar(showHomeWordmark, onOpenMe, avatar)
        AppDestination.FOLLOWING, AppDestination.ME -> TopAppBar(
            title = { MainPageTitle(destination.label) },
            modifier = Modifier.topAppBarContentWidth(),
            colors = appTopAppBarColors(),
        )

        AppDestination.SEARCH -> searchTopBar()
    }
}

@Composable
private fun MainPageTitle(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        fontWeight = FontWeight.SemiBold,
        fontFamily = BrandTitleFontFamily,
    )
}

@Composable
private fun HomeTopBar(showWordmark: Boolean, onOpenMe: (() -> Unit)?, avatar: String?) {
    Box(
        modifier = Modifier
            .clipToBounds(),
    ) {
        ShadingIcon(
            icon = R.drawable.logo_kirakira,
            modifier = Modifier.matchParentSize(),
            endPadding = 72.dp,
        )
        TopAppBar(
            modifier = Modifier.topAppBarContentWidth(browsePageMaxWidth),
            colors = appTopAppBarColors(),
            title = {
                if (showWordmark) {
                    Icon(
                        painter = painterResource(R.drawable.logo_kirakira_wordmark),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.height(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    MainPageTitle(R.string.nav_home)
                }
            },
            actions = {
                if (onOpenMe != null) {
                    val meDescription = stringResource(R.string.nav_me)
                    IconButton(
                        onClick = onOpenMe,
                        modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = meDescription },
                    ) {
                        AccountAvatar(url = avatar, size = 40.dp)
                    }
                }
            },
        )
    }
}

@Preview(name = "Main · English", locale = "en", showBackground = true)
@Preview(name = "主屏 · 中文", locale = "zh", showBackground = true)
@Preview(name = "Main · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun MainScreenPreview() {
    KIRAKIRATheme(dynamicColor = false) {
        MainScreen(onOpenSettings = {})
    }
}
