package moe.kirakira.ui.components.button

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

internal object DestructiveActionDefaults {
    @Composable
    fun buttonColors(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
    )

    @Composable
    fun textButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun DestructiveActionIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.error,
    )
}

/** Flat destructive action for confirmations; disabled colors remain the Material defaults. */
@Composable
internal fun DestructiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    FlatTextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = DestructiveActionDefaults.textButtonColors(),
        contentPadding = contentPadding,
        content = content,
    )
}
