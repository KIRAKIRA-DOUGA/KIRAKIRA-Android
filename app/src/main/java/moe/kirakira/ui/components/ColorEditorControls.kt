package moe.kirakira.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var argb by mutableIntStateOf(initialArgb or 0xFF000000.toInt())
        private set
    var format by mutableStateOf(initialFormat)
        private set
    var input by mutableStateOf(initialInput)
        private set
    private var parsedInput by mutableStateOf(parseColorValue(initialInput, initialFormat))
    val parsedColor: Int? get() = if (parsedInput != null) argb else null
    val gamutMapped: Boolean get() = parsedInput?.gamutMapped == true

    fun selectColor(color: Int) {
        argb = color or 0xFF000000.toInt()
        input = formatColorValue(argb, format)
        parsedInput = parseColorValue(input, format)
    }

    fun selectFormat(value: ColorValueFormat) {
        if (format == value) return
        format = value
        selectColor(argb)
    }

    fun updateInput(value: String) {
        input = value
        parsedInput = parseColorValue(value, format)
        parsedInput?.let { argb = it.argb }
    }

    companion object {
        val Saver = listSaver<ColorEditorState, Any>(
            save = { listOf(it.argb, it.format.name, it.input) },
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
) {
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
            value = state.input,
            onValueChange = { value ->
                state.updateInput(value)
                state.parsedColor?.let(onColorChanged)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(label)) },
            placeholder = { Text(hint) },
            leadingIcon = leadingContent,
            singleLine = true,
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
