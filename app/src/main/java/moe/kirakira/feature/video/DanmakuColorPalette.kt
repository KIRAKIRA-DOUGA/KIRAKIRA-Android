package moe.kirakira.feature.video

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.data.content.DanmakuStyle

private val danmakuColors =
    listOf(0xFFFFFF, 0xFF3225, 0xF06E8E, 0xFFA800, 0xFBFF34, 0x2CE73F, 0x39C5BB, 0x24C1F2, 0xDC1FED)
private val danmakuColorRows = (danmakuColors + listOf<Int?>(null)).chunked(5)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DanmakuColorPalette(
    color: Int,
    onColor: (Int) -> Unit,
    onCustomColor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        danmakuColorRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { rgb ->
                    if (rgb != null) {
                        DanmakuColorOption(
                            checked = color == rgb,
                            onClick = { onColor(rgb) },
                            description = stringResource(
                                R.string.danmaku_style_color_value,
                                DanmakuStyle(color = rgb).colorHex,
                            ),
                            color = Color(0xFF000000.toInt() or rgb),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        val checked = color !in danmakuColors
                        DanmakuColorOption(
                            checked = checked,
                            onClick = onCustomColor,
                            description = stringResource(R.string.danmaku_style_custom),
                            color = if (checked) {
                                Color(0xFF000000.toInt() or color)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.weight(1f),
                            uncheckedContent = {
                                Icon(
                                    painterResource(R.drawable.ic_symbol_palette),
                                    contentDescription = null,
                                    modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DanmakuColorOption(
    checked: Boolean,
    onClick: () -> Unit,
    description: String,
    color: Color,
    modifier: Modifier = Modifier,
    uncheckedContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val transition = updateTransition(checked, label = "danmakuColorSelection")
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val shapeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val indicatorAlpha = transition.animateFloat(
        transitionSpec = { effectsSpec },
        label = "selectionIndicatorAlpha",
    ) { selected -> if (selected) 1f else 0f }
    val indicatorScale = transition.animateFloat(
        transitionSpec = { spatialSpec },
        label = "selectionIndicatorScale",
    ) { selected -> if (selected) 1f else 0.6f }
    val selectionShapeProgress = transition.animateFloat(
        transitionSpec = { shapeSpec },
        label = "selectionShapeProgress",
    ) { selected -> if (selected) 1f else 0f }
    val pressedShapeProgress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = shapeSpec,
        label = "pressedShapeProgress",
    )
    val shapes = IconButtonDefaults.toggleableShapes(
        shape = IconButtonDefaults.extraSmallRoundShape,
        pressedShape = IconButtonDefaults.extraSmallPressedShape,
        checkedShape = IconButtonDefaults.extraSmallSelectedRoundShape,
    )
    // The visible swatch and its outer outline read the same morph in the drawing phase.
    val shape = remember(shapes, selectionShapeProgress, pressedShapeProgress) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val selectedShape = Interpolatable.lerp(shapes.shape, shapes.checkedShape, selectionShapeProgress.value)
                val currentShape = Interpolatable.lerp(selectedShape, shapes.pressedShape, pressedShapeProgress.value)
                return (currentShape as Shape).createOutline(size, layoutDirection, density)
            }
        }
    }
    val outlinePath = remember { Path() }
    val accent = MaterialTheme.colorScheme.primary
    val buttonSize = IconButtonDefaults.extraSmallContainerSize()
    val checkColor = if (color.luminance() > 0.45f) Color.Black else Color.White

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .drawWithContent {
                drawContent()
                val alpha = indicatorAlpha.value.coerceIn(0f, 1f)
                if (alpha > 0f) {
                    val swatchSize = Size(buttonSize.width.toPx(), buttonSize.height.toPx())
                    val swatchOutline = shape.createOutline(swatchSize, layoutDirection, this)
                    val roundRect = when (swatchOutline) {
                        is Outline.Rounded -> swatchOutline.roundRect
                        is Outline.Rectangle -> RoundRect(swatchOutline.rect)
                        is Outline.Generic -> return@drawWithContent
                    }
                    outlinePath.reset()
                    outlinePath.addRoundRect(roundRect.outset(4.dp.toPx()))
                    translate(
                        left = (size.width - swatchSize.width) / 2,
                        top = (size.height - swatchSize.height) / 2,
                    ) {
                        drawPath(
                            path = outlinePath,
                            color = accent.copy(alpha = alpha),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        IconToggleButton(
            checked = checked,
            onCheckedChange = { onClick() },
            shape = shape,
            modifier = Modifier
                .size(buttonSize)
                .semantics {
                    contentDescription = description
                    role = Role.RadioButton
                    selected = checked
                },
            colors = IconButtonDefaults.iconToggleButtonColors(
                containerColor = color,
                checkedContainerColor = color,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContentColor = checkColor,
            ),
            interactionSource = interactionSource,
        ) {
            if (uncheckedContent != null) {
                Box(Modifier.graphicsLayer { alpha = 1f - indicatorAlpha.value }) {
                    uncheckedContent()
                }
            }
            Icon(
                painterResource(R.drawable.ic_symbol_check),
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier
                    .size(IconButtonDefaults.extraSmallIconSize)
                    .graphicsLayer {
                        alpha = indicatorAlpha.value
                        scaleX = indicatorScale.value
                        scaleY = indicatorScale.value
                    },
            )
        }
    }
}

private fun RoundRect.outset(amount: Float): RoundRect = RoundRect(
    rect = Rect(left - amount, top - amount, right + amount, bottom + amount),
    topLeft = topLeftCornerRadius.outset(amount),
    topRight = topRightCornerRadius.outset(amount),
    bottomRight = bottomRightCornerRadius.outset(amount),
    bottomLeft = bottomLeftCornerRadius.outset(amount),
)

private fun CornerRadius.outset(amount: Float): CornerRadius = CornerRadius(x + amount, y + amount)
