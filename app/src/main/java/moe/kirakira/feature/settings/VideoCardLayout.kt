package moe.kirakira.feature.settings

import androidx.annotation.StringRes
import moe.kirakira.R

enum class VideoCardLayout(@param:StringRes val titleRes: Int, val columns: Int) {
    GRID(R.string.video_card_layout_grid, 2),
    LIST(R.string.video_card_layout_list, 1);

    companion object {
        fun fromStoredName(name: String?): VideoCardLayout = entries.firstOrNull { it.name == name } ?: GRID
    }
}
