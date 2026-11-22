package moe.kirakira.ui.components.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import moe.kirakira.R
import moe.kirakira.core.color.ColorValueFormat
import moe.kirakira.core.color.formatColorValue
import moe.kirakira.core.color.parseColorValue

/** The draft survives recreation; picker controllers remain local to the controls. */
internal class ColorEditorState(
    initialArgb: Int,
    initialFormat: ColorValueFormat = ColorValueFormat.HEX,
    initialInput: String = formatColorValue(initialArgb, initialFormat),
) {
    val editor = TextFieldState(initialInput)
    var format by mutableStateOf(initialFormat)
        private set
    private var lastValidArgb by mutableIntStateOf(initialArgb or 0xFF000000.toInt())
    private val parsedInput by derivedStateOf { parseColorValue(editor.text.toString(), format) }
    private var pickerValue by mutableStateOf(initialInput to lastValidArgb)
    private var lastObservedInput = initialInput
    val argb: Int get() = parsedColor ?: lastValidArgb
    val parsedColor: Int?
        get() = parsedInput?.let {
            // Formatting (especially OKLCH) need not round-trip to the exact picker color.
            val (text, argb) = pickerValue
            if (editor.text.toString() == text) argb else it.argb
        }
    val gamutMapped: Boolean get() = parsedInput?.gamutMapped == true

    fun selectColor(color: Int) {
        lastValidArgb = color or 0xFF000000.toInt()
        val text = formatColorValue(lastValidArgb, format)
        pickerValue = text to lastValidArgb
        lastObservedInput = text
        if (editor.text.toString() != text) editor.setTextAndPlaceCursorAtEnd(text)
    }

    fun selectFormat(value: ColorValueFormat) {
        if (format == value) return
        val color = argb
        format = value
        selectColor(color)
    }

    /** Only user edits drive the picker; picker-originated edits already have the right position. */
    fun acceptEditorInput(): Int? {
        val text = editor.text.toString()
        if (text == lastObservedInput) return null
        lastObservedInput = text
        return parsedColor?.also { lastValidArgb = it }
    }

    companion object {
        val Saver = listSaver<ColorEditorState, Any>(
            save = { listOf(it.argb, it.format.name, it.editor.text.toString()) },
            restore = { ColorEditorState(it[0] as Int, ColorValueFormat.valueOf(it[1] as String), it[2] as String) },
        )
    }
}

@Composable
internal fun rememberColorEditorState(seedColorArgb: Int): ColorEditorState =
    rememberSaveable(saver = ColorEditorState.Saver) { ColorEditorState(seedColorArgb) }

@Composable
internal fun ColorEditorControls(
    state: ColorEditorState,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    brightnessLabelColor: Color = LocalContentColor.current,
    inputContent: (@Composable (onColorChanged: (Int) -> Unit) -> Unit)? = null,
    previewContent: (@Composable () -> Unit)? = null,
) {
    val controller = rememberColorPickerController()
    // Typing must update the existing picker instead of resetting its initial color every frame.
    val initialColor = remember { Color(state.argb) }
    val brightness = remember(state.argb) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(state.argb, hsv)
        hsv[2]
    }
    val brightnessLabel = stringResource(R.string.theme_color_brightness)
    val pickerLabel = stringResource(R.string.theme_color_palette)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        HsvColorPicker(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(12.dp)
                .semantics { contentDescription = pickerLabel },
            controller = controller,
            initialColor = initialColor,
            onColorChanged = { envelope ->
                if (envelope.fromUser) {
                    state.selectColor(envelope.color.toArgb())
                }
            },
        )
        Text(
            text = brightnessLabel,
            style = MaterialTheme.typography.labelLarge,
            color = brightnessLabelColor,
        )
        BrightnessSlider(
            controller = controller,
            initialColor = initialColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics {
                    contentDescription = brightnessLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(brightness, 0f..1f)
                    setProgress {
                        controller.setBrightness(it.coerceIn(0f, 1f), fromUser = true)
                        true
                    }
                },
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            borderSize = 1.dp,
            borderRadius = 24.dp,
        )
        previewContent?.invoke()
        val syncPicker: (Int) -> Unit = { controller.selectByColor(Color(it), fromUser = false) }
        if (inputContent != null) inputContent(syncPicker) else ColorEditorInput(state, syncPicker)
    }
}

@Composable
internal fun ColorEditorInput(
    state: ColorEditorState,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    colors: TextFieldColors = FilledTextFieldDefaults.colors(),
) {
    val syncPicker by rememberUpdatedState(onColorChanged)
    LaunchedEffect(state) {
        snapshotFlow { state.editor.text.toString() }.collect {
            state.acceptEditorInput()?.let(syncPicker)
        }
    }
    val label = when (state.format) {
        ColorValueFormat.HEX -> R.string.theme_color_hex
        ColorValueFormat.RGB -> R.string.theme_color_rgb
        ColorValueFormat.OKLCH -> R.string.theme_color_oklch
    }
    val error = when (state.format) {
        ColorValueFormat.HEX -> R.string.theme_color_hex_error
        ColorValueFormat.RGB -> R.string.theme_color_rgb_error
        ColorValueFormat.OKLCH -> R.string.theme_color_oklch_error
    }
    val hint = when (state.format) {
        ColorValueFormat.HEX -> "#F06E8E"
        ColorValueFormat.RGB -> "rgb(240 110 142)"
        ColorValueFormat.OKLCH -> "oklch(70% 0.16 5)"
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTextField(
            state = state.editor,
            modifier = Modifier.fillMaxWidth(),
            colors = colors,
            label = { Text(stringResource(label)) },
            placeholder = { Text(hint) },
            leadingIcon = leadingContent,
            lineLimits = TextFieldLineLimits.SingleLine,
            errorMessage = if (state.parsedColor == null) stringResource(error) else null,
            keyboardOptions = KeyboardOptions(
                capitalization = if (state.format == ColorValueFormat.HEX) {
                    KeyboardCapitalization.Characters
                } else KeyboardCapitalization.None,
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
            ),
        )
        if (state.gamutMapped) {
            Text(
                text = stringResource(R.string.theme_color_gamut_mapped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
