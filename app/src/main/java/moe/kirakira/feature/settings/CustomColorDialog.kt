package moe.kirakira.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import moe.kirakira.R
import moe.kirakira.ui.theme.formatThemeColor
import moe.kirakira.ui.theme.parseThemeColor

@Composable
internal fun CustomColorDialog(
    seedColorArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var draftArgb by rememberSaveable { mutableIntStateOf(seedColorArgb) }
    var hex by rememberSaveable { mutableStateOf(formatThemeColor(seedColorArgb)) }
    val controller = rememberColorPickerController()
    // Recreating the dialog restores the draft, while typing must not reset the picker on every frame.
    val initialColor = remember { Color(draftArgb) }
    val brightness = remember(draftArgb) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(draftArgb, hsv)
        hsv[2]
    }
    val brightnessLabel = stringResource(R.string.theme_color_brightness)
    val pickerLabel = stringResource(R.string.theme_color_palette)
    val parsedColor = parseThemeColor(hex)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.theme_color_custom)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.theme_color_picker_description))
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
                            hex = formatThemeColor(draftArgb)
                        }
                    },
                )
                Text(
                    text = brightnessLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(draftArgb), CircleShape),
                        )
                        ThemePaletteSwatch(
                            seedColor = Color(draftArgb),
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = formatThemeColor(draftArgb),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { value ->
                        hex = value
                        parseThemeColor(value)?.let { argb ->
                            draftArgb = argb
                            controller.selectByColor(Color(argb), fromUser = false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedColor?.let(onConfirm) },
                enabled = parsedColor != null,
            ) {
                Text(stringResource(R.string.theme_color_apply))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) { Text(stringResource(R.string.theme_color_cancel)) }
        },
    )
}
