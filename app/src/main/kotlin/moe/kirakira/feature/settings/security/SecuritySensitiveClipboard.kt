package moe.kirakira.feature.settings.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import java.util.UUID

internal class SensitiveClipboard(context: Context) {
    private val manager = context.getSystemService(ClipboardManager::class.java)
    private val owner = UUID.randomUUID().toString()
    fun copy(value: String): Boolean = runCatching {
        val clip = ClipData.newPlainText("", value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
            putString("moe.kirakira.security.owner", owner)
        }
        // The current SDK exposes primaryClip as read-only to Kotlin; keep its Java setter.
        @Suppress("UsePropertyAccessSyntax")
        manager.setPrimaryClip(clip)
        true
    }.getOrDefault(false)
    fun clear() {
        runCatching {
            if (manager.primaryClipDescription?.extras?.getString("moe.kirakira.security.owner") == owner) {
                if (android.os.Build.VERSION.SDK_INT >= 28) manager.clearPrimaryClip()
                else {
                    @Suppress("UsePropertyAccessSyntax")
                    manager.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }
}
