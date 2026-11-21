package moe.kirakira.ui.components.search

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import moe.kirakira.data.content.VideoTag

/** UI state for handing the tag field between the Activity and the picker window. */
internal class TagSearchAnchor {
    var boundsOnScreen: Rect? by mutableStateOf(null)
    var drawsInDialog by mutableStateOf(false)
    var returnTags: List<VideoTag>? by mutableStateOf(null)
    var scrollState: ScrollState? = null
}

internal val LocalTagSearchAnchor = staticCompositionLocalOf<TagSearchAnchor?> { null }
