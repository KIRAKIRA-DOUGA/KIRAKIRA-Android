package moe.kirakira.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldDefaults.indicatorLine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import moe.kirakira.R

internal object FilledTextFieldDefaults {
    val IndicatorThickness = 2.dp

    @Composable
    fun colors(): TextFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Keep the inset shadow below the official indicator and the editor's foreground content. */
@Composable
internal fun FilledTextFieldContainer(
    enabled: Boolean,
    isError: Boolean,
    interactionSource: InteractionSource,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = FilledTextFieldDefaults.colors(),
    shape: Shape = MaterialTheme.shapes.large,
    indicatorThickness: Dp = FilledTextFieldDefaults.IndicatorThickness,
) {
    val scheme = MaterialTheme.colorScheme
    val darkTheme = scheme.surface.luminance() < scheme.onSurface.luminance()
    Box(
        modifier = modifier.indicatorLine(
            enabled = enabled,
            isError = isError,
            interactionSource = interactionSource,
            colors = colors,
            textFieldShape = shape,
            focusedIndicatorLineThickness = indicatorThickness,
            unfocusedIndicatorLineThickness = indicatorThickness,
        ),
    ) {
        TextFieldDefaults.Container(
            enabled = enabled,
            isError = isError,
            interactionSource = interactionSource,
            modifier = Modifier.matchParentSize(),
            colors = colors,
            shape = shape,
            focusedIndicatorLineThickness = 0.dp,
            unfocusedIndicatorLineThickness = 0.dp,
        )
        // innerShadow draws before its content; a separate sibling keeps the opaque background below it.
        Box(
            Modifier
                .matchParentSize()
                .innerShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 4.dp,
                        spread = 0.dp,
                        color = scheme.scrim.copy(alpha = if (darkTheme) 0.12f else 0.06f),
                        offset = DpOffset(0.dp, 4.dp),
                    ),
                ),
        )
    }
}

/** Material filled field with a consistent 2dp indicator, matching Cerasus's 2px stripe. */
@Composable
internal fun FilledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    errorMessage: String? = null,
    isError: Boolean = errorMessage != null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = MaterialTheme.shapes.large,
    colors: TextFieldColors = FilledTextFieldDefaults.colors(),
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = if (isError) { { TextFieldErrorIcon() } } else null,
    supportingText: @Composable (() -> Unit)? = textFieldSupportingError(errorMessage),
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val textColor = textStyle.color.takeOrElse {
        when {
            !enabled -> colors.disabledTextColor
            isError -> colors.errorTextColor
            focused -> colors.focusedTextColor
            else -> colors.unfocusedTextColor
        }
    }
    val invalidInputMessage = stringResource(R.string.text_field_invalid_input)
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
                .then(if (isError) Modifier.semantics { error(errorMessage ?: invalidInputMessage) } else Modifier)
                .defaultMinSize(minWidth = TextFieldDefaults.MinWidth, minHeight = TextFieldDefaults.MinHeight),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle.merge(TextStyle(color = textColor)),
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            interactionSource = interaction,
            decorationBox = { editor ->
                TextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = editor,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = interaction,
                    isError = isError,
                    label = label,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    supportingText = supportingText,
                    shape = shape,
                    colors = colors,
                    container = {
                        FilledTextFieldContainer(
                            enabled = enabled,
                            isError = isError,
                            interactionSource = interaction,
                            colors = colors,
                            shape = shape,
                        )
                    },
                )
            },
        )
    }
}

@Composable
internal fun TextFieldErrorIcon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_symbol_error_filled),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = modifier.size(24.dp),
    )
}
