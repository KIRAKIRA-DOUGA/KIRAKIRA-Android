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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Destructive actions own their error colors; [colors] applies only to ordinary buttons. */
@Composable
internal fun ShadowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    shapes: ButtonShapes = ButtonDefaults.shapes(),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val outline = rememberButtonShadowShape(shapes.shape, shapes.pressedShape, enabled, source)
    val resolvedColors = if (destructive) DestructiveActionDefaults.buttonColors() else colors
    var visualSize by remember { mutableStateOf(Size.Zero) }
    Button(
        onClick = onClick,
        modifier = modifier
            .then(
                if (destructive) Modifier.observeButtonMeasurement { placeable ->
                    val horizontalInset = placeable[MinimumInteractiveLeftAlignmentLine].coerceAtLeast(0)
                    val verticalInset = placeable[MinimumInteractiveTopAlignmentLine].coerceAtLeast(0)
                    visualSize = Size(
                        (placeable.width - 2 * horizontalInset).toFloat(),
                        (placeable.height - 2 * verticalInset).toFloat(),
                    )
                } else Modifier,
            )
            .buttonShadow(outline, resolvedColors.containerColor, enabled, source),
        enabled = enabled,
        shape = outline,
        colors = resolvedColors,
        elevation = null,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
    ) {
        if (destructive && enabled) {
            DestructiveButtonContent(
                visualSize = { visualSize },
                contentPadding = contentPadding,
                interactionSource = source,
                color = resolvedColors.contentColor,
                content = content,
            )
        } else {
            content()
        }
    }
}

@Composable
internal fun ShadowFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ButtonShapes = ButtonDefaults.shapes(),
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val outline = rememberButtonShadowShape(shapes.shape, shapes.pressedShape, enabled, source)
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
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val outline = rememberButtonShadowShape(shapes.shape, shapes.pressedShape, enabled, source)
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
    shapes: IconButtonShapes = IconButtonDefaults.shapes(),
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val outline = rememberButtonShadowShape(shapes.shape, shapes.pressedShape, enabled, source)
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
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.primary,
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
    defaultShape: Shape,
    pressedShape: Shape,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
): Shape {
    if (defaultShape == pressedShape) return defaultShape
    val pressed by interactionSource.collectIsPressedAsState()
    return rememberMorphingButtonShape(
        targetShape = if (enabled && pressed) pressedShape else defaultShape,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
}

@Composable
internal fun Modifier.buttonShadow(
    shape: Shape,
    containerColor: Color,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    floating: Boolean = false,
    opacity: () -> Float = { 1f },
    tint: () -> Color = { materialShadowColor(containerColor) },
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    var visualInset by remember { mutableStateOf(IntOffset.Zero) }
    val shadowShape = remember(shape, visualInset) { ButtonShadowOutline(shape, visualInset) }
    val elevation by animateFloatAsState(
        targetValue = when {
            // Disabled buttons draw no shadow, but retain resting geometry for immediate restoration.
            !enabled -> if (floating) 6f else 2f
            pressed -> if (floating) 12f else 8f
            hovered || focused -> if (floating) 8f else 4f
            else -> if (floating) 6f else 2f
        },
        animationSpec = tween(durationMillis = if (enabled && pressed) 120 else 180),
        label = "buttonShadowElevation",
    )
    if (!enabled) return this
    return observeButtonMeasurement { placeable ->
        visualInset = IntOffset(
            placeable[MinimumInteractiveLeftAlignmentLine].coerceAtLeast(0),
            placeable[MinimumInteractiveTopAlignmentLine].coerceAtLeast(0),
        )
    }.materialColorShadow(shadowShape, containerColor, opacity = opacity, tint = tint) { elevation }
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

/** Intrinsic queries delegate without publishing synthetic measurements to the decoration. */
internal fun Modifier.observeButtonMeasurement(onMeasured: (Placeable) -> Unit): Modifier = then(
    object : LayoutModifier {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            onMeasured(placeable)
            return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

        override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
            measurable.minIntrinsicWidth(height)

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
            measurable.maxIntrinsicWidth(height)

        override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
            measurable.minIntrinsicHeight(width)

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
            measurable.maxIntrinsicHeight(width)
    },
)
