package moe.kirakira.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun FlatTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ButtonShapes = pressToRoundButtonShapes(),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interactive = enabled && !loading
    ButtonLoadingIndicator(loading, shapes.shape, modifier) { buttonModifier ->
        TextButton(
            onClick = onClick,
            shapes = shapes,
            modifier = buttonModifier,
            enabled = interactive,
            colors = colors,
            contentPadding = contentPadding,
            content = content,
        )
    }
}
