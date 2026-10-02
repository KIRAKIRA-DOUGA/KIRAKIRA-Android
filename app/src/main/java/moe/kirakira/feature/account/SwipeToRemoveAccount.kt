package moe.kirakira.feature.account

import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.R

private enum class AccountSwipeValue { CLOSED, REVEALED, REMOVE }

/** Adds a reveal stop before the full-swipe action; removal still goes through the page's confirmation. */
@Composable
internal fun SwipeToRemoveAccount(
    accountId: String,
    removeLabel: String,
    active: Boolean,
    onSwipeStarted: () -> Unit,
    onClose: () -> Unit,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier, () -> Unit) -> Unit,
) {
    val state = remember { AnchoredDraggableState(AccountSwipeValue.CLOSED) }
    val interactionSource = remember { MutableInteractionSource() }
    val dragging by interactionSource.collectIsDraggedAsState()
    val latestOnRemove by rememberUpdatedState(onRemove)
    val latestOnSwipeStarted by rememberUpdatedState(onSwipeStarted)
    var rowWidth by remember { mutableIntStateOf(0) }
    var buttonWidth by remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { ListItemDefaults.SegmentedGap.toPx() }
    val motionSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = state,
        animationSpec = motionSpec,
    )
    val closeLabel = stringResource(R.string.account_close_swipe)
    val revealed = active && state.settledValue == AccountSwipeValue.REVEALED && !dragging

    LaunchedEffect(rowWidth, buttonWidth, gap) {
        if (rowWidth > 0 && buttonWidth > 0) {
            state.updateAnchors(
                newAnchors = DraggableAnchors {
                    AccountSwipeValue.CLOSED at 0f
                    AccountSwipeValue.REVEALED at -(buttonWidth + gap).coerceAtMost(rowWidth * 0.5f)
                    AccountSwipeValue.REMOVE at -rowWidth.toFloat()
                },
                newTarget = if (state.settledValue == AccountSwipeValue.REMOVE) {
                    AccountSwipeValue.CLOSED
                } else {
                    state.settledValue
                },
            )
        }
    }
    LaunchedEffect(dragging) {
        if (dragging) latestOnSwipeStarted()
    }
    LaunchedEffect(active) {
        if (!active && state.offset.isFinite() && state.offset != 0f) {
            state.animateTo(AccountSwipeValue.CLOSED, motionSpec)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }
            .filter { it == AccountSwipeValue.REMOVE }
            .collect {
                // Keep the account present behind the dialog, including when the user cancels it.
                state.snapTo(AccountSwipeValue.CLOSED)
                latestOnRemove()
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidth = it.width }
            .clipToBounds()
            .anchoredDraggable(
                state = state,
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                flingBehavior = flingBehavior,
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val offset = state.offset.takeIf { it.isFinite() } ?: 0f
                    alpha = (-offset / (buttonWidth + gap).coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
                .semantics { if (!revealed) hideFromAccessibility() },
            contentAlignment = Alignment.CenterEnd,
        ) {
            ShadowButton(
                onClick = onRemove,
                enabled = revealed,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 80.dp)
                    .onSizeChanged { buttonWidth = it.width }
                    .testTag("account_swipe_remove_$accountId"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.onError,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_symbol_delete), contentDescription = null)
                }
            }
        }
        content(
            Modifier
                // Relative offset and anchoredDraggable both mirror automatically in RTL.
                .offset { IntOffset((state.offset.takeIf { it.isFinite() } ?: 0f).roundToInt(), 0) }
                .semantics {
                    customActions = buildList {
                        add(CustomAccessibilityAction(removeLabel) { onRemove(); true })
                        if (revealed) add(CustomAccessibilityAction(closeLabel) { onClose(); true })
                    }
                },
            {
                if (state.offset.isFinite() && state.offset != 0f) {
                    onClose()
                } else {
                    onSelect()
                }
            },
        )
    }
}
