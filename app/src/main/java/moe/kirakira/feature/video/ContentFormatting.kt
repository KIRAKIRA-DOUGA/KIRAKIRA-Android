package moe.kirakira.feature.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import moe.kirakira.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun categoryText(category: String): String {
    val resource = when (category) {
        "anime" -> R.string.video_category_anime
        "music" -> R.string.video_category_music
        "otomad" -> R.string.video_category_otomad
        "tech" -> R.string.video_category_tech
        "design" -> R.string.video_category_design
        "game" -> R.string.video_category_game
        "misc" -> R.string.video_category_misc
        else -> return category
    }
    return stringResource(resource)
}

internal fun durationText(milliseconds: Long?): String {
    if (milliseconds == null) return "—"
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
internal fun dateText(timestamp: Long?): String {
    val locale = LocalConfiguration.current.locales[0]
    return timestamp?.takeIf { it >= 0 }?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(it))
    } ?: "—"
}
