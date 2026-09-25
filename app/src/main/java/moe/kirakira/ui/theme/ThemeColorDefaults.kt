package moe.kirakira.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val LocalClassicAccent = staticCompositionLocalOf { false }

/** Surface roles for app-owned chrome and pages; component colors come directly from ColorScheme. */
internal object ThemeColorDefaults {
    @Composable
    fun appBarContainerColor(default: Color = MaterialTheme.colorScheme.surfaceContainer): Color =
        if (LocalClassicAccent.current) MaterialTheme.colorScheme.surface else default

    @Composable
    fun pageBackgroundColor(default: Color = MaterialTheme.colorScheme.surface): Color =
        if (LocalClassicAccent.current) MaterialTheme.colorScheme.background else default
}
