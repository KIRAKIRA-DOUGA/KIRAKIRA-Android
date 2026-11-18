package moe.kirakira.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Surface roles shared by app-owned chrome, pages, and controls. */
internal object ThemeColorDefaults {
    @Composable
    fun appBarContainerColor(): Color = MaterialTheme.colorScheme.surface

    @Composable
    fun pageBackgroundColor(): Color = MaterialTheme.colorScheme.background

    /** Settings containers stay distinct from the default Surface role of segmented list items. */
    @Composable
    fun settingsBackgroundColor(): Color = MaterialTheme.colorScheme.surfaceContainer

    /** Use the color directly behind the control, including inside cards and sheets. */
    @Composable
    fun controlContainerColor(parentContainerColor: Color = MaterialTheme.colorScheme.surface): Color {
        val scheme = MaterialTheme.colorScheme
        return if (parentContainerColor == Color.White) scheme.surfaceContainerLow else scheme.surfaceContainerHigh
    }
}
