package moe.kirakira.ui.components

import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import moe.kirakira.ui.theme.ThemeColorDefaults

internal object AppChipDefaults {
    @Composable
    fun filterColors(parentContainerColor: Color = MaterialTheme.colorScheme.surface): SelectableChipColors =
        FilterChipDefaults.filterChipColors().withAppColors(parentContainerColor)

    @Composable
    fun inputColors(parentContainerColor: Color = MaterialTheme.colorScheme.surface): SelectableChipColors =
        InputChipDefaults.inputChipColors().withAppColors(parentContainerColor)

    @Composable
    private fun SelectableChipColors.withAppColors(parentContainerColor: Color): SelectableChipColors {
        val scheme = MaterialTheme.colorScheme
        return copy(
            containerColor = ThemeColorDefaults.controlContainerColor(parentContainerColor),
            labelColor = scheme.onSurface,
            leadingIconColor = scheme.onSurfaceVariant,
            trailingIconColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.primary,
            selectedLabelColor = scheme.onPrimary,
            selectedLeadingIconColor = scheme.onPrimary,
            selectedTrailingIconColor = scheme.onPrimary,
        )
    }
}
