package moe.kirakira.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import moe.kirakira.ui.components.ExpressiveMenuPopup

@Composable
internal fun CustomColorInput(
    state: ColorEditorState,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val itemColors = MenuDefaults.selectableItemColors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        selectedTrailingContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.theme_color_format, state.format.name))
                Icon(
                    painter = painterResource(R.drawable.ic_symbol_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).rotate(90f),
                )
            }
            ExpressiveMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShape(index = 0, count = 1),
                    modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    ColorValueFormat.entries.forEachIndexed { index, format ->
                        SelectableDropdownMenuItem(
                            selected = state.format == format,
                            text = { Text(format.name) },
                            onClick = {
                                state.selectFormat(format)
                                expanded = false
                            },
                            shapes = MenuDefaults.itemShape(index = index, count = ColorValueFormat.entries.size),
                            colors = itemColors,
                            trailingContent = if (state.format == format) {
                                {
                                    Icon(painterResource(R.drawable.ic_symbol_check), contentDescription = null)
                                }
                            } else null,
                        )
                    }
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
