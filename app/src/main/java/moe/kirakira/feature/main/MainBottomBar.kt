package moe.kirakira.feature.main

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
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
import moe.kirakira.R
import moe.kirakira.ui.components.awaitSlidingSelectionChange
import moe.kirakira.ui.components.frostedBarBackground
import moe.kirakira.ui.components.rememberSlidingSelection
import moe.kirakira.ui.components.rememberSlidingSelectionPressProgress
import moe.kirakira.ui.components.slidingSelectionIndicator
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
    onDestinationReselect: (AppDestination) -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val latestDestination by rememberUpdatedState(destination)
    val latestOnDestinationChange by rememberUpdatedState(onDestinationChange)
    val latestOnDestinationReselect by rememberUpdatedState(onDestinationReselect)
    val interactionSources = AppDestination.entries.map { remember { MutableInteractionSource() } }
    val tabPressStates = interactionSources.map { it.collectIsPressedAsState() }
    val motion = rememberSlidingSelection(destination.ordinal)
    val pressProgress = rememberSlidingSelectionPressProgress(
        motion.pressedPosition != null || tabPressStates.any { it.value },
    )
    val colorScheme = MaterialTheme.colorScheme
    val indicatorColor = if (colorScheme.surface.luminance() < colorScheme.onSurface.luminance()) {
        colorScheme.onSurface.copy(alpha = DarkSelectedBackgroundAlpha)
    } else {
        colorScheme.scrim.copy(alpha = LightSelectedBackgroundAlpha)
    }
    val selectedContentColor = colorScheme.primary
    val contentColor = colorScheme.onSurface

    val gestures = Modifier.pointerInput(layoutDirection, motion) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val padding = BarContentPadding.toPx()
            val itemWidth = (size.width - padding * 2) / AppDestination.entries.size
            if (itemWidth <= 0f) return@awaitEachGesture

            fun logicalX(x: Float): Float =
                if (layoutDirection == LayoutDirection.Rtl) size.width - x else x

            fun positionFor(x: Float): Float =
                ((logicalX(x) - padding) / itemWidth - 0.5f)
                    .coerceIn(0f, AppDestination.entries.lastIndex.toFloat())

            val downX = logicalX(down.position.x)
            val startIndex = ((downX - padding) / itemWidth).toInt()
                .coerceIn(0, AppDestination.entries.lastIndex)
            down.consume()
            val pressedPosition = if (startIndex == latestDestination.ordinal) startIndex.toFloat()
            else positionFor(down.position.x)
            motion.press(pressedPosition)
            var settlement: Int? = null
            try {
                while (true) {
                    val change = awaitSlidingSelectionChange(down.id, consumeEvent = true) ?: break

                    val position = positionFor(change.position.x)
                    if (motion.isDragging || abs(logicalX(change.position.x) - downX) > viewConfiguration.touchSlop) {
                        motion.dragTo(position)
                    }
                    if (!change.pressed) {
                        val dragging = motion.isDragging
                        val target = AppDestination.entries[if (dragging) position.roundToInt() else startIndex]
                        settlement = target.ordinal
                        if (target != latestDestination) {
                            latestOnDestinationChange(target)
                        } else if (!dragging) {
                            latestOnDestinationReselect(target)
                        }
                        break
                    }
                }
            } finally {
                motion.release(settlement ?: latestDestination.ordinal)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
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
                    .slidingSelectionIndicator(
                        shape = CircleShape,
                        bounds = {
                            val padding = BarContentPadding.toPx()
                            val itemWidth = ((size.width - padding * 2) / AppDestination.entries.size)
                                .coerceAtLeast(0f)
                            motion.bounds(AppDestination.entries.lastIndex, pressProgress.value) { position ->
                                val start = padding + position * itemWidth
                                val left = if (layoutDirection == LayoutDirection.Rtl) {
                                    size.width - start - itemWidth
                                } else start
                                Rect(left, padding, left + itemWidth, size.height - padding)
                            }
                        },
                        containerColor = {
                            indicatorColor.copy(alpha = indicatorColor.alpha * (1f + 0.5f * pressProgress.value))
                        },
                        contentColor = selectedContentColor,
                    )
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
                                interactionSource = interactionSources[item.ordinal],
                                indication = ripple(),
                                onClick = {
                                    if (!selected) {
                                        motion.clearSettlement()
                                        onDestinationChange(item)
                                    } else {
                                        onDestinationReselect(item)
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
