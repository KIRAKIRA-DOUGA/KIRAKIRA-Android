package moe.kirakira.feature.main

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.feature.me.MeScreen
import moe.kirakira.feature.search.SearchScreen
import moe.kirakira.feature.settings.VideoCardLayout
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.VideoCardRow
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.navigation.rememberNavigationMotion
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.LocalClassicAccent
import moe.kirakira.ui.theme.ThemeColorDefaults
import moe.kirakira.ui.theme.navigationBarShadow
import moe.kirakira.ui.theme.topAppBarShadow

private enum class AppDestination(
    @param:StringRes val label: Int,
    val icon: MainTabIcon,
) {
    HOME(R.string.nav_home, MainTabIcon.HOME),
    SEARCH(R.string.nav_search, MainTabIcon.SEARCH),
    FOLLOWING(R.string.nav_following, MainTabIcon.FOLLOWING),
    ME(R.string.nav_me, MainTabIcon.ME),
}

@Composable
internal fun MainScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenVideo: (Int) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    profile: AccountProfile? = null,
    videos: ContentState<List<VideoSummary>> = ContentState(),
    onRefreshVideos: () -> Unit = {},
    videosLayout: VideoCardLayout = VideoCardLayout.GRID,
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    val meScrollState = rememberScrollState()
    val homeScrollState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current
    val motion = rememberNavigationMotion()
    val tabStateHolder = rememberSaveableStateHolder()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ThemeColorDefaults.pageBackgroundColor(),
        // Each animated page owns its top and horizontal insets; the bottom bar owns the bottom inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MainBottomBar(
                destination = destination,
                onDestinationChange = { destination = it },
            )
        },
    ) { bottomBarPadding ->
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.fillMaxSize().clipToBounds(),
            contentAlignment = Alignment.TopStart,
            transitionSpec = {
                val transition = if (targetState.ordinal > initialState.ordinal) motion.forward else motion.backward
                ContentTransform(
                    targetContentEnter = transition.targetContentEnter,
                    initialContentExit = transition.initialContentExit,
                    // Stable page ordering also keeps interrupted and reversed transitions layered correctly.
                    targetContentZIndex = targetState.ordinal.toFloat(),
                    sizeTransform = null,
                )
            },
            contentKey = { it },
            label = "Main tab transition",
        ) { page ->
            tabStateHolder.SaveableStateProvider(page.name) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ThemeColorDefaults.pageBackgroundColor(),
                    contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                    topBar = {
                        MainTopBar(
                            avatar = profile?.avatar,
                            destination = page,
                            onOpenMe = { destination = AppDestination.ME },
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                                bottom = if (page == AppDestination.HOME) {
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
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    val statusHeight = (maxHeight - bottomBarPadding.calculateBottomPadding() - 32.dp)
                                        .coerceAtLeast(0.dp)
                                    LazyColumn(
                                        state = homeScrollState,
                                        modifier = Modifier.fillMaxSize().testTag("home_screen"),
                                        contentPadding = PaddingValues(
                                            top = 16.dp,
                                            bottom = bottomBarPadding.calculateBottomPadding() + 16.dp,
                                            start = 16.dp,
                                            end = 16.dp,
                                        ),
                                    ) {
                                        item(key = "status") {
                                            if (videos.error != null || videos.data.isNullOrEmpty()) {
                                                ContentStatus(
                                                    videos.copy(loading = videos.loading && videos.data == null), onRefreshVideos,
                                                    Modifier.fillMaxWidth().then(
                                                        if (videos.data.isNullOrEmpty()) Modifier.heightIn(min = statusHeight) else Modifier,
                                                    ),
                                                    videos.data.isNullOrEmpty(),
                                                )
                                            }
                                        }
                                        items(videos.data.orEmpty().chunked(videosLayout.columns), key = { it.first().id }) { row ->
                                            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.TopCenter) {
                                                VideoCardRow(row, videosLayout, onOpenVideo,
                                                    Modifier.widthIn(max = 840.dp).fillMaxWidth())
                                            }
                                        }
                                    }
                                }
                            }

                            AppDestination.SEARCH -> SearchScreen()
                            AppDestination.FOLLOWING -> ContentUnavailableView(
                                state = ContentUnavailableState.EMPTY,
                                title = stringResource(R.string.content_not_available_yet),
                                description = null,
                                iconRes = R.drawable.ic_symbol_person_add,
                                modifier = Modifier.fillMaxSize().testTag("following_screen"),
                            )

                            AppDestination.ME -> MeScreen(
                                profile = profile,
                                onOpenSettings = onOpenSettings,
                                onOpenProfile = onOpenProfile,
                                scrollState = meScrollState,
                            )
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
    onOpenMe: () -> Unit,
    avatar: String?,
) {
    when (destination) {
        AppDestination.HOME -> HomeTopBar(onOpenMe = onOpenMe, avatar = avatar)

        AppDestination.FOLLOWING -> TopAppBar(
            modifier = Modifier.topAppBarShadow(),
            title = { Text(stringResource(R.string.nav_following)) },
            colors = mainTopAppBarColors(),
        )

        AppDestination.ME -> TopAppBar(
            modifier = Modifier.topAppBarShadow(),
            title = { Text(stringResource(R.string.nav_me)) },
            colors = mainTopAppBarColors(defaultContainer = MaterialTheme.colorScheme.surfaceContainer),
        )

        AppDestination.SEARCH -> Unit
    }
}

@Composable
private fun HomeTopBar(onOpenMe: () -> Unit, avatar: String?) {
    val meDescription = stringResource(R.string.nav_me)
    val colors = mainTopAppBarColors()
    Box(
        modifier = Modifier
            .topAppBarShadow()
            .clipToBounds()
            .background(colors.containerColor),
    ) {
        // Draw outside the title slot so the decoration can extend behind the status bar.
        Box(
            modifier = Modifier
                .matchParentSize()
                .windowInsetsPadding(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal))
                .padding(end = 72.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.logo_kirakira),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .wrapContentSize(align = Alignment.CenterEnd, unbounded = true)
                    .requiredSize(128.dp)
                    .alpha(0.2f),
                tint = MaterialTheme.colorScheme.primaryFixed,
            )
        }
        TopAppBar(
            colors = colors.copy(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            title = {
                Icon(
                    painter = painterResource(R.drawable.logo_kirakira_wordmark),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.height(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            actions = {
                IconButton(
                    onClick = onOpenMe,
                    modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = meDescription },
                ) {
                    AccountAvatar(url = avatar, size = 40.dp)
                }
            },
        )
    }
}

@Composable
private fun mainTopAppBarColors(defaultContainer: Color = MaterialTheme.colorScheme.surface): TopAppBarColors =
    if (LocalClassicAccent.current) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = ThemeColorDefaults.appBarContainerColor(),
            scrolledContainerColor = ThemeColorDefaults.appBarContainerColor(),
        )
    } else {
        TopAppBarDefaults.topAppBarColors(containerColor = defaultContainer)
    }

@Composable
private fun MainBottomBar(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.navigationBarShadow(),
        containerColor = ThemeColorDefaults.appBarContainerColor(),
    ) {
        AppDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = destination == item,
                colors = if (LocalClassicAccent.current) {
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    NavigationBarItemDefaults.colors()
                },
                onClick = { onDestinationChange(item) },
                icon = {
                    AnimatedTabIcon(icon = item.icon, selected = destination == item)
                },
                label = {
                    Text(
                        text = stringResource(item.label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.testTag("nav_${item.name.lowercase()}"),
            )
        }
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
