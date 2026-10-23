package moe.kirakira.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keeps the decoration's recording bounds equal to the visual surface, not the label bounds. */
@Composable
internal fun DestructiveButtonContent(
    visualSize: () -> Size,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource,
    color: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Layout(
        content = {
            Spacer(Modifier.destructiveDecoration(interactionSource, color))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        },
        measurePolicy = object : MeasurePolicy {
            override fun MeasureScope.measure(
                measurables: List<Measurable>,
                constraints: Constraints,
            ): MeasureResult {
                val label = measurables[1].measure(constraints)
                val surface = visualSize()
                val decoration = measurables[0].measure(
                    Constraints.fixed(
                        width = surface.width.roundToInt().coerceAtLeast(0),
                        height = surface.height.roundToInt().coerceAtLeast(0),
                    ),
                )
                // Undo the official Row's centered, padded placement for decoration only.
                val left = (label.width - decoration.width -
                    contentPadding.calculateLeftPadding(layoutDirection).roundToPx() +
                    contentPadding.calculateRightPadding(layoutDirection).roundToPx()) / 2f
                val top = (label.height - decoration.height -
                    contentPadding.calculateTopPadding().roundToPx() +
                    contentPadding.calculateBottomPadding().roundToPx()) / 2f
                return layout(label.width, label.height) {
                    decoration.place(left.roundToInt(), top.roundToInt())
                    label.place(0, 0)
                }
            }

            // The decoration follows the surface and contributes no intrinsic size.
            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>, height: Int,
            ): Int = measurables[1].minIntrinsicWidth(height)

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>, height: Int,
            ): Int = measurables[1].maxIntrinsicWidth(height)

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>, width: Int,
            ): Int = measurables[1].minIntrinsicHeight(width)

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>, width: Int,
            ): Int = measurables[1].maxIntrinsicHeight(width)
        },
    )
}

/** Cerasus decoration, clipped only by the enclosing official Button surface. */
@Composable
private fun Modifier.destructiveDecoration(
    interactionSource: MutableInteractionSource,
    color: Color,
): Modifier {
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    if (durationScale?.scaleFactor == 0f) return this

    val painter = painterResource(R.drawable.ic_symbol_close)
    val hovered by interactionSource.collectIsHoveredAsState()
    val hover = animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(1_000, easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)),
        label = "destructiveHover",
    )
    val transition = rememberInfiniteTransition(label = "destructiveDecoration")
    val phases = List(5) { index ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(4_000, easing = LinearEasing),
                initialStartOffset = StartOffset(index * 1_600),
            ),
            label = "destructiveCross$index",
        )
    }
    val tint = ColorFilter.tint(color)
    return drawBehind {
        val container = size
        if (container.width <= 0f || container.height <= 0f) return@drawBehind
        val emphasis = hover.value
        // A VectorPainter owns one bitmap cache. Keep its raster size fixed across all
        // five draws so later icons cannot resize/overwrite earlier recorded images.
        val rasterSize = 48.dp.toPx()
        phases.forEachIndexed { index, phase ->
            val progress = phase.value
            val iconSize = DecorationSizes[index].dp.toPx()
            val alpha = (1f - abs(progress * 2f - 1f)) * 0.4f
            val x = container.width * DecorationPositions[index]
            val y = (container.height - iconSize) / 2f + (1.5f - progress * 3f) * iconSize
            translate(x, y) {
                val center = Offset(iconSize / 2f, iconSize / 2f)
                rotate(90f * emphasis, pivot = center) {
                    scale(1.5f + 0.5f * emphasis, pivot = center) {
                        scale(iconSize / rasterSize, pivot = Offset.Zero) {
                            with(painter) {
                                draw(Size(rasterSize, rasterSize), alpha = alpha, colorFilter = tint)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DecorationSizes = listOf(36, 24, 48, 16, 32)
private val DecorationPositions = listOf(0f, 0.2f, 0.5f, 0.6f, 0.8f)
