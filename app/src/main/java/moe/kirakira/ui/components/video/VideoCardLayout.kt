package moe.kirakira.ui.components.video

import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import moe.kirakira.R

internal val VideoCardRowSpacing = 16.dp

enum class VideoCardLayout(@param:StringRes val titleRes: Int, val columns: Int) {
    GRID(R.string.video_card_layout_grid, 2),
    LIST(R.string.video_card_layout_list, 1),
}
