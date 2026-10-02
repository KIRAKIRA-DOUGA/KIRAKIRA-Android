package moe.kirakira.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.kirakira.R
import moe.kirakira.ui.theme.KIRAKIRAPink
import moe.kirakira.ui.theme.KIRAKIRATheme
import moe.kirakira.ui.theme.rememberSeedColorScheme

@Composable
internal fun ThemePaletteOption(
    seedColor: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
) {
    // The label shares the button's interaction source so tapping it plays the same shape morph.
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(width)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            modifier = Modifier
                .size(48.dp)
                // The option column exposes the single radio node.
                .clearAndSetSemantics {},
            shapes = ToggleButtonDefaults.shapesFor(48.dp),
            interactionSource = interactionSource,
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            contentPadding = PaddingValues(0.dp),
        ) {
            // The official button clips all three color regions to its animated shape.
            ThemePaletteSwatch(
                seedColor = seedColor,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
            )
        }
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ThemePaletteSwatch(
    seedColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    // Keep each palette recognizable across light/dark mode using its light scheme.
    // Match the reference: one upper semicircle and two lower quadrants.
    val scheme = rememberSeedColorScheme(seedColor = seedColor, darkTheme = false)
    Canvas(modifier = modifier.size(32.dp).clip(shape)) {
        drawRect(color = scheme.primary, size = Size(size.width, size.height / 2))
        drawRect(
            color = scheme.surface,
            topLeft = Offset(0f, size.height / 2),
            size = Size(size.width / 2, size.height / 2),
        )
        drawRect(
            color = scheme.surfaceContainer,
            topLeft = Offset(size.width / 2, size.height / 2),
            size = Size(size.width / 2, size.height / 2),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ThemePaletteOptionPreview() {
    KIRAKIRATheme {
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePaletteOption(
                seedColor = KIRAKIRAPink,
                label = stringResource(R.string.theme_color_pink),
                selected = true,
                onClick = {},
            )
            ThemePaletteOption(
                seedColor = Color(0xFF537FE7),
                label = stringResource(R.string.theme_color_blue),
                selected = false,
                onClick = {},
            )
        }
    }
}
