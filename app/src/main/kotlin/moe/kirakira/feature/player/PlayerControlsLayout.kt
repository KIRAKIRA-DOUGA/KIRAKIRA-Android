package moe.kirakira.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.status.IndeterminateCircularProgressIndicator
import moe.kirakira.ui.components.video.durationText

@Composable
internal fun PlayerTimeLabel(
    positionMs: Long,
    durationMs: Long,
    showRemainingTime: Boolean,
    resumePositionMs: Long?,
) {
    val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    if (resumePositionMs != null) {
        Text(
            text = stringResource(R.string.player_resumed_position, durationText(resumePositionMs)),
            style = style,
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        return
    }

    val total = remember(durationMs) { durationText(durationMs) }
    if (durationMs <= 0) {
        Text("${durationText(positionMs)} / $total", style = style, color = Color.White)
        return
    }

    val remainingMs = (durationMs - positionMs).coerceAtLeast(0)
    val displayedMs = if (showRemainingTime) remainingMs else positionMs
    val displayedTime = remember(displayedMs / 1000) { durationText(displayedMs) }
    val sign = if (showRemainingTime) "-" else ""
    val transition = updateTransition(showRemainingTime, label = "PlayerTimeMode")
    val motion = MaterialTheme.motionScheme
    val sizeSpec = motion.fastSpatialSpec<IntSize>()
    val fadeSpec = motion.fastEffectsSpec<Float>()
    FlowRow(
        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString("$sign$displayedTime / $total") },
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            transition.AnimatedVisibility(
                visible = { it },
                enter = fadeIn(fadeSpec) + expandHorizontally(sizeSpec, expandFrom = Alignment.Start),
                exit = fadeOut(fadeSpec) + shrinkHorizontally(sizeSpec, shrinkTowards = Alignment.Start),
            ) {
                Text("-", style = style, color = Color.White)
            }
            transition.AnimatedContent(
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    (fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec)).using(
                        SizeTransform { _, _ -> sizeSpec },
                    )
                },
            ) { remaining ->
                val time = when {
                    remaining == showRemainingTime -> displayedTime
                    remaining -> durationText(remainingMs)
                    else -> durationText(positionMs)
                }
                Text(time, style = style, color = Color.White)
            }
        }
        Text(" / $total", style = style, color = Color.White)
    }
}

@Composable
internal fun PlayerPlaybackControl(
    state: PlayerUiState,
    motion: PlaybackIconMotion,
    artworkLoading: Boolean,
    buttonSize: Dp,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (state.buffering || state.initialLoading) {
            IndeterminateCircularProgressIndicator(modifier = Modifier.size(40.dp), color = Color.White)
        } else {
            IconButton(
                onClick = onToggle,
                enabled = state.available,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.38f),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (artworkLoading) {
                    val playLabel = stringResource(R.string.player_play)
                    IndeterminateCircularProgressIndicator(
                        modifier = Modifier.size(40.dp).semantics { contentDescription = playLabel },
                        color = Color.White,
                    )
                } else {
                    AnimatedPlaybackIcon(
                        motion = motion,
                        description = stringResource(if (state.showPauseIcon) R.string.player_pause else R.string.player_play),
                        modifier = Modifier.size(if (buttonSize < 64.dp) 28.dp else 48.dp),
                    )
                }
            }
        }
    }
}

private enum class PlayerControlSlot { TOP, TIME, ACTIONS, PROGRESS, PLAYBACK }

@Composable
internal fun PlayerControlsLayout(
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit,
    time: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    progress: @Composable () -> Unit,
    playback: @Composable (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val horizontalOffset = (insets.getRight(density, layoutDirection) - insets.getLeft(density, layoutDirection)) / 2
    val centerOffset = if (fullscreen) IntOffset(
        if (layoutDirection == LayoutDirection.Ltr) horizontalOffset else -horizontalOffset,
        (insets.getBottom(density) - insets.getTop(density)) / 2,
    ) else IntOffset.Zero
    val controlsScroll = rememberScrollState()
    BoxWithConstraints(modifier) {
        val viewportHeight = with(density) { maxHeight.roundToPx() }
        SubcomposeLayout(
            Modifier
                .fillMaxWidth()
                .verticalScroll(controlsScroll, enabled = controlsScroll.maxValue > 0),
        ) { constraints ->
            val width = constraints.maxWidth
            val loose = Constraints(maxWidth = width)
            val gap = 8.dp.roundToPx()
            val rowGap = 4.dp.roundToPx()
            val bottomPadding = 4.dp.roundToPx()
            fun measure(slot: PlayerControlSlot, content: @Composable () -> Unit): Placeable =
                subcompose(slot) { Box { content() } }.single().measure(loose)

            val top = measure(PlayerControlSlot.TOP, topBar)
            val timestamp = measure(PlayerControlSlot.TIME, time)
            val trailing = measure(PlayerControlSlot.ACTIONS, actions)
            val seek = measure(PlayerControlSlot.PROGRESS, progress)
            fun rows(items: List<Placeable>): List<List<Placeable>> {
                val result = mutableListOf<List<Placeable>>()
                var row = mutableListOf<Placeable>()
                var usedWidth = 0
                items.forEach { item ->
                    if (row.isNotEmpty() && usedWidth + gap + item.width > width) {
                        result.add(row)
                        row = mutableListOf()
                        usedWidth = 0
                    }
                    if (row.isNotEmpty()) usedWidth += gap
                    row.add(item)
                    usedWidth += item.width
                }
                if (row.isNotEmpty()) result.add(row)
                return result
            }

            fun rowsHeight(rows: List<List<Placeable>>): Int =
                rows.sumOf { row -> row.maxOf { it.height } } + rowGap * (rows.size - 1)

            fun arrangeRows(rows: List<List<Placeable>>, startY: Int, place: (Placeable, Int, Int) -> Unit) {
                var y = startY
                rows.forEach { row ->
                    val rowHeight = row.maxOf { it.height }
                    val rowWidth = row.sumOf { it.width } + gap * (row.size - 1)
                    var x = if (row.first() == timestamp) 0 else width - rowWidth
                    row.forEachIndexed { index, item ->
                        if (index == 1 && row.first() == timestamp) x += width - rowWidth
                        place(item, x, y + (rowHeight - item.height) / 2)
                        x += item.width + gap
                    }
                    y += rowHeight + rowGap
                }
            }

            // Center on the video, compensating for asymmetric fullscreen safe Insets.
            val regularRows = rows(listOf(timestamp, trailing))
            val centerSize = 64.dp.roundToPx()
            val centerX = (width - centerSize) / 2 + centerOffset.x
            val centerY = (viewportHeight - centerSize) / 2 + centerOffset.y
            val centerGap = 2.dp.roundToPx()
            val regularRowsY = viewportHeight - bottomPadding - rowsHeight(regularRows)
            val regularSeekY = regularRowsY - seek.height
            val seekVisibleTop = regularSeekY +
                (seek.height - PlayerSeekThumbSize.height.roundToPx()) / 2 + PlayerSeekVisualOffset.roundToPx()
            val seekVisibleBottom = seekVisibleTop + PlayerSeekThumbSize.height.roundToPx()
            var compact = centerX < 0 || centerX + centerSize > width || centerY < top.height + centerGap ||
                centerY + centerSize + centerGap > seekVisibleTop && centerY < seekVisibleBottom + centerGap
            arrangeRows(regularRows, regularRowsY) { item, x, y ->
                if (x < centerX + centerSize + centerGap && x + item.width + centerGap > centerX &&
                    y < centerY + centerSize + centerGap && y + item.height + centerGap > centerY
                ) {
                    compact = true
                }
            }
            val play = measure(PlayerControlSlot.PLAYBACK) { playback(if (compact) 48.dp else 64.dp) }
            val footerRows = if (compact) rows(listOf(timestamp, play, trailing)) else regularRows
            val footerHeight = rowsHeight(footerRows) + seek.height + bottomPadding
            val minimumHeight = top.height + footerHeight + if (compact) gap else 0
            val height = maxOf(viewportHeight, minimumHeight)
            layout(width, height) {
                top.placeRelative(0, 0)
                if (!compact) {
                    play.placeRelative(centerX, centerY)
                }
                seek.placeRelative(0, height - footerHeight)
                arrangeRows(footerRows, height - bottomPadding - rowsHeight(footerRows)) { item, x, y ->
                    item.placeRelative(x, y)
                }
            }
        }
    }
}
