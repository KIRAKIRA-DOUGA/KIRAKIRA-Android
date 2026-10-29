package moe.kirakira.feature.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.core.image.deliveryImageUrl
import moe.kirakira.core.share.profileShareUrl
import moe.kirakira.core.share.shareContent
import moe.kirakira.data.auth.isReadyForContent
import moe.kirakira.data.content.FollowListKind
import moe.kirakira.data.image.ImageSource
import moe.kirakira.data.settings.RuleCategory
import moe.kirakira.feature.imageviewer.ViewerImage
import moe.kirakira.ui.components.ContentStatus
import moe.kirakira.ui.components.ContentUnavailablePresentation
import moe.kirakira.ui.components.ContentUnavailableState
import moe.kirakira.ui.components.ContentUnavailableView
import moe.kirakira.ui.components.FrostedScaffold
import moe.kirakira.ui.components.appTopAppBarColors
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.components.rememberTabChangeHandler
import moe.kirakira.ui.content.ContentState
import okio.ByteString.Companion.encodeUtf8

internal enum class ProfileTab { VIDEOS, COLLECTIONS }

@Composable
internal fun ProfilePage(
    model: ProfileViewModel,
    onBack: () -> Unit,
    onOpenVideo: (Int) -> Unit,
    onOpenImage: (ViewerImage) -> Unit,
    onLogin: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenFollowList: (FollowListKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadedDetails by model.details.collectAsStateWithLifecycle()
    val pendingMessage by model.message.collectAsStateWithLifecycle()
    val loadedVideos by model.videos.collectAsStateWithLifecycle()
    val loadedStats by model.stats.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val pendingError by model.actionError.collectAsStateWithLifecycle()
    val currentAccount = session.isReadyForContent && model.isCurrentAccount
    val details = if (currentAccount) loadedDetails else ContentState(loading = true)
    val profile = ContentState(details.data?.profile, loading = details.loading, error = details.error)
    val videos = if (currentAccount) loadedVideos else ContentState(loading = true)
    val stats = if (currentAccount) loadedStats else ContentState(loading = true)
    val message = pendingMessage.takeIf { currentAccount }
    val error = pendingError.takeIf { currentAccount }
    val snackbar = remember(session.revision) { SnackbarHostState() }
    val resources by rememberUpdatedState(LocalResources.current)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val share: () -> Unit = {
        val name = profile.data?.name.takeIf {
            model.isCurrentAccount && model.session.value.revision == session.revision
        }
        if (!shareContent(context, profileShareUrl(model.uid), name)) {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.share_unavailable)) }
        }
    }
    val density = LocalDensity.current
    val sourceInstanceId = rememberSaveable { UUID.randomUUID().toString() }
    val blocked = details.data?.isBlocked == true
    val menuState = ProfileMenuState(
        accountRevision = session.revision,
        blocked = blocked,
        hidden = details.data?.isHidden == true,
        enabled = !busy && !details.loading && details.error == null && details.data != null,
        canRetry = !busy && !details.loading && details.error != null,
    )
    val changeRule: (RuleCategory) -> Unit = { category ->
        if (model.session.value.activeUuid == null) onLogin() else model.changeRule(category)
    }
    LaunchedEffect(message, session.revision) {
        message?.let {
            snackbar.showSnackbar(resources.getString(it))
            model.dismissMessage()
        }
    }
    LaunchedEffect(error, session.revision) {
        error?.let { snackbar.showSnackbar(resources.getString(it.messageRes())); model.dismissActionError() }
    }
    val value = profile.data
    if (value == null || blocked) {
        FrostedScaffold(
            modifier, snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        if (!profile.loading) {
                            Text(stringResource(R.string.me_profile), fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        ProfileActionsMenu(
                            menuState, changeRule, model::refresh,
                            onShare = share,
                            showRelationshipActions = model.uid != session.activeProfile?.uid,
                        ) { open ->
                            IconButton(onClick = open) {
                                Icon(painterResource(R.drawable.ic_symbol_more_horiz), stringResource(R.string.video_more))
                            }
                        }
                    },
                    colors = appTopAppBarColors(),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.ic_symbol_arrow_back),
                                stringResource(R.string.navigate_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            if (blocked) {
                ContentUnavailableView(
                    state = ContentUnavailableState.EMPTY,
                    title = stringResource(R.string.profile_blocked),
                    description = details.error?.let { stringResource(it.messageRes()) },
                    iconRes = R.drawable.ic_symbol_block,
                    onRetry = model::refresh,
                    retryEnabled = !busy && !details.loading,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                ContentStatus(
                    profile,
                    model::refresh,
                    Modifier.fillMaxSize().padding(padding),
                    presentation = ContentUnavailablePresentation.PAGE,
                )
            }
        }
        return
    }
    val avatarIdentity = remember(value.uid, value.avatar) {
        "${value.uid}/${value.avatar.orEmpty()}".encodeUtf8().sha256().hex()
    }
    val state = ProfileUiState(
        value.copy(isSelf = value.uid == session.activeProfile?.uid),
        stats.data?.following, stats.data?.followers, busy || details.loading || details.error != null,
        avatarKey = "profile/$sourceInstanceId/$avatarIdentity",
    )
    val pager = rememberPagerState(pageCount = { ProfileTab.entries.size })
    val videosListState = rememberLazyListState()
    val collectionsListState = rememberLazyListState()
    val onTabChange = rememberTabChangeHandler(pager, onTabReselect = { page ->
        val listState = if (ProfileTab.entries[page] == ProfileTab.VIDEOS) videosListState else collectionsListState
        listState.animateScrollToItem(0)
    })
    var expanded by rememberSaveable(session.revision) { mutableStateOf(false) }
    ProfileScreen(
        state = state, videos = videos, pagerState = pager,
        profileError = profile.error, statsError = stats.error,
        videosListState = videosListState, collectionsListState = collectionsListState,
        bioExpanded = expanded, snackbarHostState = snackbar,
        onTabChange = { onTabChange(it.ordinal) }, onBioExpandedChange = { expanded = it },
        onFollowingChange = { if (session.activeUuid == null) onLogin() else model.follow() },
        onOpenFollowList = onOpenFollowList,
        onEditProfile = {
            if (model.uid == model.session.value.activeProfile?.uid) onEditProfile()
        },
        actions = { tabsPinned, hazeState ->
            ProfileActionsMenu(
                menuState, changeRule, model::refresh,
                onShare = share,
                showRelationshipActions = !state.isSelf,
            ) { open ->
                ProfileTopBarIconButton(tabsPinned = tabsPinned, hazeState = hazeState, onClick = open) {
                    Icon(painterResource(R.drawable.ic_symbol_more_horiz), stringResource(R.string.video_more))
                }
            }
        },
        onOpenAvatar = {
            deliveryImageUrl(value.avatar)?.let { url ->
                onOpenImage(
                    ViewerImage(
                        source = ImageSource.RemoteUrl(url),
                        description = resources.getString(R.string.profile_view_avatar, value.name),
                        fileName = "avatar_${value.uid}",
                        sharedKey = state.avatarKey,
                        thumbnail = deliveryImageUrl(value.avatar, with(density) { profileAvatarSize.roundToPx() })
                            ?.let(ImageSource::RemoteUrl),
                    ),
                )
            }
        },
        onOpenVideo = onOpenVideo, onBack = onBack, onRetry = model::refresh,
        isRefreshing = (profile.loading || videos.loading || stats.loading) &&
            currentAccount,
        modifier = modifier,
    )
}
