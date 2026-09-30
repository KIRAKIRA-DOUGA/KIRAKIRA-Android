package moe.kirakira.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import moe.kirakira.R

@Composable
internal fun DanmakuColorEditor(
    seedColorArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftArgb by rememberSaveable { mutableIntStateOf(seedColorArgb or 0xFF000000.toInt()) }
    var hex by rememberSaveable { mutableStateOf(formatDanmakuColor(seedColorArgb)) }
    val controller = rememberColorPickerController()
    // Recreating the editor restores the draft, while typing must not reset the picker on every frame.
    val initialColor = remember { Color(draftArgb) }
    val brightness = remember(draftArgb) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(draftArgb, hsv)
        hsv[2]
    }
    val brightnessLabel = stringResource(R.string.theme_color_brightness)
    val pickerLabel = stringResource(R.string.theme_color_palette)
    val parsedColor = parseDanmakuColor(hex)

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.danmaku_style_custom), style = MaterialTheme.typography.headlineSmall)
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
                    draftArgb = envelope.color.copy(alpha = 1f).toArgb()
                    hex = formatDanmakuColor(draftArgb)
                }
            },
        )
        Text(text = brightnessLabel, style = MaterialTheme.typography.labelLarge)
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
        Box(
            modifier = Modifier.size(40.dp).background(Color(draftArgb), MaterialTheme.shapes.small),
        )
        OutlinedTextField(
            value = hex,
            onValueChange = { value ->
                hex = value
                parseDanmakuColor(value)?.let { argb ->
                    draftArgb = argb
                    controller.selectByColor(Color(argb), fromUser = false)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.theme_color_hex)) },
            singleLine = true,
            isError = parsedColor == null,
            supportingText = {
                if (parsedColor == null) Text(stringResource(R.string.theme_color_hex_error))
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.theme_color_cancel)) }
            Button(onClick = { parsedColor?.let { onConfirm(it and 0xFFFFFF) } }, enabled = parsedColor != null) {
                Text(stringResource(R.string.theme_color_apply))
            }
        }
    }
}

private fun formatDanmakuColor(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

private fun parseDanmakuColor(value: String): Int? {
    val hex = value.removePrefix("#")
    if (hex.length != 6 || hex.any { it !in "0123456789abcdefABCDEF" }) return null
    return hex.toIntOrNull(16)?.or(0xFF000000.toInt())
}
