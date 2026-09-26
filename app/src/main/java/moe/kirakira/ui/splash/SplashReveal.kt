package moe.kirakira.ui.splash

import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.kirakira.R

private object SplashRevealMotion {
    const val SHRINK_SCALE = 0.92f
    const val REVEAL_DURATION_MULTIPLIER = 1.5f
    const val BACKGROUND_FADE_START_PROGRESS = 0.65f

    // A circle wholly inside the star in ic_splash_foreground's 108-unit viewport,
    // after its 0.75 group transform about (52, 58). Keep aligned with that artwork.
    val starCenter = Offset(43f, 61.75f)
    const val STAR_INNER_RADIUS = 3.75f
}

@Composable
internal fun SplashReveal(
    info: SplashRevealInfo,
    onOverlayDrawn: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shrinkScale = remember(info) { Animatable(1f) }
    val revealProgress = remember(info) { Animatable(0f) }
    val iconAlpha = remember(info) { Animatable(1f) }
    val backgroundAlpha = remember(info) { Animatable(1f) }
    val shrinkSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val revealSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val firstFrame = remember(info) { CompletableDeferred<Unit>() }
    val currentOnOverlayDrawn by rememberUpdatedState(onOverlayDrawn)
    val currentOnFinished by rememberUpdatedState(onFinished)
    var initialBounds by remember(info) { mutableStateOf<Rect?>(null) }
    var currentBounds by remember(info) { mutableStateOf<Rect?>(null) }
    var windowSize by remember(info) { mutableStateOf(info.windowSize) }
    val view = LocalView.current

    LaunchedEffect(info) {
        try {
            firstFrame.await()
            // The draw callback completes during recording. Let that frame finish before
            // removing the system view; the replacement is already opaque underneath it.
            withFrameNanos { }
            if (ValueAnimator.areAnimatorsEnabled()) {
                currentOnOverlayDrawn()
                // Finish each spatial spring at its first target crossing, before it
                // rebounds. In particular, the reveal must never cover the page again.
                shrinkScale.updateBounds(lowerBound = SplashRevealMotion.SHRINK_SCALE, upperBound = 1f)
                revealProgress.updateBounds(lowerBound = 0f, upperBound = 1f)
                shrinkScale.animateTo(
                    targetValue = SplashRevealMotion.SHRINK_SCALE,
                    animationSpec = shrinkSpec,
                )
                // A full-window reveal needs more time than a component's spatial motion.
                // Multiply the inherited scale, including live changes and disabled motion.
                val systemDurationScale = coroutineContext[MotionDurationScale]
                val revealDurationScale = object : MotionDurationScale {
                    override val scaleFactor: Float
                        get() = (systemDurationScale?.scaleFactor ?: 1f) *
                            SplashRevealMotion.REVEAL_DURATION_MULTIPLIER
                }
                withContext(revealDurationScale) {
                    val iconFade = launch { iconAlpha.animateTo(0f, fadeSpec) }
                    val backgroundFade = launch {
                        snapshotFlow { revealProgress.value }.first {
                            it >= SplashRevealMotion.BACKGROUND_FADE_START_PROGRESS
                        }
                        backgroundAlpha.animateTo(0f, fadeSpec)
                    }
                    revealProgress.animateTo(1f, revealSpec)
                    iconFade.join()
                    // The star now contains the entire window and has no remaining fill.
                    // Do not keep intercepting input while an invisible fade settles.
                    backgroundFade.cancel()
                }
            }
        } finally {
            currentOnFinished()
        }
    }

    LaunchedEffect(currentBounds, windowSize) {
        if (currentBounds != initialBounds || windowSize != info.windowSize) {
            currentOnFinished()
        }
    }

    SplashRevealFrame(
        info = info,
        originInWindow = currentBounds?.topLeft,
        shrinkScale = { shrinkScale.value },
        revealProgress = { revealProgress.value },
        iconAlpha = { iconAlpha.value },
        backgroundAlpha = { backgroundAlpha.value },
        onDrawn = { firstFrame.complete(Unit) },
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val bounds = Rect(
                    coordinates.positionInWindow(),
                    Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                )
                if (initialBounds == null) initialBounds = bounds
                currentBounds = bounds
                windowSize = IntSize(view.rootView.width, view.rootView.height)
            }
            .pointerInput(info) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .clearAndSetSemantics { },
    )
}

@Composable
private fun SplashRevealFrame(
    info: SplashRevealInfo,
    originInWindow: Offset?,
    shrinkScale: () -> Float,
    revealProgress: () -> Float,
    iconAlpha: () -> Float,
    backgroundAlpha: () -> Float,
    onDrawn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vector = ImageVector.vectorResource(R.drawable.ic_splash_foreground)
    val paths = remember(vector) { flattenSplashPaths(vector.root) }
    Canvas(
        modifier = modifier.graphicsLayer {
            // Erasing affects this window-sized layer only, never the underlying app.
            compositingStrategy = CompositingStrategy.Offscreen
            alpha = backgroundAlpha().coerceIn(0f, 1f)
        },
    ) {
        drawRect(info.backgroundColor)
        if (originInWindow == null) return@Canvas

        val foreground = info.foregroundBoundsInWindow.translate(-originInWindow)
        val scaleX = foreground.width / vector.viewportWidth
        val scaleY = foreground.height / vector.viewportHeight
        val pivot = Offset(
            foreground.left + SplashRevealMotion.starCenter.x * scaleX,
            foreground.top + SplashRevealMotion.starCenter.y * scaleY,
        )
        val radius = SplashRevealMotion.STAR_INNER_RADIUS * minOf(scaleX, scaleY)
        val farthestCorner = maxOf(
            (Offset.Zero - pivot).getDistance(),
            (Offset(size.width, 0f) - pivot).getDistance(),
            (Offset(0f, size.height) - pivot).getDistance(),
            (Offset(size.width, size.height) - pivot).getDistance(),
        )
        val endScale = maxOf(1f, farthestCorner / radius * 1.05f)
        val initialScale = shrinkScale()
        // Interpolate magnification in log space: halfway means the geometric mean,
        // not half of a potentially enormous final scale. This keeps the early logo legible.
        val scale = initialScale * (endScale / initialScale).pow(revealProgress().coerceIn(0f, 1f))
        val fillAlpha = iconAlpha().coerceIn(0f, 1f)

        withTransform({
            scale(scale, scale, pivot)
            translate(foreground.left, foreground.top)
            scale(scaleX, scaleY, Offset.Zero)
        }) {
            paths.forEach { path ->
                drawPath(path, info.iconColor)
                // Fade from an opaque colored logo to a hole, including its background.
                // Keeping the initial frame opaque also preserves antialiased edges at handoff.
                if (fillAlpha < 1f) {
                    drawPath(path, Color.Black, alpha = 1f - fillAlpha, blendMode = BlendMode.DstOut)
                }
            }
        }
        onDrawn()
    }
}

/** The bundled logo contains only filled paths and transformed groups, without strokes or clips. */
private fun flattenSplashPaths(group: VectorGroup, parent: Matrix = Matrix()): List<Path> = buildList {
    val local = Matrix().apply {
        postTranslate(-group.pivotX, -group.pivotY)
        postScale(group.scaleX, group.scaleY)
        postRotate(group.rotation)
        postTranslate(group.pivotX + group.translationX, group.pivotY + group.translationY)
    }
    val transform = Matrix(parent).apply { preConcat(local) }
    group.forEach { node ->
        when (node) {
            is VectorGroup -> addAll(flattenSplashPaths(node, transform))
            is VectorPath -> add(
                PathParser().addPathNodes(node.pathData).toPath().apply {
                    fillType = node.pathFillType
                    asAndroidPath().transform(transform)
                },
            )
        }
    }
}

@Preview(name = "Splash reveal", widthDp = 360, heightDp = 800)
@Preview(name = "Splash reveal dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SplashRevealPreview() {
    val backgroundColor = colorResource(R.color.splash_background)
    val iconColor = colorResource(R.color.splash_icon)
    val density = LocalDensity.current
    // A static intermediate frame; no Activity or live startup controller is needed.
    Box(Modifier.fillMaxSize().background(Color.Gray)) {
        SplashRevealFrame(
            info = SplashRevealInfo(
                foregroundBoundsInWindow = with(density) {
                    Rect(72.dp.toPx(), 292.dp.toPx(), 288.dp.toPx(), 508.dp.toPx())
                },
                windowSize = with(density) { IntSize(360.dp.roundToPx(), 800.dp.roundToPx()) },
                backgroundColor = backgroundColor,
                iconColor = iconColor,
                darkTheme = false,
            ),
            originInWindow = Offset.Zero,
            shrinkScale = { SplashRevealMotion.SHRINK_SCALE },
            revealProgress = { 0.3f },
            iconAlpha = { 0f },
            backgroundAlpha = { 1f },
            onDrawn = { },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
