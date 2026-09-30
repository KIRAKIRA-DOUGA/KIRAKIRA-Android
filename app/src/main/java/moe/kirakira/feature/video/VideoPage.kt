package moe.kirakira.feature.video

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import moe.kirakira.MainActivity
import moe.kirakira.R
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.feature.player.PlaybackViewModel
import moe.kirakira.feature.player.PlayerUiState
import moe.kirakira.feature.player.VideoPlayer
import moe.kirakira.feature.settings.PlaybackSettings
import moe.kirakira.ui.components.messageRes

private data object FullscreenInfo : NavigationEventInfo()

@Composable
internal fun VideoPage(
    playbackSettings: PlaybackSettings?,
    videoId: Int,
    repository: ContentRepository,
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val model = viewModel { VideoViewModel(videoId, repository) }
    val kaomojiModel: KaomojiViewModel = viewModel()
    val recentKaomoji by kaomojiModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playback = viewModel { PlaybackViewModel(context.applicationContext, createSavedStateHandle()) }
    val detail by model.detail.collectAsStateWithLifecycle()
    val comments by model.comments.collectAsStateWithLifecycle()
    val danmaku by model.danmaku.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val session by model.session.collectAsStateWithLifecycle()
    val commentDraft by model.commentDraft.collectAsStateWithLifecycle()
    val danmakuStyle by model.danmakuStyle.collectAsStateWithLifecycle()
    val danmakuDraft by model.danmakuDraft.collectAsStateWithLifecycle()
    val commentComposer = rememberComposerState(videoId, session.revision, commentDraft)
    val danmakuComposer = rememberComposerState(videoId, session.revision, danmakuDraft)
    val posted by model.posted.collectAsStateWithLifecycle()
    val error by model.actionError.collectAsStateWithLifecycle()
    val activity = remember(context) { context.mainActivity() }
    val pip = activity?.pictureInPicture == true
    val layoutDirection = LocalLayoutDirection.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var fullscreen by remember { mutableStateOf(false) }
    var playerBounds by remember { mutableStateOf<Rect?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentActive by rememberUpdatedState(isActive)
    val value = detail.data
    val signedIn = session.activeUuid != null
    fun requireLogin(action: () -> Unit) { if (signedIn) action() else { playback.pause(); onLogin() } }
    fun unavailable() { scope.launch { snackbar.showSnackbar(context.getString(R.string.video_action_unavailable)) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var initialSessionRevision by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(value, playbackSettings, isActive, lifecycleState, session.revision) {
        val previousRevision = initialSessionRevision
        initialSessionRevision = session.revision
        if (previousRevision != null && previousRevision != session.revision) {
            playback.cancelAutoplay()
            playback.release()
        }
        if (value != null) {
            playback.setContent(value.summary.title, value.parts)
            if (isActive && lifecycleState == Lifecycle.State.RESUMED && playbackSettings != null) {
                playback.maybeAutoplay(playbackSettings.autoplay)
            }
        } else playback.release()
    }
    var previouslyActive by remember { mutableStateOf(isActive) }
    LaunchedEffect(isActive) {
        if (!isActive) { playback.cancelAutoplay(); playback.release(); fullscreen = false }
        else if (!previouslyActive) model.refresh()
        previouslyActive = isActive
    }
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(context.getString(if (it == moe.kirakira.core.network.ApiFailure.NETWORK ||
                it == moe.kirakira.core.network.ApiFailure.TIMEOUT) R.string.content_mutation_uncertain else it.messageRes())); model.dismissActionError() }
    }
    DisposableEffect(activity, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { playback.cancelAutoplay(); playback.release() }
            if (event == Lifecycle.Event.ON_PAUSE && !currentActive) playback.release()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            playback.release()
            activity?.updatePictureInPicture(false, null)
            activity?.setVideoFullscreen(false)
        }
    }
    LaunchedEffect(pip) {
        if (!pip && activity != null && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) playback.release()
    }
    LaunchedEffect(fullscreen, pip) {
        if (fullscreen || pip) {
            commentComposer.panelOpen = false
            danmakuComposer.panelOpen = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
        activity?.setVideoFullscreen(fullscreen && !pip)
    }
    val view = LocalView.current
    DisposableEffect(playback.playing, view) {
        view.keepScreenOn = playback.playing
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(playback.playing, playback.buffering, isActive, playerBounds, playbackSettings) {
        val size = playback.player?.videoSize
        val ratio = if (size != null && size.width > 0 && size.height > 0) size.width.toFloat() / size.height else 16f / 9f
        activity?.updatePictureInPicture(isActive && playback.playing, playerBounds, ratio,
            autoEnter = playbackSettings?.autoPictureInPicture == true)
    }
    NavigationBackHandler(rememberNavigationEventState(FullscreenInfo), isBackEnabled = fullscreen && !pip,
        onBackCompleted = { fullscreen = false })
    Box(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { if (!pip) SnackbarHost(snackbar) },
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(
                top = if (fullscreen || pip) 0.dp else padding.calculateTopPadding(),
                start = if (fullscreen || pip) 0.dp else padding.calculateStartPadding(layoutDirection),
                end = if (fullscreen || pip) 0.dp else padding.calculateEndPadding(layoutDirection),
            )) {
                val playerHeight = (maxWidth * 9f / 16f).coerceAtMost(maxHeight * 0.42f)
                Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier = (if (fullscreen || pip) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                            .height(playerHeight))
                            .onGloballyPositioned {
                                playerBounds = it.boundsInWindow().let { b ->
                                    Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                                }
                            },
                    ) {
                        VideoPlayer(
                            player = playback.player,
                            state = PlayerUiState(
                                playing = playback.playing,
                                buffering = playback.buffering,
                                failed = playback.failed,
                                positionMs = playback.positionMs,
                                durationMs = playback.durationMs,
                                bufferedPositionMs = playback.bufferedPositionMs,
                                available = value?.parts?.getOrNull(playback.selectedPart)?.url != null && isActive,
                            ),
                            image = value?.summary?.image,
                            fullscreen = fullscreen,
                            pictureInPicture = pip,
                            onToggle = playback::toggle,
                            onRetry = playback::play,
                            onSeek = playback::seek,
                            onBack = {
                                if (fullscreen) fullscreen = false
                                else { playback.release(); onBack() }
                            },
                            onFullscreen = { fullscreen = !fullscreen },
                            onPictureInPicture = {
                                if (activity?.enterVideoPictureInPicture() != true) scope.launch {
                                    snackbar.showSnackbar(context.getString(R.string.player_pip_unavailable))
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (!fullscreen && !pip) {
                        VideoScreen(
                            VideoUiState(detail, comments, danmaku, busy, signedIn, session.revision, commentDraft, danmakuDraft, posted, danmakuStyle),
                            model::refresh, { requireLogin(model::follow) }, { requireLogin { model.vote(it) } },
                            { comment, reaction -> requireLogin { model.voteComment(comment, reaction) } },
                            model::comments, model::refreshComments, model::retryComments,
                            model::loadAdjacentComments, model::consumeCommentLocation, model::ensureTab,
                            { model.commentDraft.value = it }, { model.danmakuDraft.value = it },
                            { requireLogin(model::sendComment) }, { requireLogin { model.sendDanmaku(playback.positionMs) } },
                            model::refreshDanmaku, { uid -> playback.release(); onOpenProfile(uid) },
                            ::unavailable, { requireLogin {} }, padding.calculateBottomPadding(),
                            onDanmakuStyle = model::updateDanmakuStyle,
                            commentComposer = commentComposer, danmakuComposer = danmakuComposer,
                            recentKaomoji = recentKaomoji, onKaomojiInserted = kaomojiModel::record,
                            isActive = isActive,
                            selectedPart = playback.selectedPart,
                            onSelectPart = playback::selectPart,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
        if (!pip && !fullscreen) {
            // The edge-to-edge status bar is transparent; paint its backdrop above the page.
            Spacer(
                Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black),
            )
        }
    }
}

private fun Context.mainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.mainActivity()
    else -> null
}
