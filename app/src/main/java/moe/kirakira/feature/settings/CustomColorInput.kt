package moe.kirakira.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.core.color.ColorValueFormat
import moe.kirakira.ui.components.ColorEditorInput
import moe.kirakira.ui.components.ColorEditorState
import moe.kirakira.ui.components.FlatTextButton
import moe.kirakira.ui.components.MenuPopup
import moe.kirakira.ui.components.menuItemColors

@Composable
internal fun CustomColorInput(
    state: ColorEditorState,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FlatTextButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.theme_color_format, state.format.name))
                Icon(
                    painter = painterResource(R.drawable.ic_icon_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).rotate(90f),
                )
            }
            MenuPopup(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                maxHeight = 240.dp,
            ) {
                val colors = menuItemColors()
                val shapes = MenuDefaults.itemShapes()
                ColorValueFormat.entries.forEach { format ->
                    SelectableDropdownMenuItem(
                        selected = state.format == format,
                        text = { Text(format.name) },
                        onClick = {
                            state.selectFormat(format)
                            expanded = false
                        },
                        shapes = shapes,
                        colors = colors,
                        trailingContent = if (state.format == format) {
                            { Icon(painterResource(R.drawable.ic_icon_check), contentDescription = null) }
                        } else null,
                    )
                }
            }
        }
        ColorEditorInput(
            state = state,
            onColorChanged = onColorChanged,
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(state.argb), CircleShape)
                        .clearAndSetSemantics {},
                )
            },
        )
    }
}
