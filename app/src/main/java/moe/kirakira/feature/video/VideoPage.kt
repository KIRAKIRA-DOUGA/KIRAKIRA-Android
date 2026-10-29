package moe.kirakira.feature.video

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import moe.kirakira.core.share.shareContent
import moe.kirakira.core.share.videoShareUrl
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.history.HistoryRepository
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.feature.player.MediaVolumeController
import moe.kirakira.feature.player.PlaybackPageViewport
import moe.kirakira.feature.player.PlaybackViewModel
import moe.kirakira.feature.player.PlaybackViewportState
import moe.kirakira.feature.player.PlayerUiState
import moe.kirakira.feature.player.VideoPlayer
import moe.kirakira.ui.components.messageRes
import moe.kirakira.ui.components.video.rememberVideoZoomPlaceholder

private data object FullscreenInfo : NavigationEventInfo()

private data class InlinePlayerLayout(
    val width: Dp,
    val height: Dp,
    val contentPadding: PaddingValues,
)

@Composable
internal fun VideoPage(
    playbackSettings: PlaybackSettings?,
    playback: PlaybackViewModel,
    viewportState: PlaybackViewportState,
    videoId: Int,
    repository: ContentRepository,
    kaomojiModel: KaomojiViewModel,
    onBack: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onLogin: () -> Unit,
    onOpenTag: (Long) -> Unit,
    modifier: Modifier = Modifier,
    historyRepository: HistoryRepository? = null,
    settingsRepository: AccountSettingsRepository? = null,
    isActive: Boolean = true,
    surfaceVisible: Boolean = true,
    initialImage: String? = null,
    onQualityPreference: (Int?) -> Unit = {},
    danmakuSettings: DanmakuSettings? = null,
    onDanmakuEnabled: (Boolean) -> Unit = {},
    onOutsideAppMiniPlayerChange: (Boolean) -> Unit = {},
    onInAppMiniPlayerChange: (Boolean) -> Unit = {},
    onAutoplayChange: (Boolean) -> Unit = {},
    onDanmakuSettingsChange: (DanmakuSettings) -> Unit = {},
    onPlayerBounds: (Rect) -> Unit = {},
) {
    val model = viewModel { VideoViewModel(videoId, repository, historyRepository, settingsRepository) }
    val artworkPlaceholder = rememberVideoZoomPlaceholder(viewportState.cards, videoId)
    val recentKaomoji by kaomojiModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mediaVolume = remember(context) { MediaVolumeController(context) }
    val resources by rememberUpdatedState(LocalResources.current)
    val detail by model.detail.collectAsStateWithLifecycle()
    val access by model.access.collectAsStateWithLifecycle()
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
    val configuration = LocalConfiguration.current
    // Multi-window and Android 16+ large displays can ignore Activity orientation requests.
    val fullscreenInCurrentWindow = activity == null || activity.isInMultiWindowMode ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && configuration.smallestScreenWidthDp >= 600)
    val layoutDirection = LocalLayoutDirection.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var fullscreen by remember { mutableStateOf(false) }
    var windowFullscreen by remember { mutableStateOf(false) }
    var fullscreenInlineLayout by remember { mutableStateOf<InlinePlayerLayout?>(null) }
    val detailsInteractive = isActive && !pip && !fullscreen && !windowFullscreen
    fun requestFullscreen(target: Boolean) {
        fullscreen = target
        if (!target) fullscreenInlineLayout = null
        val windowTarget = target && isActive && !pip
        if (windowFullscreen != windowTarget) activity?.setVideoFullscreen(windowTarget)
        windowFullscreen = windowTarget
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val accessBlocked = access.unavailable || access.playbackInvalidationPending
    val displayedDetail = if (accessBlocked) detail.copy(
        data = null,
        loading = detail.loading || !access.unavailable,
    ) else detail
    val value = displayedDetail.data
    val signedIn = session.activeUuid != null
    fun requireLogin(action: () -> Unit) { if (signedIn) action() else { playback.pause(); onLogin() } }
    fun unavailable() { scope.launch { snackbar.showSnackbar(resources.getString(R.string.video_action_unavailable)) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(access, value, resume, playbackSettings, isActive, lifecycleState, session.revision, playback.videoId) {
        if (playback.videoId != videoId) return@LaunchedEffect
        // Read the latest state: a refresh can finish before the collected UI state catches up.
        val currentAccess = model.access.value
        if (currentAccess.playbackInvalidationPending) {
            playback.setContent("", emptyList())
            if (playback.miniPlayer) playback.closeMiniPlayer()
            model.playbackInvalidated()
            return@LaunchedEffect
        }
        if (!isActive || currentAccess.unavailable) return@LaunchedEffect
        val current = model.detail.value.data
        if (current != null) {
            playback.setContent(current.summary.title, current.parts,
                resume.positionMs, resume.ready, artwork = current.summary.image)
            if (resume.ready && lifecycleState == Lifecycle.State.RESUMED && playbackSettings != null) {
                playback.maybeAutoplay(playbackSettings.autoplay)
            }
        }
    }
    LaunchedEffect(error) {
        error?.let { snackbar.showSnackbar(resources.getString(if (it == moe.kirakira.core.network.ApiFailure.NETWORK ||
                it == moe.kirakira.core.network.ApiFailure.TIMEOUT || it == moe.kirakira.core.network.ApiFailure.SERVER ||
                it == moe.kirakira.core.network.ApiFailure.INVALID_RESPONSE) R.string.content_mutation_uncertain else it.messageRes())); model.dismissActionError() }
    }
    LaunchedEffect(fullscreen, pip, isActive) {
        if (fullscreen || pip || !isActive) {
            commentComposer.panelOpen = false
            danmakuComposer.panelOpen = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    LaunchedEffect(fullscreen, pip, isActive, activity) {
        requestFullscreen(fullscreen && isActive)
    }
    DisposableEffect(activity) {
        onDispose {
            if (windowFullscreen) activity?.setVideoFullscreen(false)
        }
    }
    val fullscreenActive = fullscreen || windowFullscreen
    NavigationBackHandler(
        rememberNavigationEventState(FullscreenInfo),
        isBackEnabled = isActive && fullscreenActive && !pip,
        onBackCompleted = { requestFullscreen(false) },
    )
    Box(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { if (!pip) SnackbarHost(snackbar) },
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val topPadding = padding.calculateTopPadding()
                val startPadding = padding.calculateStartPadding(layoutDirection)
                val endPadding = padding.calculateEndPadding(layoutDirection)
                val playerWidth = (maxWidth - startPadding - endPadding).coerceAtLeast(0.dp)
                val contentHeight = (maxHeight - topPadding).coerceAtLeast(0.dp)
                val playerHeight = (playerWidth * 9f / 16f).coerceAtMost(contentHeight * 0.42f)
                val currentInlineLayout = InlinePlayerLayout(
                    width = playerWidth,
                    height = playerHeight,
                    contentPadding = PaddingValues(
                        start = startPadding,
                        top = topPadding,
                        end = endPadding,
                        bottom = padding.calculateBottomPadding(),
                    ),
                )
                // The IME can shorten portrait bounds; rotation can update Configuration before the new bounds arrive.
                val landscapeWindow = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    maxWidth > maxHeight
                val playerFillsWindow = pip ||
                    (fullscreen && (landscapeWindow || fullscreenInCurrentWindow))
                val inlineLayout = if (fullscreen && !playerFillsWindow) {
                    fullscreenInlineLayout ?: currentInlineLayout
                } else currentInlineLayout
                Box(
                    Modifier.fillMaxSize()
                        .padding(
                            top = inlineLayout.contentPadding.calculateTopPadding(),
                        )
                        .drawWithContent {
                            if (!playerFillsWindow) drawContent()
                        }
                        .then(
                            if (detailsInteractive) Modifier else Modifier
                                .clearAndSetSemantics { }
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                        }
                                    }
                                },
                        )
                        .focusProperties {
                            onEnter = { if (!detailsInteractive) cancelFocusChange() }
                        }
                        .focusGroup(),
                ) {
                    VideoScreen(
                        VideoUiState(
                            detail = displayedDetail,
                            access = access,
                            comments = comments,
                            danmaku = danmaku,
                            busy = busy,
                            signedIn = signedIn,
                            sessionRevision = session.revision,
                            commentDraft = commentDraft,
                            danmakuDraft = danmakuDraft,
                            posted = posted,
                            danmakuStyle = danmakuStyle,
                        ),
                        model::refresh, { requireLogin(model::follow) }, { requireLogin { model.vote(it) } },
                        { comment, reaction -> requireLogin { model.voteComment(comment, reaction) } },
                        model::comments, model::refreshComments, model::retryComments,
                        model::loadAdjacentComments, model::consumeCommentLocation, model::ensureTab,
                        model::updateCommentDraft, model::updateDanmakuDraft,
                        { requireLogin(model::sendComment) }, { requireLogin { model.sendDanmaku(playback.positionMs) } },
                        model::refreshDanmaku, model::retryDanmaku, onOpenProfile,
                        ::unavailable,
                        {
                            val title = value?.summary?.title.takeIf {
                                model.session.value.revision == session.revision
                            }
                            if (!shareContent(context, videoShareUrl(videoId), title)) {
                                scope.launch { snackbar.showSnackbar(resources.getString(R.string.share_unavailable)) }
                            }
                        },
                        { requireLogin {} }, inlineLayout.contentPadding,
                        onDanmakuStyle = model::updateDanmakuStyle,
                        commentComposer = commentComposer, danmakuComposer = danmakuComposer,
                        recentKaomoji = recentKaomoji, onKaomojiInserted = kaomojiModel::record,
                        isActive = detailsInteractive,
                        selectedPart = playback.selectedPart,
                        onSelectPart = playback::selectPart,
                        onOpenTag = onOpenTag,
                        playerHeight = inlineLayout.height,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                VideoPlayerViewport(
                    inlineLayout = inlineLayout,
                    fullscreen = playerFillsWindow,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .onGloballyPositioned {
                                val bounds = it.boundsInWindow().let { b ->
                                    Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                                }
                                val position = it.positionInParent()
                                viewportState.pageViewport = PlaybackPageViewport(
                                    videoId,
                                    androidx.compose.ui.geometry.Rect(
                                        position.x, position.y,
                                        position.x + it.size.width, position.y + it.size.height,
                                    ),
                                )
                                if (isActive) onPlayerBounds(bounds)
                            },
                    ) {
                        VideoPlayer(
                            danmaku = danmaku.data.orEmpty().takeUnless { accessBlocked }.orEmpty(),
                            playbackSettings = playbackSettings,
                            danmakuSettings = danmakuSettings,
                            onOutsideAppMiniPlayerChange = onOutsideAppMiniPlayerChange,
                            onInAppMiniPlayerChange = onInAppMiniPlayerChange,
                            onAutoplayChange = onAutoplayChange,
                            onDanmakuSettingsChange = onDanmakuSettingsChange,
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
                            player = playback.player.takeIf {
                                !accessBlocked && surfaceVisible && playback.videoId == videoId
                            },
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
                                resumeNoticePositionMs = playback.resumeNoticePositionMs,
                                available = !accessBlocked && isActive && playback.videoId == videoId &&
                                    (playback.player != null || value?.parts?.getOrNull(playback.selectedPart)?.url != null),
                            ),
                            image = if (accessBlocked) null else value?.summary?.image ?: initialImage,
                            artworkPlaceholder = artworkPlaceholder.takeUnless { accessBlocked },
                            fullscreen = fullscreenActive,
                            pictureInPicture = pip,
                            onToggle = playback::toggle,
                            onRetry = playback::play,
                            onSeek = playback::seek,
                            onReadVolume = mediaVolume::read,
                            onVolume = mediaVolume::set,
                            onBack = {
                                if (isActive && !pip) {
                                    if (fullscreenActive) requestFullscreen(false)
                                    else onBack()
                                }
                            },
                            onFullscreen = {
                                if (isActive && !pip) {
                                    if (!fullscreen) fullscreenInlineLayout = currentInlineLayout
                                    requestFullscreen(!fullscreen)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (!playerFillsWindow) {
                    // Keep the status-bar backdrop in place while the system prepares the landscape window.
                    Spacer(
                        Modifier.fillMaxWidth()
                            .height(inlineLayout.contentPadding.calculateTopPadding())
                            .background(Color.Black),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerViewport(
    inlineLayout: InlinePlayerLayout,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    // Remeasure one persistent player node; SurfaceView never moves between composition branches.
    Layout(content = content, modifier = modifier.fillMaxSize()) { measurables, constraints ->
        val width = if (fullscreen) constraints.maxWidth else {
            inlineLayout.width.roundToPx().coerceIn(0, constraints.maxWidth)
        }
        val height = if (fullscreen) constraints.maxHeight else {
            inlineLayout.height.roundToPx().coerceIn(0, constraints.maxHeight)
        }
        val player = measurables.single().measure(Constraints.fixed(width, height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            player.placeRelative(
                x = if (fullscreen) 0 else inlineLayout.contentPadding.calculateStartPadding(layoutDirection).roundToPx(),
                y = if (fullscreen) 0 else inlineLayout.contentPadding.calculateTopPadding().roundToPx(),
            )
        }
    }
}

private fun Context.mainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.mainActivity()
    else -> null
}
