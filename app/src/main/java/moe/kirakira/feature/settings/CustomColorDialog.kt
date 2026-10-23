package moe.kirakira.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ActionDialog
import moe.kirakira.ui.components.ColorEditorControls
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.rememberColorEditorState

@Composable
internal fun CustomColorDialog(
    seedColorArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberColorEditorState(seedColorArgb)
    val parsedColor = state.parsedColor

    ActionDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_symbol_palette), contentDescription = null) },
        title = { Text(stringResource(R.string.theme_color_custom)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.theme_color_picker_description))
                ColorEditorControls(
                    state = state,
                    brightnessLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    inputContent = { onColorChanged ->
                        CustomColorInput(state = state, onColorChanged = onColorChanged)
                    },
                )
            }
        },
        actions = {
            ShadowButton(
                onClick = { parsedColor?.let(onConfirm) },
                enabled = parsedColor != null,
            ) {
                Text(stringResource(R.string.theme_color_apply))
            }
            TextButton(
                onClick = onDismiss,
            ) { Text(stringResource(R.string.theme_color_cancel)) }
        },
    )
}
