package moe.kirakira.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Surface roles for app-owned chrome and pages; component colors come directly from ColorScheme. */
internal object ThemeColorDefaults {
    @Composable
    fun appBarContainerColor(): Color = MaterialTheme.colorScheme.surface

    @Composable
    fun pageBackgroundColor(): Color = MaterialTheme.colorScheme.background

    /** Settings containers stay distinct from the default Surface role of segmented list items. */
    @Composable
    fun settingsBackgroundColor(): Color = MaterialTheme.colorScheme.surfaceContainer
}
