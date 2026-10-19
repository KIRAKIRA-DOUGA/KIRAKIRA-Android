package moe.kirakira.feature.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.components.ColorEditorControls
import moe.kirakira.ui.components.ShadowButton
import moe.kirakira.ui.components.rememberColorEditorState

@Composable
internal fun DanmakuColorEditor(
    seedColorArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberColorEditorState(seedColorArgb)
    val parsedColor = state.parsedColor

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.danmaku_style_custom), style = MaterialTheme.typography.headlineSmall)
        ColorEditorControls(state = state, spacing = 16.dp) {
            Box(
                modifier = Modifier.size(40.dp).background(Color(state.argb), MaterialTheme.shapes.small),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.theme_color_cancel)) }
            ShadowButton(onClick = { parsedColor?.let { onConfirm(it and 0xFFFFFF) } }, enabled = parsedColor != null) {
                Text(stringResource(R.string.theme_color_apply))
            }
        }
    }
}
