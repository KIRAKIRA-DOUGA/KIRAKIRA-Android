package moe.kirakira.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val ADJACENT_TAB_SCROLL_MILLIS = 500
private const val EXTRA_PAGE_SCROLL_MILLIS = 150

internal val TabIndicatorLeadingEasing = Easing { sin(it * PI / 2).toFloat() }
internal val TabIndicatorTrailingEasing = Easing { (1 - cos(it * PI / 2)).toFloat() }

internal fun tabTransitionDurationMillis(distance: Float): Int =
    ADJACENT_TAB_SCROLL_MILLIS +
        (EXTRA_PAGE_SCROLL_MILLIS * (distance - 1f).coerceIn(0f, 1f)).toInt()

@Composable
internal fun rememberTabChangeHandler(
    pagerState: PagerState,
    scope: CoroutineScope = rememberCoroutineScope(),
    onTabReselect: suspend (Int) -> Unit = {},
): (Int) -> Unit {
    val reselectTab = rememberTabReselectHandler(onTabReselect)
    val easing = rememberEmphasizedEasing()
    var tabScrollJob by remember { mutableStateOf<Job?>(null) }
    return remember(pagerState, scope, easing, reselectTab) {
        { targetPage ->
            if (
                pagerState.currentPage != targetPage ||
                pagerState.isScrollInProgress ||
                tabScrollJob?.isActive == true
            ) {
                reselectTab(null)
                tabScrollJob?.cancel()
                tabScrollJob = scope.launch {
                    val distance = abs(
                        targetPage - (pagerState.currentPage + pagerState.currentPageOffsetFraction),
                    )
                    val durationMillis = tabTransitionDurationMillis(distance)
                    pagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = tween(durationMillis, easing = easing),
                    )
                }
            } else {
                reselectTab(targetPage)
            }
        }
    }
}

/** Runs only the latest reselect action; null cancels it when changing tabs. */
@Composable
internal fun rememberTabReselectHandler(
    onTabReselect: suspend (Int) -> Unit,
): (Int?) -> Unit {
    val scope = rememberCoroutineScope()
    val latestOnTabReselect by rememberUpdatedState(onTabReselect)
    var reselectJob by remember { mutableStateOf<Job?>(null) }
    return remember(scope) {
        { page ->
            reselectJob?.cancel()
            reselectJob = if (page == null) null else scope.launch { latestOnTabReselect(page) }
        }
    }
}

/** The indicator compresses around its center while the selected tab is held. */
@Composable
internal fun rememberTabIndicatorWidthScale(pressed: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (pressed) 0.85f else 1f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    label = "Tab indicator press width",
)
