package moe.kirakira.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import moe.kirakira.AppDependenciesViewModel
import moe.kirakira.R
import moe.kirakira.data.auth.SessionState
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.feature.auth.AuthRoute
import moe.kirakira.feature.follow.FollowListPage
import moe.kirakira.feature.follow.FollowListViewModel
import moe.kirakira.feature.history.HistoryPage
import moe.kirakira.feature.history.HistoryViewModel
import moe.kirakira.feature.player.PlaybackViewModel
import moe.kirakira.feature.profile.ProfilePage
import moe.kirakira.feature.tag.TagPage
import moe.kirakira.feature.tag.TagViewModel
import moe.kirakira.feature.video.KaomojiViewModel
import moe.kirakira.feature.video.VideoPage

internal fun registerContentEntries(
    scope: EntryProviderScope<NavKey>,
    navigation: AppNavigationState,
    dependencies: AppDependenciesViewModel?,
    accountState: SessionState,
    playback: PlaybackViewModel,
    playbackSettings: PlaybackSettings?,
    danmakuSettings: DanmakuSettings?,
    kaomojiModel: KaomojiViewModel?,
    onDanmakuEnabled: (Boolean) -> Unit,
    onQualityPreference: (Int?) -> Unit,
) {
    val contentRepository = dependencies?.content
    val historyRepository = dependencies?.history
    with(scope) {
        entry<VideoRoute> { route ->
            NavigationPage {
                if (contentRepository == null || kaomojiModel == null || route.videoId <= 0) {
                    Text(stringResource(R.string.content_login_to_interact))
                } else {
                    VideoPage(
                        playback = playback,
                        onPlayerBounds = playback::updateBounds,
                        danmakuSettings = danmakuSettings,
                        onDanmakuEnabled = onDanmakuEnabled,
                        onQualityPreference = onQualityPreference,
                        kaomojiModel = kaomojiModel,
                        playbackSettings = playbackSettings,
                        videoId = route.videoId,
                        repository = contentRepository,
                        historyRepository = historyRepository,
                        isActive = navigation.backStack.lastOrNull() == route,
                        onOpenProfile = { uid -> navigation.openFrom(route, ProfileRoute(uid)) },
                        onOpenTag = { id -> navigation.openFrom(route, TagRoute(id)) },
                        onLogin = { navigation.openFrom(route, AuthRoute()) },
                        onBack = { navigation.popFrom(route) },
                    )
                }
            }
        }
        entry<HistoryRoute> {
            NavigationPage {
                if (historyRepository != null) HistoryPage(
                    model = viewModel { HistoryViewModel(historyRepository) },
                    isActive = navigation.backStack.lastOrNull() == HistoryRoute,
                    onBack = { navigation.popFrom(HistoryRoute) },
                    onLogin = { navigation.openFrom(HistoryRoute, AuthRoute()) },
                    onOpenVideo = { id -> navigation.openFrom(HistoryRoute, VideoRoute(id)) },
                )
            }
        }
        entry<TagRoute> { route ->
            NavigationPage {
                if (contentRepository != null && route.tagId > 0) {
                    TagPage(
                        model = viewModel { TagViewModel(route.tagId, contentRepository) },
                        onBack = { navigation.popFrom(route) },
                        onOpenVideo = { id -> navigation.openFrom(route, VideoRoute(id)) },
                    )
                }
            }
        }
        entry<ProfileRoute> { route ->
            NavigationPage {
                if (contentRepository == null || route.uid <= 0) Text(stringResource(R.string.content_login_to_interact))
                else ProfilePage(model = viewModel { moe.kirakira.feature.profile.ProfileViewModel(route.uid, contentRepository) },
                    onEditProfile = { navigation.openFrom(route, ProfileEditorRoute) },
                    onOpenFollowList = { kind -> navigation.openFrom(route, FollowListRoute(route.uid, kind)) },
                    onOpenVideo = { id -> navigation.openFrom(route, VideoRoute(id)) },
                    onOpenImage = { navigation.openFrom(route, ImageViewerRoute(it)) },
                    onLogin = { navigation.openFrom(route, AuthRoute()) },
                    onBack = { navigation.popFrom(route) })
            }
        }
        entry<SelfProfileRoute> {
            NavigationPage {
                val account = accountState.activeProfile
                if (account == null || contentRepository == null) LaunchedEffect(Unit) {
                    navigation.popFrom(SelfProfileRoute)
                } else ProfilePage(model = viewModel(key = "profile-${account.uid}") { moe.kirakira.feature.profile.ProfileViewModel(account.uid, contentRepository) },
                    onEditProfile = { navigation.openFrom(SelfProfileRoute, ProfileEditorRoute) },
                    onOpenFollowList = { kind -> navigation.openFrom(SelfProfileRoute, FollowListRoute(account.uid, kind)) },
                    onOpenVideo = { id -> navigation.openFrom(SelfProfileRoute, VideoRoute(id)) },
                    onOpenImage = { navigation.openFrom(SelfProfileRoute, ImageViewerRoute(it)) }, onLogin = {},
                    onBack = { navigation.popFrom(SelfProfileRoute) })
            }
        }
        entry<FollowListRoute> { route ->
            NavigationPage {
                if (contentRepository != null && route.uid > 0) {
                    FollowListPage(
                        model = viewModel { FollowListViewModel(route.uid, route.kind, contentRepository) },
                        isActive = navigation.backStack.lastOrNull() == route,
                        onBack = { navigation.popFrom(route) },
                        onOpenProfile = { uid -> navigation.openFrom(route, ProfileRoute(uid)) },
                    )
                }
            }
        }
    }
}
