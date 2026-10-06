package moe.kirakira.feature.main

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.data.content.VideoSummary
import moe.kirakira.feature.me.MeScreen
import moe.kirakira.feature.search.SearchScreen
import moe.kirakira.feature.search.SearchTopBar
import moe.kirakira.feature.search.SearchUiState
import moe.kirakira.feature.settings.VideoCardLayout
import moe.kirakira.feature.video.ContentState
import moe.kirakira.feature.video.VideoCardRow
import moe.kirakira.ui.components.AccountAvatar
import moe.kirakira.ui.components.ContentPullToRefresh
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.ShadingIcon
import moe.kirakira.ui.components.appTopAppBarColors
import moe.kirakira.ui.components.frostedBarBackground
import moe.kirakira.ui.navigation.rememberNavigationMotion
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.ThemeColorDefaults
import moe.kirakira.ui.theme.navigationBarShadow

private const val NavigationBarSelectedBackgroundAlpha = 0.08f

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
    onOpenHistory: () -> Unit = {},
    profile: AccountProfile? = null,
    videos: ContentState<List<VideoSummary>> = ContentState(),
    onRefreshVideos: () -> Unit = {},
    videosLayout: VideoCardLayout = VideoCardLayout.GRID,
    onBottomBarHeightChange: (Int) -> Unit = {},
    searchTopBar: @Composable () -> Unit = { SearchTopBar(SearchUiState(ready = true), {}) },
    searchContent: @Composable (PaddingValues) -> Unit = { padding ->
        SearchScreen(SearchUiState(ready = true), {}, onOpenVideo, contentPadding = padding)
    },
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    val meScrollState = rememberScrollState()
    val homeScrollState = rememberLazyListState()
    val layoutDirection = LocalLayoutDirection.current
    val motion = rememberNavigationMotion()
    val tabStateHolder = rememberSaveableStateHolder()

    FrostedScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ThemeColorDefaults.pageBackgroundColor(),
        // Each animated page owns its top and horizontal insets; the bottom bar owns the bottom inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MainBottomBar(
                destination = destination,
                onDestinationChange = { destination = it },
                modifier = Modifier.onSizeChanged { onBottomBarHeightChange(it.height) },
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
                FrostedScaffold(
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
                                BoxWithConstraints(Modifier.fillMaxSize()) {
                                    val statusHeight = (maxHeight - innerPadding.calculateTopPadding() - bottomBarPadding.calculateBottomPadding() - 32.dp)
                                        .coerceAtLeast(0.dp)
                                    LazyColumn(
                                        state = homeScrollState,
                                        modifier = Modifier.fillMaxSize().testTag("home_screen"),
                                        contentPadding = PaddingValues(
                                            top = innerPadding.calculateTopPadding() + 16.dp,
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

                            AppDestination.SEARCH -> searchContent(PaddingValues(
                                top = innerPadding.calculateTopPadding(),
                                bottom = bottomBarPadding.calculateBottomPadding(),
                            ))
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
                                onOpenHistory = onOpenHistory,
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

@Composable
private fun MainTopBar(
    destination: AppDestination,
    onOpenMe: () -> Unit,
    avatar: String?,
    searchTopBar: @Composable () -> Unit,
) {
    when (destination) {
        AppDestination.HOME -> HomeTopBar(onOpenMe = onOpenMe, avatar = avatar)

        AppDestination.FOLLOWING -> TopAppBar(
            title = { Text(stringResource(R.string.nav_following), fontWeight = FontWeight.SemiBold) },
            colors = appTopAppBarColors(),
        )

        AppDestination.ME -> TopAppBar(
            title = { Text(stringResource(R.string.nav_me), fontWeight = FontWeight.SemiBold) },
            colors = appTopAppBarColors(),
        )

        AppDestination.SEARCH -> searchTopBar()
    }
}

@Composable
private fun HomeTopBar(onOpenMe: () -> Unit, avatar: String?) {
    val meDescription = stringResource(R.string.nav_me)
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
            colors = appTopAppBarColors(),
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
private fun MainBottomBar(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().navigationBarShadow(shape).frostedBarBackground(shape),
            shape = shape,
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppDestination.entries.forEach { item ->
                    val selected = destination == item
                    val containerColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = NavigationBarSelectedBackgroundAlpha)
                        } else {
                            Color.Transparent
                        },
                        label = "Tab container color",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        label = "Tab content color",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(containerColor)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onDestinationChange(item) },
                            )
                            .heightIn(min = 64.dp)
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .testTag("nav_${item.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides contentColor) {
                            AnimatedTabIcon(icon = item.icon, selected = selected)
                            Text(
                                text = stringResource(item.label),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
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
