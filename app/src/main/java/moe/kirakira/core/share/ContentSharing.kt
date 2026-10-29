package moe.kirakira.core.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

internal fun profileShareUrl(uid: Long): String = "https://kirakira.moe/user/$uid"

internal fun videoShareUrl(id: Int): String = "https://kirakira.moe/video/kv$id"

/** Opens the system share sheet from a UI context; cancellation has no result to report. */
internal fun shareContent(context: Context, url: String, title: String?): Boolean {
    val heading = title?.takeIf { it.isNotBlank() }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, heading?.let { "$it\n$url" } ?: url)
        heading?.let { putExtra(Intent.EXTRA_TITLE, it) }
    }
    return try {
        context.startActivity(Intent.createChooser(intent, null))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
