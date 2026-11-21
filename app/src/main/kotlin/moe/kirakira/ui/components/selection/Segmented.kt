package moe.kirakira.ui.components.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import moe.kirakira.ui.components.button.rememberButtonShadowElevation
import moe.kirakira.ui.components.form.textFieldInnerShadow
import moe.kirakira.ui.components.list.connectedListItemShapes
import moe.kirakira.ui.theme.ThemeColorDefaults
import moe.kirakira.ui.theme.materialColorShadow

internal val SegmentedItemHorizontalPadding = 8.dp

/** Single choice from distinct [items]. A null or absent [selected] value leaves every segment unselected. */
@Composable
fun <T : Any> Segmented(
    items: List<T>,
    selected: T?,
    onSelectedChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    parentContainerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    val selectedIndex = items.indexOf(selected).takeIf { it >= 0 }
    val itemBounds = remember(items) { mutableStateMapOf<Int, Rect>() }
    val interactionSources = remember(items) { items.map { MutableInteractionSource() } }
    val pressStates = interactionSources.map { it.collectIsPressedAsState() }
    val hoverStates = interactionSources.map { it.collectIsHoveredAsState() }
    val focusStates = interactionSources.map { it.collectIsFocusedAsState() }
    val motion = rememberSlidingSelection(selectedIndex)
    val pressed = enabled && (motion.pressedPosition != null || pressStates.any { it.value })
    val pressProgress = rememberSlidingSelectionPressProgress(pressed)
    val shadowElevation by rememberButtonShadowElevation(
        enabled = enabled,
        pressed = pressed,
        hoveredOrFocused = selectedIndex != null &&
            (hoverStates[selectedIndex].value || focusStates[selectedIndex].value),
    )
    val latestOnSelectedChange by rememberUpdatedState(onSelectedChange)
    val layoutDirection = LocalLayoutDirection.current
    val shape = connectedListItemShapes(0, 1).shape
    val colors = MaterialTheme.colorScheme

    fun indicatorBounds(): Rect? {
        return motion.bounds(items.lastIndex, pressProgress.value) { position ->
            val index = position.toInt()
            val start = itemBounds[index] ?: return@bounds null
            val end = itemBounds[index + 1] ?: return@bounds start
            lerp(start, end, position - index)
        }
    }

    val drag = if (enabled) {
        Modifier.pointerInput(items, selectedIndex, layoutDirection, motion) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            fun indexAt(x: Float): Int? = items.indices.firstOrNull { index ->
                itemBounds[index]?.let { x >= it.left && x < it.right } == true
            }

            fun positionAt(x: Float): Float {
                val slot = indexAt(x) ?: return if ((x < 0f) != rtl) 0f else items.lastIndex.toFloat()
                val bounds = itemBounds.getValue(slot)
                val offset = (x - bounds.center.x) / bounds.width * if (rtl) -1f else 1f
                return (slot + offset).coerceIn(0f, items.lastIndex.toFloat())
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val startIndex = indexAt(down.position.x) ?: return@awaitEachGesture
                if (down.isConsumed || itemBounds[startIndex]?.contains(down.position) != true) return@awaitEachGesture

                val pressedPosition = if (startIndex == selectedIndex) startIndex.toFloat()
                else positionAt(down.position.x)
                motion.press(pressedPosition, fromEmptySelection = selectedIndex == null)
                var settlement = selectedIndex
                try {
                    while (true) {
                        val change = awaitSlidingSelectionChange(down.id) ?: break

                        if (!change.pressed) {
                            if (motion.isDragging) {
                                change.consume()
                                motion.updatePointerPreview(positionAt(change.position.x))
                                val index = indexAt(change.position.x)
                                if (index != null && itemBounds[index]?.contains(change.position) == true) {
                                    settlement = index
                                    if (index != selectedIndex) latestOnSelectedChange(items[index])
                                }
                            }
                            break
                        }

                        if (!motion.isDragging) {
                            val delta = change.position - down.position
                            val horizontal = abs(delta.x)
                            val vertical = abs(delta.y)
                            val slop = viewConfiguration.touchSlop
                            if (vertical > slop && vertical >= horizontal) break
                            if (horizontal <= slop) {
                                motion.updatePointerPreview(positionAt(change.position.x))
                                val final = awaitPointerEvent(PointerEventPass.Final)
                                if (final.changes.any { it.id == down.id && it.isConsumed }) break
                                continue
                            }
                        }
                        // The selectable item handles taps while the settings page retains vertical scroll.
                        change.consume()
                        motion.dragTo(positionAt(change.position.x))
                    }
                } finally {
                    motion.release(settlement)
                }
            }
        }
    } else Modifier

    val shadow = if (enabled) {
        Modifier.materialColorShadow(
            shape = shape,
            containerColor = colors.primary,
            opacity = { motion.visibilityScale },
            bounds = ::indicatorBounds,
        ) { shadowElevation }
    } else Modifier

    Row(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .background(ThemeColorDefaults.controlContainerColor(parentContainerColor), shape)
            .textFieldInnerShadow(shape)
            .selectableGroup()
            .then(drag)
            .then(shadow)
            .slidingSelectionIndicator(
                shape = shape,
                bounds = { indicatorBounds() },
                containerColor = { if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.12f) },
                contentColor = colors.onPrimary.takeIf { enabled },
            ),
    ) {
        items.forEachIndexed { index, item ->
            key(item) {
                val contentColor = if (enabled) colors.onSurfaceVariant else colors.onSurface.copy(alpha = 0.38f)
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onPlaced { itemBounds[index] = Rect(it.positionInParent(), it.size.toSize()) }
                            .minimumInteractiveComponentSize()
                            .clip(shape)
                            .selectable(
                                selected = index == selectedIndex,
                                interactionSource = interactionSources[index],
                                indication = ripple(focusRingShape = shape, enablePressIndication = false),
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { if (enabled && index != selectedIndex) onSelectedChange(item) },
                            )
                            .fillMaxWidth()
                            .padding(horizontal = SegmentedItemHorizontalPadding, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.labelLarge) { content(item) }
                    }
                }
            }
        }
    }
}
