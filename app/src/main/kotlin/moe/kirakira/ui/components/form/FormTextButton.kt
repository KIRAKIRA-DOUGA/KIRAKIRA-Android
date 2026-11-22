package moe.kirakira.ui.components.form

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import moe.kirakira.ui.components.button.FlatTextButton

internal enum class FormButtonEdge { START, END }

/** Aligns text to a form edge while retaining the official padding and touch target. */
@Composable
internal fun FormTextButton(
    onClick: () -> Unit,
    edge: FormButtonEdge,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val padding = ButtonDefaults.TextButtonContentPadding
    val direction = LocalLayoutDirection.current
    val offset = when (edge) {
        FormButtonEdge.START -> -padding.calculateStartPadding(direction)
        FormButtonEdge.END -> padding.calculateEndPadding(direction)
    }
    FlatTextButton(
        onClick = onClick,
        modifier = modifier.offset(x = offset).defaultMinSize(minWidth = 48.dp),
        enabled = enabled,
        loading = loading,
        contentPadding = padding,
        content = content,
    )
}
