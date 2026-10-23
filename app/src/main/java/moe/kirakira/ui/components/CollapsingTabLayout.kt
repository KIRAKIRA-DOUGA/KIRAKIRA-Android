package moe.kirakira.ui.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import moe.kirakira.ui.theme.barSurfaceLayer
import kotlin.math.roundToInt

internal class CollapsingTabState(initialOffset: Float = 0f) {
    private var savedOffset by mutableFloatStateOf(initialOffset)
    internal var headerHeight by mutableIntStateOf(0)
    val offset: Float get() = savedOffset.coerceIn(0f, headerHeight.toFloat())
    val isCollapsed by derivedStateOf { headerHeight > 0 && offset >= headerHeight }

    internal val scrollableState = ScrollableState { delta ->
        val previous = offset
        savedOffset = (previous + delta).coerceIn(0f, headerHeight.toFloat())
        savedOffset - previous
    }

    internal val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            return Offset(0f, -scrollableState.dispatchRawDelta(-available.y))
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f) return Offset.Zero
            return Offset(0f, -scrollableState.dispatchRawDelta(-available.y))
        }
    }

    companion object {
        val Saver = Saver<CollapsingTabState, Float>(
            save = { it.savedOffset },
            restore = { CollapsingTabState(it) },
        )
    }
}

@Composable
internal fun rememberCollapsingTabState(): CollapsingTabState =
    rememberSaveable(saver = CollapsingTabState.Saver) { CollapsingTabState() }

/** One elevated header surface above the pager; only the header body scrolls out of view. */
@Composable
internal fun CollapsingTabLayout(
    state: CollapsingTabState,
    topPadding: Dp,
    tabHeight: Dp,
    modifier: Modifier = Modifier,
    headerBackground: @Composable BoxScope.() -> Unit,
    header: @Composable () -> Unit,
    tabs: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.clipToBounds().nestedScroll(state.nestedScrollConnection),
        content = {
            Box { content() }
            Box(Modifier.barSurfaceLayer()) {
                Box(Modifier.matchParentSize(), content = headerBackground)
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(topPadding))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .scrollable(state.scrollableState, Orientation.Vertical, reverseDirection = true)
                            .onSizeChanged { state.headerHeight = it.height }
                            .graphicsLayer { alpha = if (state.isCollapsed) 0f else 1f },
                    ) {
                        header()
                    }
                    Box(
                        Modifier.scrollable(
                            state.scrollableState,
                            Orientation.Vertical,
                            enabled = !state.isCollapsed,
                            reverseDirection = true,
                        ),
                    ) {
                        tabs()
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val top = topPadding.roundToPx()
        val tabs = tabHeight.roundToPx()
        val surface = measurables[1].measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        val page = measurables[0].measure(
            Constraints.fixed(constraints.maxWidth, (constraints.maxHeight - top - tabs).coerceAtLeast(0)),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val range = (surface.height - top - tabs).coerceAtLeast(0)
            val offset = state.offset.roundToInt().coerceIn(0, range)
            page.placeRelative(0, surface.height - offset)
            surface.placeRelative(0, -offset)
        }
    }
}
