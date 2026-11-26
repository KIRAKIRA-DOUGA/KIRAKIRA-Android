package moe.kirakira.feature.player

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.kirakira.MainActivity
import moe.kirakira.R
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.ui.adaptive.LocalAppWindowLayout
import moe.kirakira.ui.adaptive.clampTo
import moe.kirakira.ui.adaptive.windowCornerRadii
import moe.kirakira.ui.components.motion.mediaZoomSpec
import moe.kirakira.ui.components.motion.rememberEmphasizedEasing
import moe.kirakira.ui.components.status.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.navigation.NavigationPageTransform
import moe.kirakira.ui.navigation.PlaybackViewportState
import moe.kirakira.ui.navigation.VideoBackPreview
import moe.kirakira.ui.navigation.VideoZoomPush
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.geometry.Rect as RectF

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun PlaybackHost(
    playback: PlaybackViewModel,
    videoId: Int,
    viewportState: PlaybackViewportState,
    backPreview: VideoBackPreview?,
    sourceOwner: Any,
    settings: PlaybackSettings?,
    videoPageActive: Boolean,
    imageViewerActive: Boolean,
    videoPageUnderImage: Boolean,
    videoPageCovered: Boolean,
    mainNavigationSize: IntSize,
    miniPlayerExit: MiniPlayerExitViewport?,
    hostScale: () -> Float,
    onRestore: () -> Unit,
    onCardExitFinished: () -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (Modifier, Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.playerActivity() }
    val pip = activity?.pictureInPicture == true
    var horizontal by rememberSaveable { mutableFloatStateOf(1f) }
    var vertical by rememberSaveable { mutableFloatStateOf(1f) }
    val outgoing = miniPlayerExit?.session
    val outgoingToCard = outgoing?.toCard == true
    if (outgoing == null) {
        PlaybackActivityEffects(playback, activity, settings, videoPageActive, imageViewerActive, pip)
    } else {
        DisposableEffect(activity, playback, outgoing) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) playback.finishMiniPlayerExit(outgoing)
            }
            activity?.lifecycle?.addObserver(observer)
            onDispose {
                activity?.lifecycle?.removeObserver(observer)
                playback.finishMiniPlayerExit(outgoing)
            }
        }
    }
    val player = if (outgoing != null) outgoing.player else playback.player
    val isMiniPlayer = if (outgoing != null) !outgoingToCard else playback.miniPlayer
    val loading = outgoing?.loading ?: (playback.buffering || playback.initialLoading)
    val showPauseIcon = outgoing == null && playback.showPauseIcon
    var closeRequested by remember(player) { mutableStateOf(false) }
    val closing = closeRequested || (outgoing != null && !outgoingToCard)
    // A detached card return keeps the same flag, so its running animation is not restarted.
    val exitingCard = outgoingToCard || (outgoing == null && viewportState.exitingVideoId == videoId)
    // A card return has left the stack: an ordinary page transition still settling around it no longer
    // owns this page, so it neither pauses the return nor withholds its background push.
    val navigatingPage = outgoing == null && !exitingCard && viewportState.navigatingPage
    val coveredMini = videoPageCovered && !videoPageActive
    val miniTarget = isMiniPlayer && !videoPageActive && !videoPageUnderImage &&
        !pip && !exitingCard && !navigatingPage
    val visible = videoPageUnderImage || (
        (videoPageActive || videoPageCovered || isMiniPlayer || pip || exitingCard) &&
            !imageViewerActive && (outgoing == null || !pip)
        )
    val origin = if (miniPlayerExit == null) viewportState.cards.origin?.takeIf { it.videoId == videoId }
    else miniPlayerExit.origin

    fun pageViewport() = if (miniPlayerExit == null) viewportState.pageViewport else miniPlayerExit.pageViewport
    val cardProgress = remember(origin) { Animatable(if (origin != null && videoPageActive) 0f else 1f) }
    val currentCardExitFinished by rememberUpdatedState(onCardExitFinished)
    val currentOutgoing by rememberUpdatedState(outgoing)
    val miniPreview = backPreview?.takeIf { it.toMiniPlayer }
    val cardPreview = backPreview?.takeUnless { it.toMiniPlayer }
    val returningToCard = exitingCard || cardPreview != null
    // A return to a card leaves taps to the page behind: the card where it started decides whether it
    // reopens, and any other card opens its own video, instead of whichever one the flight covers.
    val passToPage = exitingCard
    val currentExitingCard = rememberUpdatedState(exitingCard)
    // A return to the mini player has no card to tap, so the flying video itself restores it. A gesture
    // preview still owns the motion.
    val currentCanRestore by rememberUpdatedState { outgoing == null && backPreview == null && miniTarget }
    val currentRestore by rememberUpdatedState(onRestore)
    // The flight is drawn above every page, so it cannot follow its card under a page that replaces
    // the source. Once the card leaves the active page, the flight dissolves along its path and the
    // card shows again inside its own page, leaving with it.
    val sourceLeft = exitingCard && origin != null &&
        (sourceOwner != origin.owner || !viewportState.cards.shown(origin.key.group))
    val returnPresence = remember(origin) { Animatable(1f) }
    val returnPresenceSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(returnPresence, sourceLeft) {
        returnPresence.animateTo(if (sourceLeft) 0f else 1f, returnPresenceSpec)
    }
    val currentSourceLeft = rememberUpdatedState(sourceLeft)
    val hideSource = origin != null && returningToCard && visible && !pip && !sourceLeft &&
        pageViewport()?.videoId == videoId && viewportState.cards.painter(origin) != null
    DisposableEffect(viewportState.cards, origin, hideSource) {
        if (hideSource) viewportState.cards.hideSource(origin)
        onDispose {
            if (hideSource) viewportState.cards.showSource(origin)
        }
    }
    val zoom = remember(origin) { Animatable(if (miniTarget) 1f else 0f) }
    val cardVelocity = remember(origin) { ZoomVelocity() }
    // The last place the card was seen during a return; written while drawing the flight, so a plain
    // holder rather than snapshot state.
    val lastCardBounds = remember(origin) { LastBounds(origin?.bounds) }
    // The page transition this video was drawn with when its return began; same holder reasoning.
    val returnTransform = remember(origin) { LastTransform() }
    val zoomVelocity = remember(origin) { ZoomVelocity() }
    val emphasizedEasing = rememberEmphasizedEasing()

    // Expanding uses an iOS-style critically damped spring (as SwiftUI `.smooth`): it starts at rest,
    // has no fixed end time to brake into, and keeps velocity when interrupted.
    fun zoomSpec(expanding: Boolean) = if (expanding) {
        spring(dampingRatio = 1f, stiffness = VIDEO_ZOOM_EXPAND_STIFFNESS, visibilityThreshold = 0.001f)
    } else {
        mediaZoomSpec(expanding = false, emphasizedEasing, visibilityThreshold = 0.001f)
    }
    // Once detached, the return joins the background; the native video surface cannot take the
    // background's blur, so the artwork covers the paused frame.
    val detachedArtwork = remember(origin) { Animatable(0f) }
    val detachedArtworkSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(detachedArtwork, outgoingToCard) {
        if (outgoingToCard) detachedArtwork.animateTo(1f, detachedArtworkSpec) else detachedArtwork.snapTo(0f)
    }
    val backgroundScope = rememberCoroutineScope()
    val backgroundPush = remember(origin) { BackgroundPushMotion(backgroundScope) }
    DisposableEffect(backgroundPush) {
        onDispose(backgroundPush::cancel)
    }

    // The page behind returns on its own time line, spanning the whole return spring including its
    // bounce: it neither finishes at the spring's first crossing nor outlasts the landing on the card.
    // [pushDirection] is the sign with which the progress moves the push.
    suspend fun animateReturn(
        progress: Animatable<Float, AnimationVector1D>,
        velocity: ZoomVelocity,
        target: Float,
        pushDirection: Float,
    ) {
        val spec = zoomSpec(expanding = false)
        val carried = velocity.value
        val durationMillis = (spec.vectorize(Float.VectorConverter).getDurationNanos(
            AnimationVector1D(progress.value), AnimationVector1D(target), AnimationVector1D(carried),
        ) / 1_000_000L).toInt()
        backgroundPush.startReturn(
            durationMillis = durationMillis,
            // Only the full page hides the page behind it; a mini player leaving mid-expansion does not.
            hidden = cardProgress.value >= 1f && zoom.value <= 0f,
            followVelocity = pushDirection * carried,
        )
        progress.animateTo(target, spec, velocity)
    }

    suspend fun animateExpand(
        progress: Animatable<Float, AnimationVector1D>,
        velocity: ZoomVelocity,
        target: Float,
        pushDirection: Float,
    ) {
        val spec = zoomSpec(expanding = true)
        backgroundPush.startFollow(spec, followVelocity = pushDirection * velocity.value)
        progress.animateTo(target, spec, velocity)
    }

    // A back gesture that starts while a zoom is still in flight scales that flight instead of
    // snapping to its end: the flight keeps heading for [settled] and the gesture pulls it towards
    // [pulled]. Releasing hands the finger's speed on to the motion that follows.
    suspend fun followBackGesture(
        preview: VideoBackPreview,
        progress: Animatable<Float, AnimationVector1D>,
        velocity: ZoomVelocity,
        settled: Float,
        pulled: Float,
    ) = coroutineScope {
        val base = Animatable(progress.value)
        val carried = velocity.value
        val baseVelocity = ZoomVelocity()
        if (base.value != settled || carried != 0f) {
            baseVelocity.value = carried
            launch { base.animateTo(settled, zoomSpec(expanding = true), baseVelocity) }
        }
        backgroundPush.startFollow(zoomSpec(expanding = true), followVelocity = (settled - pulled) * carried)
        try {
            snapshotFlow { base.value + (pulled - base.value) * preview.progress }
                .collect { progress.snapTo(it) }
        } finally {
            velocity.value = baseVelocity.value * (1f - preview.progress) +
                (pulled - base.value) * preview.releaseVelocity
        }
    }
    LaunchedEffect(zoom, viewportState, miniTarget, pip, miniPreview, navigatingPage, coveredMini, closing) {
        // Ordinary navigation owns the whole page. The native video stays in this tree;
        // switch to/from its floating presentation only while the covering page obscures it.
        if (pip || navigatingPage) {
            zoomVelocity.value = 0f
            zoom.snapTo(0f)
        }
        else if (closing && miniTarget) return@LaunchedEffect
        else if (coveredMini) {
            zoomVelocity.value = 0f
            zoom.snapTo(if (miniTarget) 1f else 0f)
        }
        else if (miniPreview != null) followBackGesture(miniPreview, zoom, zoomVelocity, settled = 0f, pulled = 1f)
        else {
            val target = if (miniTarget) 1f else 0f
            if (zoom.value != target || zoomVelocity.value != 0f) {
                if (miniTarget) animateReturn(zoom, zoomVelocity, target, -1f)
                else animateExpand(zoom, zoomVelocity, target, -1f)
            }
        }
    }
    LaunchedEffect(cardProgress, viewportState, videoId, videoPageActive, exitingCard, cardPreview, pip) {
        when {
            pip -> {
                cardVelocity.value = 0f
                cardProgress.snapTo(1f)
            }
            cardPreview != null -> followBackGesture(cardPreview, cardProgress, cardVelocity, settled = 1f, pulled = 0f)

            exitingCard -> {
                if (cardProgress.value != 0f || cardVelocity.value != 0f) coroutineScope {
                    // A flight that has fully dissolved has nothing left to land on: end it there.
                    val flight = launch { animateReturn(cardProgress, cardVelocity, 0f, 1f) }
                    val dissolved = launch {
                        snapshotFlow { currentSourceLeft.value && returnPresence.value == 0f }.first { it }
                        flight.cancel()
                    }
                    flight.join()
                    dissolved.cancel()
                }
                currentOutgoing?.let(playback::finishMiniPlayerExit) ?: currentCardExitFinished()
            }

            videoPageActive -> {
                if (origin != null && cardProgress.value == 0f) {
                    // Keep the real source on screen until this video's full-size page is laid out.
                    snapshotFlow { viewportState.pageViewport }
                        .first { it?.videoId == videoId && it.bounds.width > 0 && it.bounds.height > 0 }
                    withFrameNanos { }
                }
                if (cardProgress.value != 1f || cardVelocity.value != 0f) {
                    animateExpand(cardProgress, cardVelocity, 1f, 1f)
                }
            }

            // Leaving for the mini player mid-expansion lets the expansion finish underneath it. The
            // mini player's return owns the background meanwhile, so this only moves the frame.
            cardProgress.value != 1f || cardVelocity.value != 0f ->
                cardProgress.animateTo(1f, zoomSpec(expanding = true), cardVelocity)
        }
    }
    val transitioning by remember(miniTarget, exitingCard, cardProgress) {
        derivedStateOf {
            zoom.isRunning || zoom.value != (if (miniTarget) 1f else 0f) || cardProgress.isRunning ||
                cardProgress.value != (if (exitingCard) 0f else 1f)
        }
    }
    val iconMotion = rememberPlaybackIconMotion(showPauseIcon)
    val iconColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
    val restoreLabel = stringResource(R.string.player_mini_restore)
    val toggleLabel = stringResource(if (showPauseIcon) R.string.player_pause else R.string.player_play)
    var controlsVisible by remember { mutableStateOf(true) }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible && miniTarget && !transitioning) 1f else 0f,
        animationSpec = if (controlsVisible) MaterialTheme.motionScheme.defaultEffectsSpec()
        else MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "MiniPlayerControlsAlpha",
    )
    val miniNavigationAlpha by animateFloatAsState(
        targetValue = if (navigatingPage) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "MiniPlayerNavigationAlpha",
    )
    var interaction by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val dockingAnimation = remember { Animatable(horizontal) }
    val dockingSpec = tween<Float>(durationMillis = 300, easing = emphasizedEasing)
    val closeSpec = remember {
        tween<Float>(durationMillis = 220, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))
    }
    val closeScale = remember(player) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var dockingJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(closing, player, isMiniPlayer) {
        if (!closing || player == null || !isMiniPlayer) {
            closeRequested = false
            closeScale.snapTo(1f)
            return@LaunchedEffect
        }
        dockingJob?.cancel()
        if (outgoing == null) playback.pause()
        closeScale.animateTo(0f, closeSpec)
        val exit = playback.miniPlayerExits.firstOrNull { it.player === player }
        if (exit != null) playback.finishMiniPlayerExit(exit)
        else if (playback.player === player && playback.miniPlayer) playback.closeMiniPlayer()
    }
    val restoreInteraction = remember { MutableInteractionSource() }
    val toggleInteraction = remember { MutableInteractionSource() }
    val closeInteraction = remember { MutableInteractionSource() }
    val restorePressed by restoreInteraction.collectIsPressedAsState()
    val togglePressed by toggleInteraction.collectIsPressedAsState()
    val closePressed by closeInteraction.collectIsPressedAsState()
    val holdingControls = dragging || restorePressed || togglePressed || closePressed
    val touchExploration = rememberTouchExplorationEnabled()
    val keepVisible = loading || touchExploration
    val focusManager = LocalFocusManager.current
    var controlsHaveFocus by remember { mutableStateOf(false) }

    fun interact() {
        controlsVisible = true
        interaction++
    }

    fun hideControls() {
        controlsVisible = false
        interaction++
    }

    fun controlAction(action: () -> Unit) {
        if (controlsVisible && !closing && !transitioning && miniTarget && visible) {
            interact()
            action()
        }
    }

    fun finishDrag() {
        if (closing || transitioning) return
        val start = horizontal
        val target = if (start < 0.5f) 0f else 1f
        dragging = false
        interaction++
        dockingJob?.cancel()
        dockingJob = scope.launch {
            dockingAnimation.snapTo(start)
            dockingAnimation.animateTo(target, dockingSpec) {
                horizontal = value.coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(miniTarget) {
        dockingJob?.cancel()
        if (miniTarget) interact()
    }
    LaunchedEffect(keepVisible, holdingControls, interaction, controlsVisible, miniTarget, transitioning, closing) {
        if (!miniTarget || transitioning || closing) return@LaunchedEffect
        if (keepVisible) controlsVisible = true
        else if (controlsVisible && !holdingControls) {
            delay(3.seconds)
            hideControls()
        }
    }
    LaunchedEffect(controlsVisible, closing, transitioning) {
        if ((!controlsVisible || closing || transitioning) && controlsHaveFocus) {
            focusManager.clearFocus(force = true)
        }
    }
    val controlsLabel =
        stringResource(if (controlsVisible) R.string.player_hide_controls else R.string.player_show_controls)
    val currentTransitioning by rememberUpdatedState(transitioning || backPreview != null)
    val currentLandedTap by rememberUpdatedState {
        if (miniTarget && !closing && visible) {
            if (keepVisible || !controlsVisible) interact() else hideControls()
        }
    }
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing.union(WindowInsets.ime)
    val window = LocalAppWindowLayout.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val windowCorners = remember(view, window.size, window.safeBounds, configuration) { view.windowCornerRadii() }
    val dockingPane = window.primaryPane
    val excludedBottom = dockingPane?.let { (window.size.height - it.bottom).coerceAtLeast(0) } ?: 0
    val safeBottom = (WindowInsets.safeDrawing.getBottom(density) - excludedBottom).coerceAtLeast(0)
    val keyboardBottom = (WindowInsets.ime.getBottom(density) - excludedBottom).coerceAtLeast(0)
    val extraBottom by animateDpAsState(
        targetValue = with(density) {
            if (keyboardBottom == 0) (mainNavigationSize.height - safeBottom).coerceAtLeast(0).toDp() else 0.dp
        },
        animationSpec = tween(durationMillis = 300, easing = emphasizedEasing),
        label = "MiniPlayerBottomBarInset",
    )
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(
        modifier
            // A press still routed to the old page when a card return starts must not reach its controls.
            .then(
                if (outgoing == null && (videoPageActive || exitingCard)) {
                    Modifier.pointerInput(viewportState, videoId) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val returningAtDown = viewportState.exitingVideoId == videoId
                            if (returningAtDown && currentExitingCard.value) return@awaitEachGesture
                            var block = returningAtDown
                            if (block) down.consume()
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                block = block || viewportState.exitingVideoId == videoId
                                if (block) event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                } else Modifier,
            )
            .then(if (passToPage) SharePointerInputWithSiblings else Modifier)
            .fillMaxSize()
            .graphicsLayer {
                val live = if (outgoing == null && !exitingCard) {
                    viewportState.navigationReturn?.invoke()
                        ?: viewportState.navigationPage?.takeIf { it.isTransitioning() }?.transform?.invoke()
                } else null
                // A return that starts mid page transition keeps the offset it was drawn at, then lets
                // it go with the flight, so it leaves from where it was and lands on its card.
                if (!exitingCard) returnTransform.value = live
                val transform = if (exitingCard) returnTransform.value?.fadedBy(cardProgress.value) else live
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = transform?.scale ?: 1f
                scaleY = transform?.scale ?: 1f
                translationX = transform?.x ?: 0f
                translationY = transform?.y ?: 0f
                alpha = transform?.alpha ?: 1f
                val radius = transform?.cornerRadius ?: 0f
                shape = RoundedCornerShape(radius)
                clip = radius > 0f
            },
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        val windowWidth = constraints.maxWidth
        val windowHeight = constraints.maxHeight
        val gap = with(density) { 8.dp.toPx() }
        val pane = dockingPane?.clampTo(windowWidth, windowHeight) ?: IntRect(0, 0, windowWidth, windowHeight)
        val rail = mainNavigationSize.width
        val ltr = layoutDirection == LayoutDirection.Ltr
        val left = maxOf(pane.left, insets.getLeft(density, layoutDirection), if (ltr) rail else 0) + gap
        val endInset = maxOf(insets.getRight(density, layoutDirection), if (ltr) 0 else rail)
        val rightEdge = minOf(pane.right, windowWidth - endInset) - gap
        val top = maxOf(insets.getTop(density), pane.top) + gap
        val bottomEdge = minOf(windowHeight - insets.getBottom(density), pane.bottom) - gap - with(density) {
            if (rail > 0 || keyboardBottom > 0) 0f else extraBottom.coerceAtLeast(0.dp).toPx()
        }
        val availableWidth = (rightEdge - left).coerceAtLeast(0f)
        val availableHeight = (bottomEdge - top).coerceAtLeast(0f)
        val hasDockSpace = with(density) { availableWidth >= 96.dp.toPx() && availableHeight >= 54.dp.toPx() }
        val widthPx = minOf(with(density) { 192.dp.toPx() }, availableWidth, availableHeight * 16f / 9f)
            .coerceAtLeast(1f)
        val heightPx = widthPx * 9f / 16f
        val maxX = (availableWidth - widthPx).coerceAtLeast(0f)
        val maxY = (availableHeight - heightPx).coerceAtLeast(0f)
        val width = with(density) { widthPx.toDp() }
        val height = with(density) { heightPx.toDp() }
        val buttonSize = minOf(48.dp, width / 3f, height)
        val iconSize = minOf(24.dp, buttonSize)
        val playbackIconSize = minOf(32.dp, buttonSize)
        val fullBounds = RectF(0f, 0f, windowWidth.toFloat(), windowHeight.toFloat())

        // A detached return is itself pushed back with the page, by [hostScale] about the window
        // centre, so window rectangles undo that once. The inverse is computed from the same scale
        // rather than read back from layout coordinates, which lag a frame when that layer is added.
        fun windowToHost(rect: RectF): RectF {
            val local = rect.translate(-viewportState.windowOrigin)
            val scale = hostScale()
            if (scale == 1f || scale <= 0f) return local
            val center = fullBounds.center
            return RectF(center + (local.topLeft - center) / scale, center + (local.bottomRight - center) / scale)
        }

        fun playerBounds(): RectF = pageViewport()?.takeIf { it.videoId == videoId }?.bounds
            ?: RectF(0f, 0f, windowWidth.toFloat(), windowWidth * 9f / 16f)

        fun liveCardBounds(): RectF? =
            origin?.let(viewportState.cards::liveBounds)?.also { lastCardBounds.value = it }

        // A card scrolled out of view leaves the flight where it was heading, fading along its path,
        // rather than snapping back to full size.
        fun returnTarget(): RectF? =
            ((if (sourceLeft) null else liveCardBounds()) ?: lastCardBounds.value)?.let(::windowToHost)

        fun sourceBounds(): RectF = when {
            origin == null -> fullBounds
            returningToCard -> returnTarget() ?: fullBounds
            // Reversing a return keeps flying from the card where it is now, not where it was tapped.
            else -> windowToHost(liveCardBounds() ?: origin.bounds)
        }

        fun miniBounds(): RectF {
            val miniX = left + horizontal * maxX
            val miniY = top + vertical * maxY
            return RectF(miniX, miniY, miniX + widthPx, miniY + heightPx)
        }

        fun miniProgress(): Float = when {
            navigatingPage -> 0f
            coveredMini -> if (miniTarget) 1f else 0f
            else -> zoom.value
        }

        fun viewportFrame(): RectF {
            val mini = miniProgress()
            if (mini == 1f) return miniBounds()
            val card = cardProgress.value
            val page = if (card == 1f) fullBounds else lerp(sourceBounds(), fullBounds, card)
            return if (mini == 0f) page else lerp(page, miniBounds(), mini)
        }

        fun viewportVisible(): Boolean = visible && (origin == null || pageViewport()?.videoId == videoId) &&
            (!miniTarget || hasDockSpace)

        fun hasCardBounds() = origin != null && (!returningToCard || returnTarget() != null)

        // A detached card return keeps restoring the background while the next video expands.
        if (!pip && !videoPageCovered && !imageViewerActive) {
            // Follow the expansion until the video fully covers the background. This must not depend on
            // the route: a return is requested a frame before its motion takes the push over.
            val followProgress by rememberUpdatedState {
                val card = cardProgress.value
                val mini = miniProgress()
                val cardPush = if (card > 0f && card < 1f && origin != null &&
                    (cardPreview == null || returnTarget() != null)
                ) card else 0f
                val miniPush = if (hasDockSpace && mini > 0f && mini < 1f) 1f - mini else 0f
                maxOf(cardPush, miniPush)
            }
            val pushShown by rememberUpdatedState {
                !closing && !navigatingPage && viewportVisible()
            }
            DisposableEffect(viewportState, videoId, backgroundPush) {
                backgroundPush.follow = { followProgress() }
                val push = VideoZoomPush(
                    strength = { if (pushShown()) backgroundPush.strength * returnPresence.value else 0f },
                    depth = { if (pushShown()) backgroundPush.depth * returnPresence.value else 0f },
                )
                viewportState.videoZoomPushes[videoId] = push
                onDispose {
                    if (viewportState.videoZoomPushes[videoId] === push) viewportState.videoZoomPushes.remove(videoId)
                }
            }
        }
        if (outgoing == null && !pip && !videoPageCovered && !imageViewerActive) {
            val frame by rememberUpdatedState {
                if (viewportVisible() && (hasCardBounds() || miniProgress() > 0f)) viewportFrame() else null
            }
            val coversSource by rememberUpdatedState {
                videoPageActive && !exitingCard && backPreview == null && !viewportState.navigatingPage &&
                    cardProgress.value == 1f && zoom.value == 0f && viewportVisible()
            }
            val sourceCoverage = remember { computedStateOf { coversSource() } }
            DisposableEffect(viewportState) {
                val readFrame = { frame() }
                viewportState.sourceRailFrame = readFrame
                viewportState.videoCoversSource = sourceCoverage
                onDispose {
                    if (viewportState.sourceRailFrame === readFrame) viewportState.sourceRailFrame = null
                    if (viewportState.videoCoversSource === sourceCoverage) viewportState.videoCoversSource = null
                }
            }
        }

        fun sourceScale(): Float {
            if (!hasCardBounds()) return 1f
            val source = sourceBounds()
            val player = playerBounds()
            return maxOf(source.width / player.width.coerceAtLeast(1f), source.height / player.height.coerceAtLeast(1f))
        }

        fun pageScale(): Float {
            val mini = miniProgress()
            val miniScale = widthPx / playerBounds().width.coerceAtLeast(1f)
            if (mini == 1f) return miniScale
            val startScale = sourceScale()
            val pageScale = startScale + (1f - startScale) * cardProgress.value
            return pageScale + (miniScale - pageScale) * mini
        }

        fun pageOffset(): Offset {
            val player = playerBounds()
            val mini = miniProgress()
            val miniScale = widthPx / player.width.coerceAtLeast(1f)
            if (mini == 1f) return -player.topLeft * miniScale
            val cardOffset = if (hasCardBounds()) {
                (sourceBounds().center - player.center * sourceScale()) * (1f - cardProgress.value)
            } else Offset.Zero
            val miniOffset = miniBounds().topLeft - player.topLeft * miniScale
            return lerp(cardOffset, miniOffset, mini) - viewportFrame().topLeft
        }

        val dragMaxX by rememberUpdatedState(maxX)
        val dragMaxY by rememberUpdatedState(maxY)
        Box(
            Modifier
                .absoluteOffset {
                    val bounds = viewportFrame()
                    IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
                }
                .layout { measurable, _ ->
                    val bounds = viewportFrame()
                    val child = measurable.measure(
                        Constraints.fixed(
                            if (visible) bounds.width.roundToInt().coerceAtLeast(1) else 0,
                            if (visible) bounds.height.roundToInt().coerceAtLeast(1) else 0,
                        ),
                    )
                    layout(child.width, child.height) { child.place(0, 0) }
                }
                .graphicsLayer {
                    val scale = if (miniTarget) closeScale.value else 1f
                    transformOrigin = TransformOrigin.Center
                    scaleX = scale
                    scaleY = scale
                    val card = cardProgress.value
                    val mini = miniProgress()
                    if (card == 1f && mini == 0f) {
                        // Settled on the page: the frame is the window itself, so there is no outline to
                        // clip or shade. The window radius it lands on is the display's own corners.
                        shape = RectangleShape
                        shadowElevation = 0f
                        clip = false
                    } else {
                        val sourceRadius = origin?.shape?.topStart?.toPx(origin.bounds.size, this) ?: 0f
                        val baseRadius = sourceRadius * (1f - card) * (1f - mini) + 8.dp.toPx() * mini
                        val windowWeight = if (pip || videoPageCovered) 0f else card * (1f - mini)
                        fun radius(windowRadius: Float) = (baseRadius + windowRadius * windowWeight).coerceAtLeast(0f)
                        shape = AbsoluteRoundedCornerShape(
                            topLeft = radius(windowCorners.topLeft),
                            topRight = radius(windowCorners.topRight),
                            bottomRight = radius(windowCorners.bottomRight),
                            bottomLeft = radius(windowCorners.bottomLeft),
                        )
                        // Lift away from the source, then land flush at either end of the card transition.
                        val lift = if (card > 0f && card < 1f && hasCardBounds()) 4f * card * (1f - card) else 0f
                        shadowElevation =
                            if (pip) 0f else 8.dp.toPx() * (lift + (1f - lift) * mini.coerceIn(0f, 1f))
                        clip = true
                    }
                    alpha = when {
                        !viewportVisible() -> 0f
                        // Fades along its path only once the card has scrolled away or was never found;
                        // a card rebuilt for a frame keeps the flight opaque toward where it was seen.
                        // Leaving the page dissolves through [returnPresence].
                        returningToCard && !sourceLeft && origin != null && liveCardBounds() == null &&
                            !viewportState.cards.awaitingCardLayout(origin) ->
                            cardProgress.value.coerceIn(0f, 1f)
                        origin == null || viewportState.cards.painter(origin) == null -> cardProgress.value.coerceIn(
                            0f,
                            1f,
                        )

                        else -> 1f
                    }
                    if (coveredMini && miniTarget) alpha *= miniNavigationAlpha
                    alpha *= returnPresence.value
                }
                .background(Color.Black)
                .pointerInput(outgoing, passToPage) {
                    if (outgoing != null || passToPage) return@pointerInput
                    // The moving page takes no input, but a tap on a return to the mini player catches
                    // it. Anything beyond touch slop stays a blocked drag, never an accidental restore.
                    // Slop is measured on the window, not on this frame: the frame flies away under a
                    // resting finger fastest right as the return begins, which would read as a drag.
                    // Not keyed on the transition: a press that outlives it must not lose its release.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (!currentTransitioning) return@awaitEachGesture
                        down.consume()
                        val downInWindow = down.position + viewportFrame().topLeft
                        var tap = currentEvent.changes.none { it.id != down.id && it.pressed }
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            // A cancelled gesture arrives as a release that is already consumed; check
                            // before consuming it here.
                            if ((change != null && !change.pressed && change.isConsumed) ||
                                event.changes.any { it.id != down.id && (it.pressed || it.previousPressed) }
                            ) tap = false
                            event.changes.forEach { it.consume() }
                            if (change == null) break
                            val moved = change.position + viewportFrame().topLeft - downInWindow
                            if (moved.getDistance() > viewConfiguration.touchSlop) tap = false
                            released = !change.pressed
                        }
                        when {
                            !tap || !released -> Unit
                            currentTransitioning -> if (currentCanRestore()) currentRestore()
                            // Landed while held; the controls never saw the press, so act as the mini
                            // player's own tap does.
                            else -> currentLandedTap()
                        }
                    }
                }
                .onGloballyPositioned {
                    if (outgoing == null && playback.videoId == videoId && (miniTarget || transitioning)) {
                        val bounds = it.boundsInWindow()
                        playback.updateBounds(
                            Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()),
                        )
                    }
                }
                .pointerInput(player, closing, miniTarget, transitioning, visible, hasDockSpace) {
                    if (closing || !miniTarget || transitioning || !visible || !hasDockSpace) return@pointerInput
                    try {
                        detectDragGestures(
                            onDragStart = { dockingJob?.cancel(); dragging = true },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() },
                        ) { change, delta ->
                            change.consume()
                            horizontal = if (dragMaxX > 0) {
                                (horizontal + delta.x / dragMaxX).coerceIn(0f, 1f)
                            } else 1f
                            vertical = if (dragMaxY > 0) {
                                (vertical + delta.y / dragMaxY).coerceIn(0f, 1f)
                            } else 1f
                        }
                    } finally {
                        dragging = false
                    }
                }
                .then(
                    if (miniTarget) Modifier
                        .semantics { contentDescription = controlsLabel }
                        .clickable(
                            enabled = !closing && !transitioning && visible && hasDockSpace,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = controlsLabel,
                        ) { if (keepVisible || !controlsVisible) interact() else hideControls() }
                    else Modifier,
                )
                .then(
                    if (closing || transitioning || !visible || imageViewerActive || (miniTarget && !hasDockSpace)) {
                        Modifier.clearAndSetSemantics { }
                    } else Modifier,
                ),
        ) {
            // Measure the page at window size for both destinations. Only its layer transform
            // and the outer clipping frame change, so the native video view is never reparented.
            pageContent(
                Modifier
                    .layout { measurable, constraints ->
                        val child = measurable.measure(Constraints.fixed(windowWidth, windowHeight))
                        layout(constraints.maxWidth, constraints.maxHeight) { child.place(0, 0) }
                    }
                    .graphicsLayer {
                        val settled = cardProgress.value == 1f && miniProgress() == 0f
                        val scale = if (settled) 1f else pageScale()
                        val offset = if (settled) Offset.Zero else pageOffset()
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                videoPageActive && !imageViewerActive && !exitingCard &&
                    backPreview == null && !transitioning && !navigatingPage && visible,
                visible,
            )
            // The card artwork cannot contribute once the mini player has landed. Skip its layer
            // and source geometry until a restore or a bounce brings it back into view.
            val artworkVisible by remember(cardProgress, zoom, detachedArtwork, navigatingPage, coveredMini, miniTarget) {
                derivedStateOf {
                    val mini = miniProgress()
                    mini < 1f && (cardProgress.value != 1f || mini != 0f || detachedArtwork.value != 0f)
                }
            }
            if (origin != null && artworkVisible) {
                Canvas(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            val progress = ((cardProgress.value - 0.15f) / 0.7f).coerceIn(0f, 1f)
                            val reveal = progress * progress * (3f - 2f * progress)
                            alpha = maxOf(1f - reveal, detachedArtwork.value) * (1f - miniProgress().coerceIn(0f, 1f))
                        }
                        .clearAndSetSemantics { },
                ) {
                    val painter = viewportState.cards.painter(origin) ?: return@Canvas
                    val artwork = lerp(
                        lerp(sourceBounds(), playerBounds(), cardProgress.value), miniBounds(), miniProgress(),
                    ).translate(-viewportFrame().topLeft)
                    if (artwork.width > 0f && artwork.height > 0f) translate(artwork.left, artwork.top) {
                        clipRect(0f, 0f, artwork.width, artwork.height) {
                            val intrinsic = painter.intrinsicSize
                            val sourceSize =
                                if (intrinsic.width > 0f && intrinsic.height > 0f) intrinsic else artwork.size
                            val factor = ContentScale.Crop.computeScaleFactor(sourceSize, artwork.size)
                            val drawnSize = Size(sourceSize.width * factor.scaleX, sourceSize.height * factor.scaleY)
                            translate(
                                (artwork.width - drawnSize.width) / 2f,
                                (artwork.height - drawnSize.height) / 2f,
                            ) {
                                with(painter) { draw(drawnSize) }
                            }
                        }
                    }
                }
            }
            if (passToPage) {
                // Stops hit testing here, so the flying page's own controls never see a tap meant for
                // the page behind; it consumes nothing, so the page behind still handles it.
                Box(Modifier.matchParentSize().pointerInput(Unit) {})
            }
            if (miniTarget && !closing && !transitioning && !controlsVisible && visible) {
                // Intercept taps while controls fade out so revealing them cannot trigger an action.
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .clearAndSetSemantics { }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { interact() },
                )
            }
            if (miniTarget || transitioning) CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides buttonSize,
                LocalRippleConfiguration provides RippleConfiguration(color = Color.White),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (navigatingPage) 0f else controlsAlpha
                        }
                        .then(if (!controlsVisible) Modifier.clearAndSetSemantics { } else Modifier)
                        .focusProperties {
                            onEnter = { if (!controlsVisible || closing || transitioning) cancelFocusChange() }
                        }
                        .onFocusChanged { controlsHaveFocus = it.hasFocus }
                        .focusGroup()
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    IconButton(
                        onClick = { controlAction(onRestore) },
                        enabled = !closing && !transitioning && visible && hasDockSpace,
                        colors = iconColors,
                        interactionSource = restoreInteraction,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(buttonSize),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_icon_fullscreen),
                            restoreLabel,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                    IconButton(
                        onClick = { controlAction(playback::toggle) },
                        enabled = !closing && !transitioning && visible && hasDockSpace,
                        colors = iconColors,
                        interactionSource = toggleInteraction,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(buttonSize)
                            .semantics {
                                if (loading) contentDescription = toggleLabel
                            },
                    ) {
                        if (loading) {
                            IndeterminateCircularProgressIndicator(
                                modifier = Modifier.size(iconSize),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            AnimatedPlaybackIcon(
                                motion = iconMotion,
                                description = toggleLabel,
                                modifier = Modifier.size(playbackIconSize),
                            )
                        }
                    }
                    IconButton(
                        onClick = { controlAction { closeRequested = true } },
                        enabled = !closing && !transitioning && visible && hasDockSpace,
                        colors = iconColors,
                        interactionSource = closeInteraction,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(buttonSize),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_icon_close),
                            stringResource(R.string.player_mini_close),
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }
        }
    }
}

private class LastBounds(var value: RectF?)

private class LastTransform {
    var value: NavigationPageTransform? = null
}

/** Weighs [this] by [weight]: all of it at 1, none of it at 0. */
private fun NavigationPageTransform.fadedBy(weight: Float): NavigationPageTransform {
    val w = weight.coerceIn(0f, 1f)
    return NavigationPageTransform(
        scale = 1f + (scale - 1f) * w,
        x = x * w,
        y = y * w,
        alpha = 1f + (alpha - 1f) * w,
        cornerRadius = cornerRadius * w,
    )
}

/**
 * Lets hit testing continue to the siblings drawn below this host. Compose otherwise stops at the
 * topmost sibling that is hit, and the host covers the window.
 */
private object SharePointerInputWithSiblings : ModifierNodeElement<SharePointerInputWithSiblingsNode>() {
    override fun create() = SharePointerInputWithSiblingsNode()
    override fun update(node: SharePointerInputWithSiblingsNode) = Unit
    override fun hashCode() = javaClass.hashCode()
    override fun equals(other: Any?) = other === this
}

private class SharePointerInputWithSiblingsNode : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) = Unit
    override fun onCancelPointerInput() = Unit
    override fun sharePointerInputWithSiblings() = true
}

private fun Context.playerActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.playerActivity()
    else -> null
}

/** Stiffness of a unit-mass spring with a 0.4 s response, `(2π / response)²`, as SwiftUI and UIKit define it. */
private const val VIDEO_ZOOM_EXPAND_STIFFNESS = 438.6f
