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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.minus
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
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
import moe.kirakira.core.image.deliveryImageUrl
import moe.kirakira.core.share.shareContent
import moe.kirakira.core.share.videoShareUrl
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.history.HistoryRepository
import moe.kirakira.data.image.ImageSource
import moe.kirakira.data.settings.AccountSettingsRepository
import moe.kirakira.data.settings.DanmakuSettings
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.feature.imageviewer.ViewerImage
import moe.kirakira.feature.player.MediaVolumeController
import moe.kirakira.feature.player.MiniPlayerExit
import moe.kirakira.feature.player.PlaybackViewModel
import moe.kirakira.feature.player.PlayerUiState
import moe.kirakira.feature.player.TabletopTransport
import moe.kirakira.feature.player.VideoPlayer
import moe.kirakira.ui.adaptive.LocalAppWindowLayout
import moe.kirakira.ui.adaptive.WindowPane
import moe.kirakira.ui.adaptive.clampTo
import moe.kirakira.ui.adaptive.excludedInsets
import moe.kirakira.ui.components.scaffold.StatusBarScrollToTopPauseEffect
import moe.kirakira.ui.components.status.messageRes
import moe.kirakira.ui.components.video.rememberVideoZoomPlaceholder
import moe.kirakira.ui.navigation.PlaybackPageViewport
import moe.kirakira.ui.navigation.PlaybackViewportState

private data object FullscreenInfo : NavigationEventInfo()

private data class InlinePlayerLayout(
    val width: Dp,
    val height: Dp,
    val contentPadding: PaddingValues,
    val bounds: IntRect? = null,
)

private data class FullscreenRequest(
    val inlineLayout: InlinePlayerLayout,
    val rotateToLandscape: Boolean,
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
    onOpenImage: (ViewerImage) -> Unit,
    modifier: Modifier = Modifier,
    historyRepository: HistoryRepository? = null,
    settingsRepository: AccountSettingsRepository? = null,
    isActive: Boolean = true,
    surfaceVisible: Boolean = true,
    miniPlayerExit: MiniPlayerExit? = null,
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
    val mutation by model.mutation.collectAsStateWithLifecycle()
    val busy = mutation != null
    val session by model.session.collectAsStateWithLifecycle()
    val commentDraft = model.commentDraft.collectAsStateWithLifecycle().value
    val danmakuStyle by model.danmakuStyle.collectAsStateWithLifecycle()
    val danmakuDraft = model.danmakuDraft.collectAsStateWithLifecycle().value
    val updateCommentDraft: (String) -> Unit = { model.updateCommentDraft(it, commentDraft.clearRevision) }
    val updateDanmakuDraft: (String) -> Unit = { model.updateDanmakuDraft(it, danmakuDraft.clearRevision) }
    val commentComposer = rememberComposerState(videoId, session.revision, commentDraft)
    val danmakuComposer = rememberComposerState(videoId, session.revision, danmakuDraft)
    val posted by model.posted.collectAsStateWithLifecycle()
    val error by model.actionError.collectAsStateWithLifecycle()
    val publicationRetry by model.publicationRetry.collectAsStateWithLifecycle()
    val activity = remember(context) { context.mainActivity() }
    val pip = activity?.pictureInPicture == true
    val configuration = LocalConfiguration.current
    val window = LocalAppWindowLayout.current
    val safeWindow = window.safeBounds
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val panes = with(density) { videoPaneLayout(window, layoutDirection) }
    val wideWindow = with(density) { safeWindow.width.toDp() >= 600.dp && safeWindow.height.toDp() >= 480.dp }
    val canFullscreenInPlace = activity == null || activity.isInMultiWindowMode ||
        window.primaryPane != null || wideWindow ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && configuration.smallestScreenWidthDp >= 600)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var fullscreenRequest by remember { mutableStateOf<FullscreenRequest?>(null) }
    val fullscreen = fullscreenRequest != null
    var windowFullscreen by remember { mutableStateOf(false) }
    val detailsInteractive = isActive && !pip && !fullscreen && !windowFullscreen
    StatusBarScrollToTopPauseEffect(paused = isActive && !detailsInteractive)
    if (publicationRetry != null && detailsInteractive) {
        PublicationRetryDialog(
            busy, model::dismissPublicationRetry, model::checkPublication,
            {
                when (publicationRetry) {
                    PublicationRetry.Comment -> commentComposer.binding.publish(updateCommentDraft)
                    is PublicationRetry.Danmaku -> danmakuComposer.binding.publish(updateDanmakuDraft)
                    null -> Unit
                }
                model.confirmPublicationRetry()
            },
        )
    }
    fun requestFullscreen(request: FullscreenRequest?) {
        fullscreenRequest = request
        val windowTarget = request != null && isActive && !pip
        if (windowTarget || windowFullscreen) {
            activity?.setVideoFullscreen(windowTarget, rotateToLandscape = request?.rotateToLandscape == true)
        }
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
    LaunchedEffect(
        access, value, resume, playbackSettings, isActive, lifecycleState, session.revision, playback.videoId, miniPlayerExit,
    ) {
        // Read the latest state: a refresh can finish before the collected UI state catches up.
        val currentAccess = model.access.value
        if (miniPlayerExit != null) {
            if (currentAccess.unavailable || currentAccess.playbackInvalidationPending) {
                playback.finishMiniPlayerExit(miniPlayerExit)
                if (currentAccess.playbackInvalidationPending) model.playbackInvalidated()
            }
            return@LaunchedEffect
        }
        if (playback.videoId != videoId) return@LaunchedEffect
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
        error?.let {
            val message = if (it.isUncertainMutation) R.string.content_mutation_uncertain else it.messageRes()
            snackbar.showSnackbar(resources.getString(message))
            model.dismissActionError()
        }
    }
    LaunchedEffect(fullscreen, pip, isActive) {
        if (fullscreen || pip || !isActive) {
            commentComposer.panelOpen = false
            danmakuComposer.panelOpen = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    LaunchedEffect(pip, isActive, activity) {
        requestFullscreen(fullscreenRequest.takeIf { isActive })
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
        onBackCompleted = { requestFullscreen(null) },
    )
    Box(
        modifier.fillMaxSize()
            .dismissComposersOnOutsideTap(commentComposer, danmakuComposer, enabled = detailsInteractive),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val topPadding = padding.calculateTopPadding()
                val startPadding = padding.calculateStartPadding(layoutDirection)
                val endPadding = padding.calculateEndPadding(layoutDirection)
                val viewportSize = IntSize(constraints.maxWidth, constraints.maxHeight)
                val inlinePane = panes.viewing?.clampTo(viewportSize.width, viewportSize.height)
                val detailPane = panes.details?.clampTo(viewportSize.width, viewportSize.height)
                val introductionColumn = panes.introductionColumn?.clampTo(viewportSize.width, viewportSize.height)
                val availableWidth = inlinePane?.let { with(density) { it.width.toDp() } }
                    ?: (maxWidth - startPadding - endPadding).coerceAtLeast(0.dp)
                val contentHeight = inlinePane?.let { with(density) { it.height.toDp() } }
                    ?: (maxHeight - topPadding).coerceAtLeast(0.dp)
                val maxPlayerHeight = if (introductionColumn != null) contentHeight * (2f / 3f) else contentHeight
                val playerWidth = if (panes.stacked) availableWidth else minOf(availableWidth, maxPlayerHeight * (16f / 9f))
                val playerHeight = if (panes.stacked) minOf(playerWidth * (9f / 16f), contentHeight * 0.42f)
                    else playerWidth * (9f / 16f)
                val playerRect = inlinePane?.let { pane ->
                    with(density) {
                        val width = playerWidth.roundToPx()
                        val height = playerHeight.roundToPx()
                        val left = pane.left + (pane.width - width) / 2
                        val top = if (panes.stacked || introductionColumn != null) pane.top
                            else pane.top + (pane.height - height) / 2
                        IntRect(left, top, left + width, top + height)
                    }
                }
                val currentInlineLayout = InlinePlayerLayout(
                    width = playerWidth,
                    height = playerHeight,
                    contentPadding = PaddingValues(
                        start = startPadding,
                        top = topPadding,
                        end = endPadding,
                        bottom = padding.calculateBottomPadding(),
                    ),
                    bounds = playerRect,
                )
                val introductionPane = introductionColumn?.let { column ->
                    column.copy(top = (playerRect?.bottom ?: column.top).coerceIn(column.top, column.bottom))
                }
                fun contentPadding(pane: IntRect?) = if (pane == null) padding
                    else padding - pane.excludedInsets(viewportSize).asPaddingValues(density)
                val detailPadding = contentPadding(detailPane)
                val introductionPadding = contentPadding(introductionPane)
                val imeVisible = WindowInsets.ime.getBottom(density) > 0
                // The IME can shorten portrait bounds; rotation can update Configuration before the new bounds arrive.
                val landscapeWindow = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    maxWidth > maxHeight
                val request = fullscreenRequest
                val playerFillsWindow = pip || (request != null && (!request.rotateToLandscape || landscapeWindow))
                val inlineLayout = if (request != null && !playerFillsWindow) {
                    request.inlineLayout
                } else currentInlineLayout
                val playerFullscreenPane = panes.viewing.takeIf { window.primaryPane != null && !pip }
                val tabletopTransport = panes.tabletop && !fullscreenActive && !pip && !imeVisible
                val playerAvailable = !accessBlocked && isActive && playback.videoId == videoId &&
                    (playback.player != null || value?.parts?.getOrNull(playback.selectedPart)?.url != null)
                VideoScreen(
                    VideoUiState(
                        detail = displayedDetail,
                        access = access,
                        comments = comments,
                        danmaku = danmaku,
                        busy = busy,
                        mutation = mutation,
                        signedIn = signedIn,
                        sessionRevision = session.revision,
                        commentDraft = commentDraft.text,
                        danmakuDraft = danmakuDraft.text,
                        posted = posted,
                        danmakuStyle = danmakuStyle,
                    ),
                    model::refresh, { requireLogin(model::follow) }, { requireLogin { model.vote(it) } },
                    { comment, reaction -> requireLogin { model.voteComment(comment, reaction) } },
                    model::comments, model::refreshComments, model::retryComments,
                    model::loadAdjacentComments, model::consumeCommentLocation, model::ensureTab,
                    updateCommentDraft, updateDanmakuDraft,
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
                    { requireLogin {} }, detailPadding,
                    onViewCover = {
                        val image = value?.summary?.image.takeIf {
                            model.session.value.revision == session.revision && detailsInteractive
                        }
                        deliveryImageUrl(image)?.let { url ->
                            onOpenImage(
                                ViewerImage(
                                    source = ImageSource.RemoteUrl(url),
                                    description = resources.getString(
                                        R.string.video_cover_description, value?.summary?.title.orEmpty(),
                                    ),
                                    fileName = "cover_$videoId",
                                    thumbnail = deliveryImageUrl(image, 720)?.let(ImageSource::RemoteUrl),
                                ),
                            )
                        }
                    },
                    onDanmakuStyle = model::updateDanmakuStyle,
                    commentComposer = commentComposer, danmakuComposer = danmakuComposer,
                    recentKaomoji = recentKaomoji, onKaomojiInserted = kaomojiModel::record,
                    isActive = detailsInteractive,
                    selectedPart = playback.selectedPart,
                    onSelectPart = playback::selectPart,
                    onOpenTag = onOpenTag,
                    playerHeight = if (panes.stacked) inlineLayout.height else 0.dp,
                    modifier = Modifier.fillMaxSize()
                        .padding(top = if (detailPane == null) inlineLayout.contentPadding.calculateTopPadding() else 0.dp)
                        .drawWithContent { if (!playerFillsWindow) drawContent() }
                        .then(
                            if (introductionPane != null) Modifier.background(MaterialTheme.colorScheme.background)
                            else Modifier,
                        )
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
                        .focusProperties { onEnter = { if (!detailsInteractive) cancelFocusChange() } }
                        .focusGroup(),
                    detailPane = detailPane,
                    introductionPane = introductionPane,
                    introductionPadding = introductionPadding,
                    wideWindow = wideWindow,
                    aboveTabs = {
                        if (tabletopTransport) TabletopTransport(
                            showPause = playback.showPauseIcon,
                            loading = playback.buffering || playback.initialLoading,
                            canPlay = playerAvailable,
                            canSeek = playerAvailable && playback.durationMs > 0 && !playback.initialLoading &&
                                playback.player?.isCurrentMediaItemSeekable == true,
                            position = playback.positionMs,
                            duration = playback.durationMs,
                            onToggle = playback::toggle,
                            onSeek = playback::seek,
                        )
                    },
                )
                if (playerFillsWindow && playerFullscreenPane != null) {
                    Spacer(Modifier.fillMaxSize().background(Color.Black))
                } else if (!playerFillsWindow && !panes.stacked) {
                    val backdrop = if (introductionColumn != null && introductionPane != null) {
                        introductionColumn.copy(bottom = introductionPane.top)
                    } else inlinePane
                    WindowPane(backdrop) { Spacer(Modifier.fillMaxSize().background(Color.Black)) }
                }
                VideoPlayerViewport(
                    inlineLayout = inlineLayout,
                    fullscreen = playerFillsWindow,
                    fullscreenPane = playerFullscreenPane,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .onGloballyPositioned {
                                if (miniPlayerExit != null) return@onGloballyPositioned
                                val position = it.positionInParent()
                                viewportState.pageViewport = PlaybackPageViewport(
                                    videoId,
                                    androidx.compose.ui.geometry.Rect(
                                        position.x, position.y,
                                        position.x + it.size.width, position.y + it.size.height,
                                    ),
                                )
                                if (isActive) {
                                    val bounds = it.boundsInWindow()
                                    onPlayerBounds(
                                        Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()),
                                    )
                                }
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
                            player = if (accessBlocked || !surfaceVisible) null
                                else if (miniPlayerExit != null) miniPlayerExit.player
                                else playback.player.takeIf { playback.videoId == videoId },
                            state = if (miniPlayerExit != null) PlayerUiState() else PlayerUiState(
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
                                available = playerAvailable,
                            ),
                            image = if (accessBlocked) null else value?.summary?.image ?: initialImage,
                            artworkPlaceholder = artworkPlaceholder.takeUnless { accessBlocked },
                            fullscreen = fullscreenActive,
                            fullscreenLayoutReady = playerFillsWindow,
                            inlinePlayerBottom = when {
                                introductionPane != null -> with(density) { introductionPane.top.toDp() }
                                inlinePane == null -> inlineLayout.contentPadding.calculateTopPadding() + inlineLayout.height
                                else -> inlineLayout.height
                            },
                            externalTransport = tabletopTransport,
                            confinedToPane = playerFullscreenPane != null,
                            pictureInPicture = pip,
                            onToggle = playback::toggle,
                            onRetry = playback::play,
                            onSeek = playback::seek,
                            onReadVolume = mediaVolume::read,
                            onVolume = mediaVolume::set,
                            onBack = {
                                if (isActive && !pip) {
                                    if (fullscreenActive) requestFullscreen(null)
                                    else onBack()
                                }
                            },
                            onFullscreen = {
                                if (isActive && !pip) {
                                    val nextRequest = if (fullscreenRequest == null) {
                                        // Keep the entry policy through rotation and picture-in-picture.
                                        FullscreenRequest(
                                            currentInlineLayout,
                                            rotateToLandscape = !canFullscreenInPlace,
                                        )
                                    } else null
                                    requestFullscreen(nextRequest)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (!playerFillsWindow) {
                    // Keep white status icons legible in every layout and during fullscreen entry.
                    Spacer(
                        Modifier.fillMaxWidth()
                            .height(inlineLayout.contentPadding.calculateTopPadding())
                            .background(Color.Black),
                    )
                }
                if (!pip && !playerFillsWindow) WindowPane(detailPane) {
                    Box(Modifier.fillMaxSize().padding(detailPadding), contentAlignment = Alignment.BottomCenter) {
                        SnackbarHost(snackbar)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerViewport(
    inlineLayout: InlinePlayerLayout,
    fullscreen: Boolean,
    fullscreenPane: IntRect?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    // Remeasure one persistent player node; SurfaceView never moves between composition branches.
    Layout(content = content, modifier = modifier.fillMaxSize()) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val explicit = if (fullscreen) fullscreenPane else inlineLayout.bounds
        val viewport = explicit?.clampTo(width, height)
        val playerWidth = viewport?.width ?: if (fullscreen) width else inlineLayout.width.roundToPx().coerceIn(0, width)
        val playerHeight = viewport?.height ?: if (fullscreen) height else inlineLayout.height.roundToPx().coerceIn(0, height)
        val player = measurables.single().measure(Constraints.fixed(playerWidth, playerHeight))
        layout(width, height) {
            if (viewport != null) player.place(viewport.left, viewport.top)
            else player.placeRelative(
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
