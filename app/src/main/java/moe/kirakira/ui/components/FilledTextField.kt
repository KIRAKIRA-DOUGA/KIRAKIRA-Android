package moe.kirakira.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.ThemeColorDefaults

internal object FilledTextFieldDefaults {
    val IndicatorThickness = 2.dp

    @Composable
    fun colors(parentContainerColor: Color = MaterialTheme.colorScheme.surface): TextFieldColors {
        val containerColor = ThemeColorDefaults.controlContainerColor(parentContainerColor)
        return TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            errorContainerColor = containerColor,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
internal fun Modifier.textFieldInnerShadow(shape: Shape): Modifier {
    val scheme = MaterialTheme.colorScheme
    val darkTheme = scheme.surface.luminance() < scheme.onSurface.luminance()
    return innerShadow(
        shape = shape,
        shadow = Shadow(
            radius = 4.dp,
            spread = 0.dp,
            color = scheme.scrim.copy(alpha = if (darkTheme) 0.12f else 0.06f),
            offset = DpOffset(0.dp, 4.dp),
        ),
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
        Box(Modifier.matchParentSize().textFieldInnerShadow(shape))
    }
}

/** Material filled field with a consistent 2dp indicator, matching Cerasus's 2px stripe. */
@Composable
internal fun FilledTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    errorMessage: String? = null,
    isError: Boolean = errorMessage != null,
    inputTransformation: InputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    passwordObfuscationMode: TextObfuscationMode? = null,
    shape: Shape = MaterialTheme.shapes.large,
    colors: TextFieldColors = FilledTextFieldDefaults.colors(),
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = if (isError) { { ErrorIcon() } } else null,
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
    val fieldModifier = modifier
        .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())
        .then(if (isError) Modifier.semantics { error(errorMessage ?: invalidInputMessage) } else Modifier)
        .defaultMinSize(minWidth = TextFieldDefaults.MinWidth, minHeight = TextFieldDefaults.MinHeight)
    val style = textStyle.merge(TextStyle(color = textColor))
    val cursor = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor)
    val decorator = TextFieldDefaults.decorator(
        state = state,
        enabled = enabled,
        lineLimits = if (passwordObfuscationMode != null) TextFieldLineLimits.SingleLine else lineLimits,
        outputTransformation = null,
        interactionSource = interaction,
        isError = isError,
        label = label?.let { content -> { content() } },
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        colors = colors,
        container = { FilledTextFieldContainer(enabled, isError, interaction, colors = colors, shape = shape) },
    )
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        if (passwordObfuscationMode != null) {
            BasicSecureTextField(
                state = state,
                modifier = fieldModifier,
                enabled = enabled,
                readOnly = readOnly,
                textStyle = style,
                cursorBrush = cursor,
                inputTransformation = inputTransformation,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                interactionSource = interaction,
                decorator = decorator,
                textObfuscationMode = passwordObfuscationMode,
                textObfuscationCharacter = '\u2022',
            )
        } else {
            BasicTextField(
                state = state,
                modifier = fieldModifier,
                enabled = enabled,
                readOnly = readOnly,
                textStyle = style,
                cursorBrush = cursor,
                inputTransformation = inputTransformation,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                lineLimits = lineLimits,
                interactionSource = interaction,
                decorator = decorator,
            )
        }
    }
}
