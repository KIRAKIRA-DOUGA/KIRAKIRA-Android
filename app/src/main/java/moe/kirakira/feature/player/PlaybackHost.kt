package moe.kirakira.feature.player

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.kirakira.MainActivity
import moe.kirakira.R
import moe.kirakira.data.settings.PlaybackSettings
import moe.kirakira.ui.components.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.rememberEmphasizedEasing
import androidx.compose.ui.geometry.Rect as RectF

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

@Composable
internal fun PlaybackHost(
    playback: PlaybackViewModel,
    videoId: Int,
    viewportState: PlaybackViewportState,
    settings: PlaybackSettings?,
    videoPageActive: Boolean,
    imageViewerActive: Boolean,
    videoPageCovered: Boolean,
    bottomBarHeightPx: Int,
    onRestore: () -> Unit,
    onCardExitFinished: () -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (Modifier, Boolean, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.playerActivity() }
    val view = LocalView.current
    val pip = activity?.pictureInPicture == true
    var horizontal by rememberSaveable { mutableFloatStateOf(1f) }
    var vertical by rememberSaveable { mutableFloatStateOf(1f) }
    LaunchedEffect(settings?.autoQuality, settings?.preferredVideoHeight) {
        settings?.let { playback.setQualityPreference(it.autoQuality, it.preferredVideoHeight) }
    }

    LaunchedEffect(videoPageActive) {
        if (!videoPageActive) activity?.setVideoFullscreen(false)
    }
    DisposableEffect(activity, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) playback.flushHistory()
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true &&
                activity?.isInPictureInPictureMode != true) {
                playback.closeMiniPlayer()
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            activity?.updatePictureInPicture(false, null)
            view.keepScreenOn = false
        }
    }
    LaunchedEffect(pip) {
        if (!pip && activity != null && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            playback.closeMiniPlayer()
        }
    }
    DisposableEffect(playback.playing, view) {
        view.keepScreenOn = playback.playing
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(
        playback.player, playback.playing, playback.failed, playback.bounds, playback.actualVideoHeight,
        settings?.outsideAppMiniPlayer, videoPageActive, playback.miniPlayer, pip,
    ) {
        val size = playback.player?.videoSize
        val ratio = if (size != null && size.width > 0 && size.height > 0) {
            size.width.toFloat() * size.pixelWidthHeightRatio / size.height
        } else 16f / 9f
        activity?.updatePictureInPicture(
            eligible = (videoPageActive || playback.miniPlayer) && playback.player != null && !playback.failed,
            bounds = playback.bounds,
            aspectRatio = ratio,
            autoEnter = playback.playing && settings?.outsideAppMiniPlayer == true,
        )
    }
    val player = playback.player
    val exitingCard = viewportState.exitingVideoId != null
    val navigatingPage = viewportState.navigatingPage
    val coveredMini = videoPageCovered && !videoPageActive
    val miniTarget = playback.miniPlayer && !videoPageActive && !pip && !exitingCard && !navigatingPage
    val visible = (videoPageActive || videoPageCovered || playback.miniPlayer || pip || exitingCard) && !imageViewerActive
    val origin = viewportState.cards.origin?.takeIf { it.videoId == videoId }
    val cardProgress = remember(origin) { Animatable(if (origin != null && videoPageActive) 0f else 1f) }
    val currentCardExitFinished by rememberUpdatedState(onCardExitFinished)
    val backProgress = viewportState.backProgress
    val zoom = remember(origin) { Animatable(if (miniTarget) 1f else 0f) }
    val emphasizedEasing = rememberEmphasizedEasing()
    fun zoomSpec(expanding: Boolean) = if (expanding) tween<Float>(
        durationMillis = 450,
        easing = emphasizedEasing,
    ) else spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 380f,
        visibilityThreshold = 0.001f,
    )
    LaunchedEffect(miniTarget, pip, backProgress, navigatingPage, coveredMini) {
        // Ordinary navigation owns the whole page. The native video stays in this tree;
        // switch to/from its floating presentation only while the covering page obscures it.
        if (pip || navigatingPage) zoom.snapTo(0f)
        else if (coveredMini) zoom.snapTo(if (miniTarget) 1f else 0f)
        else if (backProgress != null && viewportState.backToMiniPlayer) zoom.snapTo(backProgress)
        else {
            val target = if (miniTarget) 1f else 0f
            if (zoom.value != target) {
                zoom.animateTo(target, zoomSpec(expanding = !miniTarget))
            }
        }
    }
    LaunchedEffect(origin, videoId, videoPageActive, exitingCard, backProgress, pip) {
        when {
            pip -> cardProgress.snapTo(1f)
            backProgress != null && !viewportState.backToMiniPlayer -> cardProgress.snapTo(1f - backProgress)
            exitingCard -> {
                if (cardProgress.value != 0f) {
                    cardProgress.animateTo(0f, zoomSpec(expanding = false))
                }
                currentCardExitFinished()
            }
            videoPageActive -> {
                if (origin != null && cardProgress.value == 0f) {
                    // Keep the real source on screen until this video's full-size page is laid out.
                    snapshotFlow { viewportState.pageViewport }
                        .first { it?.videoId == videoId && it.bounds.width > 0 && it.bounds.height > 0 }
                    withFrameNanos { }
                }
                if (cardProgress.value != 1f) {
                    cardProgress.animateTo(1f, zoomSpec(expanding = true))
                }
            }
        }
    }
    val transitioning by remember(miniTarget, exitingCard, cardProgress) {
        derivedStateOf {
            zoom.isRunning || zoom.value != (if (miniTarget) 1f else 0f) || cardProgress.isRunning ||
                cardProgress.value != (if (exitingCard) 0f else 1f)
        }
    }
    val iconMotion = rememberPlaybackIconMotion(playback.showPauseIcon)
    val iconColors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
    val restoreLabel = stringResource(R.string.player_mini_restore)
    val toggleLabel = stringResource(if (playback.showPauseIcon) R.string.player_pause else R.string.player_play)
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
    val visibility = remember { Animatable(1f) }
    var closing by remember(player) { mutableStateOf(false) }
    LaunchedEffect(player) {
        visibility.animateTo(1f, dockingSpec)
    }
    val scope = rememberCoroutineScope()
    var dockingJob by remember { mutableStateOf<Job?>(null) }
    val restoreInteraction = remember { MutableInteractionSource() }
    val toggleInteraction = remember { MutableInteractionSource() }
    val closeInteraction = remember { MutableInteractionSource() }
    val restorePressed by restoreInteraction.collectIsPressedAsState()
    val togglePressed by toggleInteraction.collectIsPressedAsState()
    val closePressed by closeInteraction.collectIsPressedAsState()
    val holdingControls = dragging || restorePressed || togglePressed || closePressed
    val touchExploration = rememberTouchExplorationEnabled()
    val keepVisible = playback.buffering || playback.initialLoading || touchExploration
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
    LaunchedEffect(keepVisible, holdingControls, interaction, controlsVisible, miniTarget, transitioning) {
        if (!miniTarget || transitioning) return@LaunchedEffect
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
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing.union(WindowInsets.ime)
    val safeBottom = WindowInsets.safeDrawing.getBottom(density)
    val keyboardBottom = WindowInsets.ime.getBottom(density)
    val extraBottom by animateDpAsState(
        targetValue = with(density) {
            if (keyboardBottom == 0) (bottomBarHeightPx - safeBottom).coerceAtLeast(0).toDp() else 0.dp
        },
        animationSpec = tween(durationMillis = 300, easing = emphasizedEasing),
        label = "MiniPlayerBottomBarInset",
    )
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(
        modifier.fillMaxSize().graphicsLayer {
            val transform = viewportState.navigationReturn?.invoke()
                ?: viewportState.navigationPage?.takeIf { it.isTransitioning() }?.transform?.invoke()
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
    ) {
        val windowWidth = constraints.maxWidth
        val windowHeight = constraints.maxHeight
        val gap = with(density) { 8.dp.toPx() }
        val left = insets.getLeft(density, layoutDirection) + gap
        val right = insets.getRight(density, layoutDirection) + gap
        val top = insets.getTop(density) + gap
        val bottom = insets.getBottom(density) + gap + with(density) { extraBottom.coerceAtLeast(0.dp).toPx() }
        val availableWidth = (windowWidth - left - right).coerceAtLeast(0f)
        val availableHeight = (windowHeight - top - bottom).coerceAtLeast(0f)
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

        fun playerBounds(): RectF = viewportState.pageViewport?.takeIf { it.videoId == videoId }?.bounds
            ?: RectF(0f, 0f, windowWidth.toFloat(), windowWidth * 9f / 16f)

        fun sourceBounds(): RectF = origin?.let {
            val bounds = if (exitingCard || backProgress != null) viewportState.cards.bounds(it) else it.bounds
            bounds.translate(-viewportState.windowOrigin)
        } ?: fullBounds

        fun miniBounds(): RectF {
            val miniX = left + horizontal * maxX
            val miniY = top + vertical * maxY
            return RectF(miniX, miniY, miniX + widthPx, miniY + heightPx)
        }

        fun miniProgress(): Float = when {
            viewportState.navigatingPage -> 0f
            coveredMini -> if (miniTarget) 1f else 0f
            else -> zoom.value
        }

        fun viewportFrame(): RectF = lerp(lerp(sourceBounds(), fullBounds, cardProgress.value), miniBounds(), miniProgress())

        fun frameBounds(): RectF {
            val bounds = viewportFrame()
            val exitX = if (horizontal < 0.5f) -(widthPx + gap * 3f) else windowWidth + gap * 3f
            return bounds.translate(Offset((exitX - bounds.left) * (1f - visibility.value) * miniProgress(), 0f))
        }

        fun sourceScale(): Float {
            if (origin == null) return 1f
            val source = sourceBounds()
            val player = playerBounds()
            return maxOf(source.width / player.width.coerceAtLeast(1f), source.height / player.height.coerceAtLeast(1f))
        }

        fun pageScale(): Float {
            val startScale = sourceScale()
            val pageScale = startScale + (1f - startScale) * cardProgress.value
            val miniScale = widthPx / playerBounds().width.coerceAtLeast(1f)
            return pageScale + (miniScale - pageScale) * miniProgress()
        }

        fun pageOffset(): Offset {
            val player = playerBounds()
            val cardOffset = if (origin != null) {
                (sourceBounds().center - player.center * sourceScale()) * (1f - cardProgress.value)
            } else Offset.Zero
            val miniScale = widthPx / player.width.coerceAtLeast(1f)
            val miniOffset = miniBounds().topLeft - player.topLeft * miniScale
            return lerp(cardOffset, miniOffset, miniProgress()) - viewportFrame().topLeft
        }

        val dragMaxX by rememberUpdatedState(maxX)
        val dragMaxY by rememberUpdatedState(maxY)
        Box(
            Modifier.offset {
                val bounds = frameBounds()
                IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
            }
                .layout { measurable, _ ->
                    val bounds = frameBounds()
                    val child = measurable.measure(
                        Constraints.fixed(
                            if (visible) bounds.width.roundToInt().coerceAtLeast(1) else 0,
                            if (visible) bounds.height.roundToInt().coerceAtLeast(1) else 0,
                        ),
                    )
                    layout(child.width, child.height) { child.place(0, 0) }
                }
                .graphicsLayer {
                    val sourceRadius = origin?.shape?.topStart?.toPx(origin.bounds.size, this) ?: 0f
                    val radius = sourceRadius * (1f - cardProgress.value) * (1f - miniProgress()) + 8.dp.toPx() * miniProgress()
                    shape = RoundedCornerShape(radius.coerceAtLeast(0f))
                    shadowElevation = 8.dp.toPx() * miniProgress().coerceIn(0f, 1f)
                    clip = true
                    val pageMeasured = viewportState.pageViewport?.videoId == videoId
                    alpha = when {
                        !visible || (origin != null && !pageMeasured) -> 0f
                        origin == null || viewportState.cards.painter(origin) == null -> cardProgress.value.coerceIn(0f, 1f)
                        else -> 1f
                    }
                    if (coveredMini && miniTarget) alpha *= miniNavigationAlpha
                }
                .background(Color.Black)
                .pointerInput(transitioning) {
                    if (!transitioning) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
                .onGloballyPositioned {
                    if (miniTarget || transitioning) {
                        val bounds = it.boundsInWindow()
                        playback.updateBounds(
                            Rect(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()),
                        )
                    }
                }
                .pointerInput(player, closing, miniTarget, transitioning, visible) {
                    if (closing || !miniTarget || transitioning || !visible) return@pointerInput
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
                    } finally { dragging = false }
                }
                .then(
                    if (miniTarget) Modifier.semantics { contentDescription = controlsLabel }
                        .clickable(
                            enabled = !closing && !transitioning && visible,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = controlsLabel,
                        ) { if (keepVisible || !controlsVisible) interact() else hideControls() }
                    else Modifier,
                )
                .then(
                    if (closing || transitioning || !visible) Modifier.clearAndSetSemantics { } else Modifier,
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
                        val scale = pageScale()
                        val offset = pageOffset()
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                videoPageActive && !transitioning && !navigatingPage && visible,
                visible,
            )
            if (origin != null) {
                Canvas(
                    Modifier.matchParentSize().graphicsLayer {
                        val progress = ((cardProgress.value - 0.15f) / 0.7f).coerceIn(0f, 1f)
                        val reveal = progress * progress * (3f - 2f * progress)
                        alpha = (1f - reveal) * (1f - miniProgress().coerceIn(0f, 1f))
                    }.clearAndSetSemantics { },
                ) {
                    val painter = viewportState.cards.painter(origin) ?: return@Canvas
                    val artwork = lerp(
                        lerp(sourceBounds(), playerBounds(), cardProgress.value), miniBounds(), miniProgress(),
                    ).translate(-viewportFrame().topLeft)
                    if (artwork.width > 0f && artwork.height > 0f) translate(artwork.left, artwork.top) {
                        clipRect(0f, 0f, artwork.width, artwork.height) {
                            val intrinsic = painter.intrinsicSize
                            val sourceSize = if (intrinsic.width > 0f && intrinsic.height > 0f) intrinsic else artwork.size
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
            if (miniTarget && !transitioning && !controlsVisible && visible) {
                // Intercept taps while controls fade out so revealing them cannot trigger an action.
                Box(
                    Modifier.fillMaxSize().zIndex(1f).clearAndSetSemantics { }
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
                    Modifier.fillMaxSize()
                        .graphicsLayer { alpha = if (viewportState.navigatingPage) 0f else controlsAlpha }
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
                        enabled = !closing && !transitioning && visible,
                        colors = iconColors,
                        interactionSource = restoreInteraction,
                        modifier = Modifier.align(Alignment.TopStart).size(buttonSize),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_symbol_fullscreen),
                            restoreLabel,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                    IconButton(
                        onClick = { controlAction(playback::toggle) },
                        enabled = !closing && !transitioning && visible,
                        colors = iconColors,
                        interactionSource = toggleInteraction,
                        modifier = Modifier.align(Alignment.Center).size(buttonSize).semantics {
                            if (playback.buffering || playback.initialLoading) contentDescription = toggleLabel
                        },
                    ) {
                        if (playback.buffering || playback.initialLoading) {
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
                        onClick = {
                            controlAction {
                                closing = true
                                dockingJob?.cancel()
                                playback.pause()
                                scope.launch {
                                    visibility.animateTo(0f, closeSpec)
                                    if (playback.player === player && playback.miniPlayer) {
                                        playback.closeMiniPlayer()
                                    }
                                }
                            }
                        },
                        enabled = !closing && !transitioning && visible,
                        colors = iconColors,
                        interactionSource = closeInteraction,
                        modifier = Modifier.align(Alignment.TopEnd).size(buttonSize),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_symbol_close),
                            stringResource(R.string.player_mini_close),
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }
        }
    }
}

private fun Context.playerActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.playerActivity()
    else -> null
}
