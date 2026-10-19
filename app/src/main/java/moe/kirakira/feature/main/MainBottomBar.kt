package moe.kirakira.feature.main

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import moe.kirakira.R
import moe.kirakira.ui.components.TabIndicatorLeadingEasing
import moe.kirakira.ui.components.TabIndicatorTrailingEasing
import moe.kirakira.ui.components.frostedBarBackground
import moe.kirakira.ui.components.rememberEmphasizedEasing
import moe.kirakira.ui.components.tabTransitionDurationMillis
import moe.kirakira.ui.theme.BarShadowElevation
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LightSelectedBackgroundAlpha = 0.05f
private const val DarkSelectedBackgroundAlpha = 0.1f
private val BarContentPadding = 4.dp

internal enum class AppDestination(
    @param:StringRes val label: Int,
    val icon: MainTabIcon,
) {
    HOME(R.string.nav_home, MainTabIcon.HOME),
    SEARCH(R.string.nav_search, MainTabIcon.SEARCH),
    FOLLOWING(R.string.nav_following, MainTabIcon.FOLLOWING),
    ME(R.string.nav_me, MainTabIcon.ME),
}

@Composable
internal fun MainBottomBar(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val latestDestination by rememberUpdatedState(destination)
    val latestOnDestinationChange by rememberUpdatedState(onDestinationChange)
    // Pointer samples are consumed only by drawing; composition observes drag start/end instead.
    val dragPosition = remember { mutableFloatStateOf(Float.NaN) }
    var isDragging by remember { mutableStateOf(false) }
    // Drag release and cancellation settle without starting a new Elastic click animation.
    var dragSettlement by remember { mutableStateOf<AppDestination?>(null) }
    val indicatorStart = remember { Animatable(destination.ordinal.toFloat(), visibilityThreshold = 0.001f) }
    val indicatorEnd = remember { Animatable(destination.ordinal.toFloat(), visibilityThreshold = 0.001f) }
    val emphasizedEasing = rememberEmphasizedEasing()
    val leadingEasing = remember(emphasizedEasing) {
        Easing { TabIndicatorLeadingEasing.transform(emphasizedEasing.transform(it)) }
    }
    val trailingEasing = remember(emphasizedEasing) {
        Easing { TabIndicatorTrailingEasing.transform(emphasizedEasing.transform(it)) }
    }
    LaunchedEffect(destination, isDragging, dragSettlement, leadingEasing, trailingEasing) {
        if (isDragging) return@LaunchedEffect

        val settlement = dragSettlement
        val handoffPosition = dragPosition.floatValue
        if (!handoffPosition.isNaN()) {
            // Keep drawing the final pointer sample until both animation edges own that position.
            indicatorStart.snapTo(handoffPosition)
            indicatorEnd.snapTo(handoffPosition)
            if (isDragging) return@LaunchedEffect
            dragPosition.floatValue = Float.NaN
        }
        val targetPosition = destination.ordinal.toFloat()
        val stretch = settlement != destination
        if (indicatorStart.value != targetPosition || indicatorEnd.value != targetPosition) {
            val currentPosition = (indicatorStart.value + indicatorEnd.value) / 2f
            val forward = targetPosition >= currentPosition
            val duration = tabTransitionDurationMillis(abs(targetPosition - currentPosition))
            val dragSpec = spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
            coroutineScope {
                launch {
                    indicatorStart.animateTo(
                        targetValue = targetPosition,
                        animationSpec = if (stretch) {
                            tween(duration, easing = if (forward) trailingEasing else leadingEasing)
                        } else {
                            dragSpec
                        },
                    )
                }
                launch {
                    indicatorEnd.animateTo(
                        targetValue = targetPosition,
                        animationSpec = if (stretch) {
                            tween(duration, easing = if (forward) leadingEasing else trailingEasing)
                        } else {
                            dragSpec
                        },
                    )
                }
            }
        }
        if (!isDragging && dragSettlement == settlement) dragSettlement = null
    }
    val colorScheme = MaterialTheme.colorScheme
    val indicatorColor = if (colorScheme.surface.luminance() < colorScheme.onSurface.luminance()) {
        colorScheme.onSurface.copy(alpha = DarkSelectedBackgroundAlpha)
    } else {
        colorScheme.scrim.copy(alpha = LightSelectedBackgroundAlpha)
    }
    val selectedContentColor = colorScheme.primary
    val contentColor = colorScheme.onSurface

    val gestures = Modifier.pointerInput(layoutDirection) {
        try {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                val padding = BarContentPadding.toPx()
                val itemWidth = (size.width - padding * 2) / AppDestination.entries.size
                if (itemWidth <= 0f) return@awaitEachGesture

                fun logicalX(x: Float): Float =
                    if (layoutDirection == LayoutDirection.Rtl) size.width - x else x

                val downX = logicalX(down.position.x)
                val startIndex = ((downX - padding) / itemWidth).toInt()
                    .coerceIn(0, AppDestination.entries.lastIndex)
                down.consume()
                var dragging = false
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val cancelled = change.isConsumed ||
                            event.changes.any { it.id != down.id && it.pressed } ||
                            change.position.y < -viewConfiguration.touchSlop ||
                            change.position.y > size.height + viewConfiguration.touchSlop
                        event.changes.forEach { it.consume() }
                        if (cancelled) break

                        val distance = logicalX(change.position.x) - downX
                        if (!dragging && abs(distance) > viewConfiguration.touchSlop) {
                            dragging = true
                            isDragging = true
                            dragSettlement = null
                        }
                        val position = (startIndex + distance / itemWidth)
                            .coerceIn(0f, AppDestination.entries.lastIndex.toFloat())
                        if (dragging) dragPosition.floatValue = position
                        if (!change.pressed) {
                            val target = AppDestination.entries[if (dragging) position.roundToInt() else startIndex]
                            dragSettlement = if (dragging) target else null
                            released = true
                            if (target != latestDestination) latestOnDestinationChange(target)
                            break
                        }
                    }
                } finally {
                    if (dragging) {
                        if (!released) {
                            dragSettlement = latestDestination
                        }
                        isDragging = false
                    }
                }
            }
        } finally {
            // Also restore the committed selection when the bar leaves composition or RTL changes.
            if (isDragging) {
                dragSettlement = latestDestination
                isDragging = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .matchParentSize()
                    .frostedBarBackground(
                        shape = CircleShape,
                        shadowElevation = BarShadowElevation,
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .selectableGroup()
                    .then(gestures)
                    .drawWithCache {
                        val padding = BarContentPadding.toPx()
                        val itemWidth = ((size.width - padding * 2) / AppDestination.entries.size)
                            .coerceAtLeast(0f)
                        val height = (size.height - padding * 2).coerceAtLeast(0f)
                        val foregroundBounds = Rect(Offset.Zero, size)
                        val foregroundPaint = Paint()
                        val mask = Path()
                        onDrawWithContent {
                            val pointerPosition = dragPosition.floatValue
                            val start = (if (pointerPosition.isNaN()) indicatorStart.value else pointerPosition)
                                .coerceIn(0f, AppDestination.entries.lastIndex.toFloat())
                            val end = (if (pointerPosition.isNaN()) indicatorEnd.value else pointerPosition)
                                .coerceIn(start, AppDestination.entries.lastIndex.toFloat())
                            val logicalStart = padding + start * itemWidth
                            val width = (end - start + 1f) * itemWidth
                            val left = if (layoutDirection == LayoutDirection.Rtl) {
                                size.width - logicalStart - width
                            } else {
                                logicalStart
                            }
                            mask.reset()
                            mask.addRoundRect(
                                RoundRect(
                                    rect = Rect(left, padding, left + width, padding + height),
                                    cornerRadius = CornerRadius(minOf(width, height) / 2),
                                ),
                            )
                            drawPath(path = mask, color = indicatorColor)

                            // Isolate foreground alpha so the tint affects only glyphs under the pill.
                            val canvas = drawContext.canvas
                            canvas.saveLayer(foregroundBounds, foregroundPaint)
                            try {
                                drawContent()
                                clipPath(mask) {
                                    drawRect(color = selectedContentColor, blendMode = BlendMode.SrcAtop)
                                }
                            } finally {
                                canvas.restore()
                            }
                        }
                    }
                    .padding(BarContentPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppDestination.entries.forEach { item ->
                    val selected = destination == item
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 56.dp)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = {
                                    if (!selected) {
                                        dragSettlement = null
                                        onDestinationChange(item)
                                    }
                                },
                            )
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .testTag("nav_${item.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides contentColor) {
                            AnimatedTabIcon(icon = item.icon, selected = selected)
                            Text(
                                text = stringResource(item.label),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
