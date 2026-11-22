package moe.kirakira.feature.imageviewer

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.internal.ImageRegionDecoder

/** Telephoto exposes this decoder interface publicly through SubSamplingImageSource.decoder(). */
internal class GuardedSubSamplingImageSource(
    private val source: SubSamplingImageSource,
    private val onFailure: () -> Unit,
) : SubSamplingImageSource {
    override val preview = source.preview
    private val lock = Any()
    private var closed = false
    private var opening = 0
    private var sourceClosed = false
    private val decoders = mutableSetOf<GuardedImageRegionDecoder>()

    override suspend fun decoder(): ImageRegionDecoder.Factory {
        try {
            val factory = source.decoder()
            return ImageRegionDecoder.Factory { params ->
                synchronized(lock) {
                    if (closed) throw CancellationException("Viewer image source disposed")
                    opening++
                }
                var guarded: GuardedImageRegionDecoder? = null
                var created: ImageRegionDecoder? = null
                try {
                    val decoder = factory.create(params)
                    created = decoder
                    val imageSize = decoder.imageSize
                    check(imageSize.width > 0 && imageSize.height > 0)
                    val guardedDecoder = GuardedImageRegionDecoder(decoder, imageSize, onFailure) { released ->
                        synchronized(lock) { decoders.remove(released) }
                        closeSourceIfIdle()
                    }
                    guarded = guardedDecoder
                    val rejected = synchronized(lock) {
                        if (!closed) decoders.add(guardedDecoder)
                        closed
                    }
                    if (rejected) throw CancellationException("Viewer image source disposed")
                    currentCoroutineContext().ensureActive()
                    guardedDecoder
                } catch (cancelled: CancellationException) {
                    if (guarded != null) guarded.close() else created?.closeSafely()
                    throw cancelled
                } catch (_: Exception) {
                    if (guarded != null) guarded.close() else created?.closeSafely()
                    onFailure()
                    // Cancel only this worker. Ordinary decode errors must not escape Telephoto's child job.
                    throw CancellationException("Viewer decoder initialization failed")
                } finally {
                    synchronized(lock) { opening-- }
                    closeSourceIfIdle()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onFailure()
            throw CancellationException("Viewer decoder source failed")
        }
    }

    override fun close() {
        val active = synchronized(lock) {
            if (closed) return
            closed = true
            decoders.toList()
        }
        active.forEach { it.close() }
        closeSourceIfIdle()
    }

    private fun closeSourceIfIdle() {
        val release = synchronized(lock) {
            (closed && opening == 0 && decoders.isEmpty() && !sourceClosed).also {
                if (it) sourceClosed = true
            }
        }
        if (release) {
            try {
                source.close()
            } catch (_: Exception) {
                // Cache snapshot disposal cannot change navigation or escape composition disposal.
            }
        }
    }
}

private fun ImageRegionDecoder.closeSafely() {
    try {
        close()
    } catch (_: Exception) {
        // Preserve the original cancellation/failure before this decoder was registered.
    }
}

/** Release native decoders only after every in-flight tile has left its decode call. */
private class GuardedImageRegionDecoder(
    private val decoder: ImageRegionDecoder,
    override val imageSize: IntSize,
    private val onFailure: () -> Unit,
    private val onReleased: (GuardedImageRegionDecoder) -> Unit,
) : ImageRegionDecoder {
    private val lock = Any()
    private var closed = false
    private var released = false
    private var inFlight = 0

    override suspend fun decodeRegion(region: IntRect, sampleSize: Int): ImageRegionDecoder.DecodeResult {
        synchronized(lock) {
            if (closed) throw CancellationException("Viewer decoder disposed")
            inFlight++
        }
        try {
            return decoder.decodeRegion(region, sampleSize)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onFailure()
            throw CancellationException("Viewer tile decoding failed")
        } finally {
            synchronized(lock) { inFlight-- }
            releaseIfIdle()
        }
    }

    override fun close() {
        synchronized(lock) { closed = true }
        releaseIfIdle()
    }

    private fun releaseIfIdle() {
        val release = synchronized(lock) {
            (closed && inFlight == 0 && !released).also { if (it) released = true }
        }
        if (release) {
            try {
                decoder.close()
            } catch (_: Exception) {
                // Disposal remains idempotent even if a platform decoder fails to release.
            } finally {
                onReleased(this)
            }
        }
    }
}
