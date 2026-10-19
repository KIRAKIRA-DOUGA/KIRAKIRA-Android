package moe.kirakira.feature.player

import android.content.Context
import android.media.AudioManager
import android.os.Build
import kotlin.math.roundToInt

/** Controls only the system media stream and respects fixed-volume and policy restrictions. */
internal class MediaVolumeController(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    fun read(): Float? {
        val manager = audio ?: return null
        if (manager.isVolumeFixed) return null
        val minimum = minimum(manager)
        val range = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) - minimum
        if (range <= 0) return null
        return ((manager.getStreamVolume(AudioManager.STREAM_MUSIC) - minimum).toFloat() / range).coerceIn(0f, 1f)
    }

    fun set(fraction: Float): Float? {
        val manager = audio ?: return null
        if (manager.isVolumeFixed) return null
        val minimum = minimum(manager)
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = minimum + (fraction.coerceIn(0f, 1f) * (maximum - minimum)).roundToInt()
        try {
            if (target != manager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
        } catch (_: SecurityException) {
            // Device policy (including DND) can reject a change; report the actual volume.
        }
        return read()
    }

    private fun minimum(manager: AudioManager): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
}
