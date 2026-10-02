@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.kirakira.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MinimumInteractiveLeftAlignmentLine
import androidx.compose.material3.MinimumInteractiveTopAlignmentLine
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Interpolatable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun ShadowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    shapes: ButtonShapes? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val outline = rememberButtonShadowShape(shape, shapes?.shape, shapes?.pressedShape, enabled, source)
    Button(
        onClick = onClick,
        modifier = modifier.buttonShadow(outline, colors.containerColor, enabled, source),
        enabled = enabled,
        shape = outline,
        colors = colors,
        elevation = null,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

@Composable
internal fun ShadowFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    shapes: ButtonShapes? = null,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val outline = rememberButtonShadowShape(shape, shapes?.shape, shapes?.pressedShape, enabled, source)
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.buttonShadow(outline, colors.containerColor, enabled, source),
        enabled = enabled,
        shape = outline,
        colors = colors,
        elevation = null,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content,
    )
}

@Composable
internal fun ShadowFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    shapes: IconButtonShapes? = null,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val outline = rememberButtonShadowShape(shape, shapes?.shape, shapes?.pressedShape, enabled, source)
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.buttonShadow(outline, colors.containerColor, enabled, source),
        enabled = enabled,
        shape = outline,
        colors = colors,
        interactionSource = source,
        content = content,
    )
}

@Composable
internal fun ShadowFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    shapes: IconButtonShapes? = null,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val outline = rememberButtonShadowShape(shape, shapes?.shape, shapes?.pressedShape, enabled, source)
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.buttonShadow(outline, colors.containerColor, enabled, source),
        enabled = enabled,
        shape = outline,
        colors = colors,
        interactionSource = source,
        content = content,
    )
}

@Composable
internal fun ShadowFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .semantics { if (!enabled) disabled() }
            .buttonShadow(shape, containerColor, enabled, source, floating = true),
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        interactionSource = source,
        content = content,
    )
}

@Composable
private fun rememberButtonShadowShape(
    shape: Shape,
    defaultShape: Shape?,
    pressedShape: Shape?,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (enabled && pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "buttonShadowShape",
    )
    if (defaultShape == null || pressedShape == null) return shape
    return Interpolatable.lerp(defaultShape, pressedShape, progress) as? Shape
        ?: if (enabled && pressed) pressedShape else defaultShape
}

@Composable
private fun Modifier.buttonShadow(
    shape: Shape,
    containerColor: Color,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    floating: Boolean = false,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    var visualInset by remember { mutableStateOf(IntOffset.Zero) }
    val shadowShape = remember(shape, visualInset) { ButtonShadowOutline(shape, visualInset) }
    val elevation by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            pressed -> if (floating) 12f else 8f
            hovered || focused -> if (floating) 8f else 4f
            else -> if (floating) 6f else 2f
        },
        animationSpec = tween(durationMillis = if (enabled && pressed) 120 else 180),
        label = "buttonShadowElevation",
    )
    if (!enabled) return this
    val maximum = maxOf(containerColor.red, containerColor.green, containerColor.blue)
    val minimum = minOf(containerColor.red, containerColor.green, containerColor.blue)
    val shadowColor = if (maximum - minimum <= 0.02f) Color.Black else containerColor.copy(alpha = 1f)
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        visualInset = IntOffset(
            placeable[MinimumInteractiveLeftAlignmentLine].coerceAtLeast(0),
            placeable[MinimumInteractiveTopAlignmentLine].coerceAtLeast(0),
        )
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }.dropShadow(shadowShape) {
        radius = (elevation * 0.75f).dp.toPx()
        spread = 0f
        color = shadowColor
        alpha = (0.08f + elevation * 0.005f) * containerColor.alpha
        offset = Offset.Zero
    }.dropShadow(shadowShape) {
        radius = elevation.dp.toPx()
        spread = 0f
        color = shadowColor
        alpha = (0.14f + elevation * 0.012f) * containerColor.alpha
        offset = Offset(0f, (elevation * 0.5f).dp.toPx())
    }
}

private class ButtonShadowOutline(
    private val shape: Shape,
    private val inset: IntOffset,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (inset == IntOffset.Zero) return shape.createOutline(size, layoutDirection, density)
        val visualSize = Size(
            (size.width - inset.x * 2f).coerceAtLeast(0f),
            (size.height - inset.y * 2f).coerceAtLeast(0f),
        )
        val path = Path().apply {
            addOutline(shape.createOutline(visualSize, layoutDirection, density))
            translate(Offset(inset.x.toFloat(), inset.y.toFloat()))
        }
        return Outline.Generic(path)
    }
}
