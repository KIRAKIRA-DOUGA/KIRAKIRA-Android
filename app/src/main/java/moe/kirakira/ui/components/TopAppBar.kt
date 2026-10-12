package moe.kirakira.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared colors for ordinary app-owned top bars. */
@Composable
internal fun appTopAppBarColors(
    containerColor: Color = Color.Transparent,
    scrolledContainerColor: Color = Color.Transparent,
): TopAppBarColors {
    val scheme = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = scrolledContainerColor,
        navigationIconContentColor = scheme.onSurfaceVariant,
        titleContentColor = scheme.primary,
        actionIconContentColor = scheme.onSurfaceVariant,
    )
}
