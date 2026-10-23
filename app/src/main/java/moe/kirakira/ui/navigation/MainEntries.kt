package moe.kirakira.ui.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import moe.kirakira.data.auth.SessionState
import moe.kirakira.feature.auth.AuthRoute
import moe.kirakira.feature.main.HomeViewModel
import moe.kirakira.feature.main.MainScreen
import moe.kirakira.feature.search.LocalTagSearchAnchor
import moe.kirakira.feature.search.SearchPage
import moe.kirakira.feature.search.SearchTopBar
import moe.kirakira.feature.search.SearchUiState
import moe.kirakira.feature.search.SearchViewModel
import moe.kirakira.feature.search.TagSearchAnchor

internal fun registerMainEntries(
    scope: EntryProviderScope<NavKey>,
    navigation: AppNavigationState,
    accountState: SessionState,
    homeViewModel: HomeViewModel?,
    searchViewModel: SearchViewModel?,
) {
    with(scope) {
        entry<TestRoute> {
            // Retained only until the restored legacy entry is removed above.
        }
        entry<MainRoute> {
            NavigationPage {
                val searchAnchor = remember(accountState.revision) { TagSearchAnchor() }
                CompositionLocalProvider(LocalTagSearchAnchor provides searchAnchor) {
                    MainScreen(
                        onBottomBarHeightChange = { navigation.bottomBarHeightPx = it },
                        profile = accountState.activeProfile,
                        onOpenProfile = { navigation.openFrom(MainRoute, if (accountState.activeProfile == null) AuthRoute() else SelfProfileRoute) },
                        onOpenHistory = { navigation.openFrom(MainRoute, HistoryRoute) },
                        onOpenSettings = { navigation.openFrom(MainRoute, SettingsRoute) },
                        onOpenVideo = { id ->
                            navigation.openFrom(MainRoute, VideoRoute(id))
                        },
                        videos = homeViewModel?.videos?.collectAsStateWithLifecycle()?.value
                                ?: moe.kirakira.ui.content.ContentState(), onRefreshVideos = { homeViewModel?.refresh() },
                        searchTopBar = {
                            val state = searchViewModel?.state?.collectAsStateWithLifecycle()?.value ?: SearchUiState()
                            SearchTopBar(state, { searchViewModel?.onEvent(it) })
                        },
                        searchContent = { padding, registerScrollToTop ->
                            SearchPage(
                                model = searchViewModel,
                                onOpenVideo = { id -> navigation.openFrom(MainRoute, VideoRoute(id)) },
                                contentPadding = padding,
                                onScrollToTopActionChange = registerScrollToTop,
                            )
                        },
                    )
                }
            }
        }
    }
}
