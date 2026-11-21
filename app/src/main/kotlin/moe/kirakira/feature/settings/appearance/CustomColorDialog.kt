package moe.kirakira.feature.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import moe.kirakira.R
import moe.kirakira.ui.components.button.FlatTextButton
import moe.kirakira.ui.components.button.ShadowButton
import moe.kirakira.ui.components.form.ColorEditorControls
import moe.kirakira.ui.components.form.rememberColorEditorState
import moe.kirakira.ui.components.overlay.ActionDialog
import moe.kirakira.ui.components.overlay.LocalMenuHazeState
import moe.kirakira.ui.components.overlay.menuHazeSource

@Composable
internal fun CustomColorDialog(
    seedColorArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberColorEditorState(seedColorArgb)
    val parsedColor = state.parsedColor
    val menuHazeState = rememberHazeState()

    ActionDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.menuHazeSource(menuHazeState),
        icon = { Icon(painterResource(R.drawable.ic_icon_palette), contentDescription = null) },
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
                        CompositionLocalProvider(LocalMenuHazeState provides menuHazeState) {
                            CustomColorInput(state = state, onColorChanged = onColorChanged)
                        }
                    },
                )
            }
        },
        actions = {
            ShadowButton(
                onClick = { state.parsedColor?.let(onConfirm) },
                enabled = parsedColor != null,
            ) {
                Text(stringResource(R.string.theme_color_apply))
            }
            FlatTextButton(
                contentPadding = ButtonDefaults.ContentPadding,
                onClick = onDismiss,
            ) { Text(stringResource(R.string.theme_color_cancel)) }
        },
    )
}
