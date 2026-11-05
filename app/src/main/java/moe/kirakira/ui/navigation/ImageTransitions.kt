package moe.kirakira.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlin.math.abs
import moe.kirakira.ui.components.mediaZoomSpec
import moe.kirakira.ui.components.rememberEmphasizedEasing

internal fun imageTransitionSpec(easing: Easing, expanding: Boolean): FiniteAnimationSpec<Float> =
    mediaZoomSpec(expanding, easing, visibilityThreshold = 0.001f)

internal val LocalImageSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalImageTransitionSizes = compositionLocalOf<ImageTransitionSizes?> { null }

internal class ImageTransitionSizes {
    private val sourceSizes = mutableStateMapOf<String, Size>()
    private val sourceContentScales = mutableStateMapOf<String, Map<Any, ContentScale>>()
    private val circularSources = mutableMapOf<String, MutableSet<Any>>()
    private val viewerSizes = mutableStateMapOf<String, Size>()
    private val participants = mutableMapOf<String, MutableMap<Any, Boolean>>()
    private val drawableViewers = mutableStateMapOf<String, Map<Any, Boolean>>()

    fun attach(
        key: String,
        participant: Any,
        viewer: Boolean,
        circularSource: Boolean,
        sourceContentScale: ContentScale?,
    ) {
        participants.getOrPut(key) { mutableMapOf() }[participant] = viewer
        if (!viewer && circularSource) circularSources.getOrPut(key) { mutableSetOf() }.add(participant)
        if (!viewer && sourceContentScale != null) {
            sourceContentScales[key] = sourceContentScales[key].orEmpty() + (participant to sourceContentScale)
        }
    }

    fun drawable(key: String, participant: Any, drawable: Boolean) {
        val previous = drawableViewers[key].orEmpty()
        if (previous[participant] != drawable) drawableViewers[key] = previous + (participant to drawable)
    }

    fun viewerCanDraw(key: String): Boolean = drawableViewers[key]?.values?.any { it } == true

    fun release(key: String, participant: Any) {
        val remaining = participants[key] ?: return
        remaining.remove(participant)
        circularSources[key]?.let { sources ->
            sources.remove(participant)
            if (sources.isEmpty()) circularSources.remove(key)
        }
        val drawable = drawableViewers[key].orEmpty() - participant
        if (drawable.isEmpty()) drawableViewers.remove(key) else drawableViewers[key] = drawable
        val scales = sourceContentScales[key].orEmpty() - participant
        if (scales.isEmpty()) sourceContentScales.remove(key) else sourceContentScales[key] = scales
        if (remaining.values.none { it } || remaining.values.none { !it }) {
            sourceSizes.remove(key)
            viewerSizes.remove(key)
        }
        if (remaining.isEmpty()) participants.remove(key)
    }

    fun record(key: String, initial: Rect, target: Rect) {
        val initialIsSource = initial.width * initial.height <= target.width * target.height
        val source = if (initialIsSource) initial else target
        val viewer = if (initialIsSource) target else initial
        sourceSizes[key] = source.size
        viewerSizes[key] = viewer.size
    }

    fun roundness(key: String, bounds: Rect, viewer: Boolean): Float {
        if (circularSources[key].isNullOrEmpty()) return 0f
        val source = sourceSizes[key] ?: return if (viewer) 0f else 1f
        val target = viewerSizes[key] ?: return if (viewer) 0f else 1f
        val fraction = sizeProgress(source, target, bounds.size) ?: return if (viewer) 0f else 1f
        // Hold the circular silhouette until the bounds have opened substantially.
        return 1f - fraction * fraction
    }

    fun contentScale(key: String, srcSize: Size, dstSize: Size, restingContentScale: ContentScale): ScaleFactor {
        val fallback = restingContentScale.computeScaleFactor(srcSize, dstSize)
        val sourceScale = sourceContentScales[key]?.values?.firstOrNull()
            ?: return fallback
        val source = sourceSizes[key] ?: return fallback
        val target = viewerSizes[key] ?: return fallback
        val fraction = sizeProgress(source, target, dstSize) ?: return fallback
        val start = sourceScale.computeScaleFactor(srcSize, source)
        val end = ContentScale.Fit.computeScaleFactor(srcSize, target)
        // Use the same geometry as the shared bounds, including reverse and predictive motion.
        return ScaleFactor(lerp(start.scaleX, end.scaleX, fraction), lerp(start.scaleY, end.scaleY, fraction))
    }

    private fun sizeProgress(source: Size, target: Size, current: Size): Float? {
        val widthChange = target.width - source.width
        val heightChange = target.height - source.height
        val distanceSquared = widthChange * widthChange + heightChange * heightChange
        if (distanceSquared == 0f) return null
        val progress = (
            (current.width - source.width) * widthChange +
                (current.height - source.height) * heightChange
            ) / distanceSquared
        return progress.coerceIn(0f, 1f)
    }
}

/** Match a source's fill at its bounds, then continuously reveal the full image in the viewer. */
@Composable
internal fun rememberImageContentScale(
    key: String?,
    restingContentScale: ContentScale = ContentScale.Fit,
): ContentScale {
    val sharedScope = LocalImageSharedScope.current ?: return restingContentScale
    val sizes = LocalImageTransitionSizes.current ?: return restingContentScale
    if (key == null) return restingContentScale
    return remember(key, sharedScope, sizes, restingContentScale) {
        object : ContentScale {
            override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor =
                if (sharedScope.isTransitionActive) sizes.contentScale(key, srcSize, dstSize, restingContentScale)
                else restingContentScale.computeScaleFactor(srcSize, dstSize)
        }
    }
}

/** Bounds and the zoom layer are separate: closing never resets Telephoto's current transform. */
@Composable
internal fun Modifier.imageSharedBounds(
    key: String?,
    viewer: Boolean,
    drawable: Boolean = false,
    circularSource: Boolean = true,
    sourceContentScale: ContentScale? = null,
): Modifier {
    val easing = rememberEmphasizedEasing()
    val sharedScope = LocalImageSharedScope.current ?: return this
    if (key == null) return this
    val sizes = LocalImageTransitionSizes.current ?: return this
    val participant = remember(key, viewer) { Any() }
    DisposableEffect(key, participant, sizes, circularSource, sourceContentScale) {
        sizes.attach(key, participant, viewer, circularSource, sourceContentScale)
        onDispose { sizes.release(key, participant) }
    }
    SideEffect { if (viewer) sizes.drawable(key, participant, drawable) }
    val visibility = LocalNavAnimatedContentScope.current
    val overlayClip = remember(key, viewer, sizes) {
        object : SharedTransitionScope.OverlayClip {
            private val path = Path()

            override fun getClipPath(
                sharedContentState: SharedTransitionScope.SharedContentState,
                bounds: Rect,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Path {
                val radius = minOf(bounds.width, bounds.height) * sizes.roundness(key, bounds, viewer) / 2f
                path.reset()
                path.addRoundRect(
                    RoundRect(0f, 0f, bounds.width, bounds.height, CornerRadius(radius)),
                )
                path.translate(bounds.topLeft)
                return path
            }
        }
    }
    return with(sharedScope) {
        val state = rememberSharedContentState(key)
        this@imageSharedBounds
            .sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = visibility,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                boundsTransform = { initial, target ->
                    sizes.record(key, initial, target)
                    // Match the video's normalized 0.001 settling threshold in pixel coordinates.
                    mediaZoomSpec(
                        expanding = target.width * target.height > initial.width * initial.height,
                        easing = easing,
                        visibilityThreshold = Rect(
                            abs(target.left - initial.left).coerceAtLeast(1f) * 0.001f,
                            abs(target.top - initial.top).coerceAtLeast(1f) * 0.001f,
                            abs(target.right - initial.right).coerceAtLeast(1f) * 0.001f,
                            abs(target.bottom - initial.bottom).coerceAtLeast(1f) * 0.001f,
                        ),
                    )
                },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = overlayClip,
                zIndexInOverlay = if (viewer) 1f else 0f,
            )
            .graphicsLayer {
                // The overlay owns the clipping during a match; this layer only clips at rest.
                // Its radius follows the animated bounds, including predictive return seeking.
                val movingInOverlay = state.isMatchFound && isTransitionActive
                shape = if (viewer || !circularSource) RectangleShape else CircleShape
                clip = !movingInOverlay
                // Match lifetime outlasts overlay rendering. Restore the source on the same
                // frame the overlay stops drawing, rather than waiting for viewer disposal.
                alpha = if (!viewer && movingInOverlay && sizes.viewerCanDraw(key)) 0f else 1f
            }
    }
}

@Composable
internal fun rememberImageVisibility(): State<Float> {
    val easing = rememberEmphasizedEasing()
    return LocalNavAnimatedContentScope.current.transition.animateFloat(
        transitionSpec = { imageTransitionSpec(easing, expanding = targetState == EnterExitState.Visible) },
        label = "image visibility",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
}

/** Keep viewer controls above the moving image while its bounds occupy the shared overlay. */
@Composable
internal fun Modifier.imageControlsOverlay(): Modifier {
    val easing = rememberEmphasizedEasing()
    val sharedScope = LocalImageSharedScope.current ?: return this
    val visibility = LocalNavAnimatedContentScope.current
    return with(sharedScope) {
        with(visibility) {
            this@imageControlsOverlay
                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 2f)
                .animateEnterExit(
                    enter = fadeIn(imageTransitionSpec(easing, expanding = true)),
                    exit = fadeOut(imageTransitionSpec(easing, expanding = false)),
                )
        }
    }
}

/** An unanchored image opens around its own center, without inventing a shared source. */
@Composable
internal fun Modifier.imageStandaloneMotion(): Modifier {
    val easing = rememberEmphasizedEasing()
    val scale = LocalNavAnimatedContentScope.current.transition.animateFloat(
        transitionSpec = { imageTransitionSpec(easing, expanding = targetState == EnterExitState.Visible) },
        label = "standalone image scale",
    ) {
        when (it) {
            EnterExitState.PreEnter -> 0.92f
            EnterExitState.Visible -> 1f
            EnterExitState.PostExit -> 0.96f
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
