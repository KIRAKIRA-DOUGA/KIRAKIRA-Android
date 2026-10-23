package moe.kirakira.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/** Window coordinates stay in composition and never enter search or saved state. */
internal class TagSearchAnchor {
    var boundsOnScreen: Rect? by mutableStateOf(null)
}

internal val LocalTagSearchAnchor = staticCompositionLocalOf<TagSearchAnchor?> { null }
