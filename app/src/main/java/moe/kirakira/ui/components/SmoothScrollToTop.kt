package moe.kirakira.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyLayoutScrollScope as lazyListScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyLayoutScrollScope as lazyGridScrollScope
import androidx.compose.foundation.lazy.layout.LazyLayoutScrollScope
import kotlin.math.roundToInt

private const val MIN_SCROLL_TO_TOP_MILLIS = 600
private const val MILLIS_PER_VIEWPORT = 100
private const val MAX_DURATION_VIEWPORTS = 6f

/** Shared motion for status-bar, bottom-navigation and tab-reselect scroll-to-top actions. */
internal suspend fun LazyListState.smoothScrollToTop(header: ScrollToTopHeader? = null) {
    if (!canScrollBackward) {
        header?.smoothExpand()
        return
    }
    scroll {
        lazyListScrollScope(this@smoothScrollToTop, this).smoothScrollToTop(layoutInfo.viewportSize.height, header)
    }
}

internal suspend fun LazyGridState.smoothScrollToTop() {
    if (!canScrollBackward) return
    scroll {
        lazyGridScrollScope(this@smoothScrollToTop, this).smoothScrollToTop(layoutInfo.viewportSize.height, null)
    }
}

internal suspend fun ScrollState.smoothScrollToTop(header: ScrollToTopHeader? = null) {
    if (value == 0 && header?.canExpand() != true) return
    scroll {
        animateScrollToTop(value.toFloat(), viewportSize, header) { remainingFraction ->
            scrollBy(-value * remainingFraction)
        }
    }
}

internal suspend fun ScrollToTopHeader.smoothExpand() {
    if (canExpand()) animateScrollToTop(0f, 0, this) {}
}

private suspend fun LazyLayoutScrollScope.smoothScrollToTop(viewportHeight: Int, header: ScrollToTopHeader?) {
    val distance = -calculateDistanceTo(0).toFloat()
    animateScrollToTop(distance, viewportHeight, header) { remainingFraction ->
        // Re-evaluate variable-height items as they enter the viewport. Scroll through the path
        // without teleporting to an intermediate item before the animation starts.
        scrollBy(calculateDistanceTo(0) * remainingFraction)
        if (remainingFraction == 1f) snapToItem(0)
    }
}

/** The same eased progress moves both the body and its optional collapsing header. */
private suspend fun animateScrollToTop(
    bodyDistance: Float,
    viewportHeight: Int,
    header: ScrollToTopHeader?,
    scrollBody: (Float) -> Unit,
) {
    val initialHeaderDistance = header?.remainingDistance() ?: 0f
    val viewports = if (viewportHeight > 0) {
        ((bodyDistance + initialHeaderDistance) / viewportHeight).coerceIn(0f, MAX_DURATION_VIEWPORTS)
    } else {
        0f
    }
    val durationMillis = MIN_SCROLL_TO_TOP_MILLIS + (viewports * MILLIS_PER_VIEWPORT).roundToInt()
    var previousProgress = 0f
    animate(0f, 1f, animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)) { value, _ ->
        val progress = value.coerceIn(previousProgress, 1f)
        val remainingFraction = if (previousProgress < 1f) {
            ((progress - previousProgress) / (1f - previousProgress)).coerceIn(0f, 1f)
        } else {
            0f
        }
        scrollBody(remainingFraction)
        if (header != null) {
            val targetHeaderDistance = initialHeaderDistance * (1f - progress)
            header.scrollBy((header.remainingDistance() - targetHeaderDistance).coerceAtLeast(0f))
        }
        previousProgress = progress
    }
    header?.onExpanded()
}
