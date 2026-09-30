package moe.kirakira.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.core.image.deliveryImageUrl
import moe.kirakira.feature.imageviewer.ImageSource
import moe.kirakira.feature.imageviewer.ViewerImage
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.components.rememberTabChangeHandler

internal enum class ProfileTab { VIDEOS, COLLECTIONS }
internal enum class ProfileAction(@param:StringRes val messageRes: Int) {
    EDIT(R.string.profile_edit_unavailable), FOLLOWING_LIST(R.string.profile_following_unavailable),
    FOLLOWERS_LIST(R.string.profile_followers_unavailable), MORE(R.string.profile_more_unavailable),
}

@Composable
internal fun ProfilePage(
    model: ProfileViewModel,
    onBack: () -> Unit,
    onOpenVideo: (Int) -> Unit,
    onOpenImage: (ViewerImage) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by model.profile.collectAsStateWithLifecycle()
    val videos by model.videos.collectAsStateWithLifecycle()
    val stats by model.stats.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val error by model.actionError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(context.getString(it.messageRes())); model.dismissActionError() }
    }
    val value = profile.data
    if (value == null) {
        Scaffold(modifier, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            TopAppBar(title = { Text(stringResource(R.string.me_profile)) }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_symbol_arrow_back), stringResource(R.string.navigate_back)) }
            })
        }) { padding -> ContentStatus(profile, model::refresh, Modifier.fillMaxSize().padding(padding), presentation = ContentUnavailablePresentation.PAGE) }
        return
    }
    val state = ProfileUiState(value.copy(isSelf = value.uid == session.activeProfile?.uid),
        stats.data?.following, stats.data?.followers, busy)
    val pager = rememberPagerState(pageCount = { ProfileTab.entries.size })
    val onTabChange = rememberTabChangeHandler(pager)
    var expanded by rememberSaveable { mutableStateOf(false) }
    ProfileScreen(
        state = state, videos = videos, pagerState = pager,
        profileError = profile.error, statsError = stats.error,
        videosListState = rememberLazyListState(), collectionsListState = rememberLazyListState(),
        bioExpanded = expanded, snackbarHostState = snackbar,
        onTabChange = { onTabChange(it.ordinal) }, onBioExpandedChange = { expanded = it },
        onFollowingChange = { if (session.activeUuid == null) onLogin() else model.follow() },
        onUnavailableAction = { action -> scope.launch { snackbar.showSnackbar(context.getString(action.messageRes)) } },
        onOpenAvatar = {
            deliveryImageUrl(value.avatar)?.let { url ->
                onOpenImage(ViewerImage(ImageSource.RemoteUrl(url),
                    context.getString(R.string.profile_view_avatar, value.name), "avatar_${value.uid}", state.avatarKey))
            }
        },
        onOpenVideo = onOpenVideo, onBack = onBack, onRetry = model::refresh,
        isRefreshing = (profile.loading || videos.loading || stats.loading) &&
            (profile.data != null || videos.data != null || stats.data != null),
        modifier = modifier,
    )
}
