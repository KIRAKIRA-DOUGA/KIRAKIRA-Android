package moe.kirakira.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val ADJACENT_TAB_SCROLL_MILLIS = 500
private const val EXTRA_PAGE_SCROLL_MILLIS = 150

@Composable
internal fun rememberTabChangeHandler(
    pagerState: PagerState,
    scope: CoroutineScope = rememberCoroutineScope(),
): (Int) -> Unit {
    val easing = rememberEmphasizedEasing()
    var tabScrollJob by remember { mutableStateOf<Job?>(null) }
    return remember(pagerState, scope, easing) {
        { targetPage ->
            if (
                pagerState.currentPage != targetPage ||
                pagerState.isScrollInProgress ||
                tabScrollJob?.isActive == true
            ) {
                tabScrollJob?.cancel()
                tabScrollJob = scope.launch {
                    val distance = abs(
                        targetPage - (pagerState.currentPage + pagerState.currentPageOffsetFraction),
                    )
                    val durationMillis = ADJACENT_TAB_SCROLL_MILLIS +
                        (EXTRA_PAGE_SCROLL_MILLIS * (distance - 1f).coerceIn(0f, 1f)).toInt()
                    pagerState.animateScrollToPage(
                        page = targetPage,
                        animationSpec = tween(durationMillis, easing = easing),
                    )
                }
            }
        }
    }
}
