package moe.kirakira.feature.video

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import moe.kirakira.MainActivity
import moe.kirakira.R
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.history.HistoryRepository
import moe.kirakira.feature.player.PlaybackViewModel
import moe.kirakira.feature.player.PlayerUiState
import moe.kirakira.feature.player.VideoPlayer
import moe.kirakira.feature.settings.DanmakuSettings
import moe.kirakira.feature.settings.PlaybackSettings
import moe.kirakira.ui.components.messageRes

private data object FullscreenInfo : NavigationEventInfo()

@Composable
internal fun VideoPage(
    playbackSettings: PlaybackSettings?,
    playback: PlaybackViewModel,
    videoId: Int,
    repository: ContentRepository,
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onLogin: () -> Unit,
    onOpenTag: (Long) -> Unit,
    modifier: Modifier = Modifier,
    historyRepository: HistoryRepository? = null,
    isActive: Boolean = true,
    onQualityPreference: (Int?) -> Unit = {},
    danmakuSettings: DanmakuSettings? = null,
    onDanmakuEnabled: (Boolean) -> Unit = {},
    onPlayerBounds: (Rect) -> Unit = {},
) {
    val model = viewModel { VideoViewModel(videoId, repository, historyRepository) }
    val kaomojiModel: KaomojiViewModel = viewModel()
    val recentKaomoji by kaomojiModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val detail by model.detail.collectAsStateWithLifecycle()
    val resume by model.resume.collectAsStateWithLifecycle()
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val value = detail.data
    val signedIn = session.activeUuid != null
    fun requireLogin(action: () -> Unit) { if (signedIn) action() else { playback.pause(); onLogin() } }
    fun unavailable() { scope.launch { snackbar.showSnackbar(context.getString(R.string.video_action_unavailable)) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(value, resume, playbackSettings, isActive, lifecycleState, session.revision) {
        if (!isActive || playback.videoId != videoId) return@LaunchedEffect
        if (value != null) {
            playback.setContent(value.summary.title, value.parts,
                resume.positionMs, resume.ready)
            if (resume.ready && lifecycleState == Lifecycle.State.RESUMED && playbackSettings != null) {
                playback.maybeAutoplay(playbackSettings.autoplay)
            }
        }
    }
    var previouslyActive by remember { mutableStateOf(isActive) }
    LaunchedEffect(isActive) {
        if (!isActive) { fullscreen = false }
        else if (!previouslyActive) model.refresh()
        previouslyActive = isActive
    }
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(context.getString(if (it == moe.kirakira.core.network.ApiFailure.NETWORK ||
                it == moe.kirakira.core.network.ApiFailure.TIMEOUT) R.string.content_mutation_uncertain else it.messageRes())); model.dismissActionError() }
    }
    LaunchedEffect(fullscreen, pip) {
        if (fullscreen || pip) {
            commentComposer.panelOpen = false
            danmakuComposer.panelOpen = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
        if (isActive) activity?.setVideoFullscreen(fullscreen && !pip)
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
                val playerContent: @Composable () -> Unit = {
                    Box(
                        modifier = (if (fullscreen || pip) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                            .height(playerHeight))
                            .onGloballyPositioned {
                                val bounds = it.boundsInWindow().let { b ->
                                    Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                                }
                                if (isActive) onPlayerBounds(bounds)
                            },
                    ) {
                        VideoPlayer(
                            danmaku = danmaku.data.orEmpty(),
                            danmakuSettings = danmakuSettings,
                            danmakuContentKey = Triple(videoId, playback.selectedPart, session.revision),
                            onDanmakuEnabled = onDanmakuEnabled,
                            active = isActive,
                            onQuality = { height ->
                                playback.setQualityPreference(height == null, height)
                                onQualityPreference(height)
                            },
                            onSpeed = playback::changeSpeed,
                            onContinuousSpeed = playback::changeContinuousSpeed,
                            onPreservesPitch = playback::changePreservesPitch,
                            player = playback.player.takeIf { isActive && playback.videoId == videoId && !playback.miniPlayer },
                            state = PlayerUiState(
                                qualityOptions = playback.qualityOptions,
                                selectedQualityHeight = playback.selectedQualityHeight,
                                actualVideoHeight = playback.actualVideoHeight,
                                speed = playback.speed,
                                continuousSpeed = playback.continuousSpeed,
                                preservesPitch = playback.preservesPitch,
                                playing = playback.playing,
                                showPauseIcon = playback.showPauseIcon,
                                buffering = playback.buffering,
                                initialLoading = playback.initialLoading,
                                artworkPending = detail.loading && value == null,
                                failed = playback.failed,
                                positionMs = playback.positionMs,
                                durationMs = playback.durationMs,
                                bufferedPositionMs = playback.bufferedPositionMs,
                                available = isActive && playback.videoId == videoId &&
                                    (playback.player != null || value?.parts?.getOrNull(playback.selectedPart)?.url != null),
                            ),
                            image = value?.summary?.image,
                            fullscreen = fullscreen,
                            pictureInPicture = pip,
                            onToggle = playback::toggle,
                            onRetry = playback::play,
                            onSeek = playback::seek,
                            onBack = {
                                if (fullscreen) fullscreen = false
                                else onBack()
                            },
                            onFullscreen = { fullscreen = !fullscreen },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (fullscreen || pip) {
                    playerContent()
                } else {
                    VideoScreen(
                        VideoUiState(detail, comments, danmaku, busy, signedIn, session.revision, commentDraft, danmakuDraft, posted, danmakuStyle),
                        model::refresh, { requireLogin(model::follow) }, { requireLogin { model.vote(it) } },
                        { comment, reaction -> requireLogin { model.voteComment(comment, reaction) } },
                        model::comments, model::refreshComments, model::retryComments,
                        model::loadAdjacentComments, model::consumeCommentLocation, model::ensureTab,
                        { model.commentDraft.value = it }, { model.danmakuDraft.value = it },
                        { requireLogin(model::sendComment) }, { requireLogin { model.sendDanmaku(playback.positionMs) } },
                        model::refreshDanmaku, onOpenProfile,
                        ::unavailable, { requireLogin {} }, padding.calculateBottomPadding(),
                        onDanmakuStyle = model::updateDanmakuStyle,
                        commentComposer = commentComposer, danmakuComposer = danmakuComposer,
                        recentKaomoji = recentKaomoji, onKaomojiInserted = kaomojiModel::record,
                        isActive = isActive,
                        selectedPart = playback.selectedPart,
                        onSelectPart = playback::selectPart,
                        onOpenTag = onOpenTag,
                        modifier = Modifier.fillMaxSize(),
                        playerContent = playerContent,
                    )
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
