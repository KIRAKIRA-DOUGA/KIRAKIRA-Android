package moe.kirakira.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import moe.kirakira.R

@Composable
internal fun ContentPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indicatorTopPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    var retainRefreshingIndicator by remember { mutableStateOf(isRefreshing) }
    LaunchedEffect(isRefreshing, state) {
        if (isRefreshing) {
            retainRefreshingIndicator = true
        } else {
            snapshotFlow { state.distanceFraction == 0f && !state.isAnimating }.first { it }
            retainRefreshingIndicator = false
        }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        enabled = enabled,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.IndicatorBox(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = indicatorTopPadding).graphicsLayer {
                    val completing = retainRefreshingIndicator && !isRefreshing
                    val exitProgress = if (completing) state.distanceFraction.coerceIn(0f, 1f) else 1f
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    scaleX = exitProgress
                    scaleY = exitProgress
                    alpha = exitProgress
                },
                containerColor = Color.White,
                elevation = PullToRefreshDefaults.Elevation,
            ) {
                Crossfade(
                    targetState = isRefreshing || retainRefreshingIndicator,
                    animationSpec = tween(durationMillis = REFRESH_CROSSFADE_DURATION_MILLIS),
                    label = "RefreshIndicator",
                ) { refreshing ->
                    if (refreshing) {
                        IndeterminateCircularProgressIndicator(
                            modifier = Modifier.size(RefreshSpinnerSize),
                            strokeWidth = RefreshStrokeWidth,
                        )
                    } else {
                        PullRefreshArrowIndicator(
                            progress = { state.distanceFraction },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(RefreshSpinnerSize),
                        )
                    }
                }
            }
        },
        content = content,
    )
}

@Composable
private fun PullRefreshArrowIndicator(
    progress: () -> Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (progress() >= 1f) 1f else REFRESH_MIN_ALPHA,
        animationSpec = tween(durationMillis = REFRESH_ALPHA_DURATION_MILLIS, easing = LinearEasing),
        label = "RefreshArrowAlpha",
    )
    val arrow = painterResource(R.drawable.ic_symbol_chevron_right)
    val tint = remember(color) { ColorFilter.tint(color) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier.progressSemantics(progress().coerceIn(0f, 1f))) {
            val pullProgress = progress().coerceAtLeast(0f)
            val adjustedProgress = ((pullProgress.coerceAtMost(1f) - 0.4f).coerceAtLeast(0f) * 5f / 3f)
            if (adjustedProgress == 0f) return@Canvas
            val linearTension = (pullProgress - 1f).coerceIn(0f, 2f)
            val tension = linearTension - linearTension * linearTension / 4f
            val rotation = (-0.25f + 0.4f * adjustedProgress + tension) * 0.5f
            val startAngle = rotation * 360f
            val sweepAngle = adjustedProgress * REFRESH_MAX_PROGRESS_ARC * 360f
            val strokeWidth = RefreshStrokeWidth.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val arrowSize = RefreshArrowSize.toPx() * adjustedProgress
            rotate(degrees = rotation) {
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    alpha = alpha,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                withTransform({
                    rotate(degrees = startAngle + sweepAngle)
                    translate(left = center.x + radius, top = center.y)
                    rotate(degrees = 90f, pivot = Offset.Zero)
                    translate(left = -arrowSize / 2f, top = -arrowSize / 2f)
                }) {
                    with(arrow) {
                        draw(size = Size(arrowSize, arrowSize), alpha = alpha, colorFilter = tint)
                    }
                }
            }
        }
    }
}

private const val REFRESH_CROSSFADE_DURATION_MILLIS = 100
private const val REFRESH_ALPHA_DURATION_MILLIS = 300
private const val REFRESH_MIN_ALPHA = 0.3f
private const val REFRESH_MAX_PROGRESS_ARC = 0.8f
private val RefreshSpinnerSize = 20.dp
private val RefreshStrokeWidth = 2.5.dp
private val RefreshArrowSize = 18.dp
