package moe.kirakira.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

@Composable
internal fun dateOnlyText(timestamp: Long, style: Int = DateFormat.MEDIUM): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateInstance(style, locale).format(Date(timestamp))
}

/** Calendar-only values retain their date without passing through the device time zone. */
@Composable
internal fun dateOnlyText(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}

@Composable
internal fun dateTimeText(timestamp: Long?): String {
    val locale = LocalConfiguration.current.locales[0]
    return timestamp?.takeIf { it >= 0 }?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(it))
    } ?: "—"
}
