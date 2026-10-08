package moe.kirakira.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import moe.kirakira.ui.components.rememberEmphasizedEasing

internal const val IMAGE_TRANSITION_MILLIS = 420
internal fun <T> imageTransitionSpec(easing: Easing): TweenSpec<T> =
    tween(durationMillis = IMAGE_TRANSITION_MILLIS, easing = easing)

internal val LocalImageSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalImageTransitionSizes = compositionLocalOf<ImageTransitionSizes?> { null }

internal class ImageTransitionSizes {
    private val sourceSizes = mutableMapOf<String, Size>()
    private val viewerSizes = mutableMapOf<String, Size>()
    private val participants = mutableMapOf<String, MutableMap<Any, Boolean>>()
    private val drawableViewers = mutableStateMapOf<String, Map<Any, Boolean>>()

    fun attach(key: String, participant: Any, viewer: Boolean) {
        participants.getOrPut(key) { mutableMapOf() }[participant] = viewer
    }

    fun drawable(key: String, participant: Any, drawable: Boolean) {
        val previous = drawableViewers[key].orEmpty()
        if (previous[participant] != drawable) drawableViewers[key] = previous + (participant to drawable)
    }

    fun viewerCanDraw(key: String): Boolean = drawableViewers[key]?.values?.any { it } == true

    fun release(key: String, participant: Any) {
        val remaining = participants[key] ?: return
        remaining.remove(participant)
        val drawable = drawableViewers[key].orEmpty() - participant
        if (drawable.isEmpty()) drawableViewers.remove(key) else drawableViewers[key] = drawable
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
        val source = sourceSizes[key] ?: return if (viewer) 0f else 1f
        val target = viewerSizes[key] ?: return if (viewer) 0f else 1f
        val widthChange = target.width - source.width
        val heightChange = target.height - source.height
        val distanceSquared = widthChange * widthChange + heightChange * heightChange
        if (distanceSquared == 0f) return if (viewer) 0f else 1f
        val progress = (
            (bounds.width - source.width) * widthChange +
                (bounds.height - source.height) * heightChange
            ) / distanceSquared
        val fraction = progress.coerceIn(0f, 1f)
        // Hold the circular silhouette until the bounds have opened substantially.
        return 1f - fraction * fraction
    }
}

/** Bounds and the zoom layer are separate: closing never resets Telephoto's current transform. */
@Composable
internal fun Modifier.imageSharedBounds(key: String?, viewer: Boolean, drawable: Boolean = false): Modifier {
    val easing = rememberEmphasizedEasing()
    val sharedScope = LocalImageSharedScope.current ?: return this
    if (key == null) return this
    val sizes = LocalImageTransitionSizes.current ?: return this
    val participant = remember(key, viewer) { Any() }
    DisposableEffect(key, participant, sizes) {
        sizes.attach(key, participant, viewer)
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
                    imageTransitionSpec(easing)
                },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = overlayClip,
                zIndexInOverlay = if (viewer) 1f else 0f,
            )
            .graphicsLayer {
                // The overlay owns the clipping during a match; this layer only clips at rest.
                // Its radius follows the animated bounds, including predictive return seeking.
                val movingInOverlay = state.isMatchFound && isTransitionActive
                shape = if (viewer) RectangleShape else CircleShape
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
        transitionSpec = { imageTransitionSpec(easing) },
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
                    enter = fadeIn(imageTransitionSpec(easing)),
                    exit = fadeOut(imageTransitionSpec(easing)),
                )
        }
    }
}
