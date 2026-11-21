package moe.kirakira.ui.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntRect
import androidx.navigation3.runtime.NavMetadataKey
import moe.kirakira.ui.adaptive.sharedColumnEdge

internal enum class SettingsPane {
    DIRECTORY, CATEGORY, SUBPAGE,
}

internal val LocalSettingsPane = compositionLocalOf<SettingsPane?> { null }
internal val LocalSettingsDirectoryActive = compositionLocalOf { false }

internal data object SettingsPaneKey : NavMetadataKey<SettingsPane>
internal data object SettingsCategoryIndexKey : NavMetadataKey<Int>

internal data class SettingsPaneLayout(
    val directory: IntRect,
    val detail: IntRect,
    val rail: Boolean,
) {
    val adjacent: Boolean get() = directory.sharedColumnEdge(detail) != null
}
