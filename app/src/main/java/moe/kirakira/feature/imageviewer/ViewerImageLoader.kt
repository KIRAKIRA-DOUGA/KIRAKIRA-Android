package moe.kirakira.feature.imageviewer

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.asPainter
import coil3.decode.DataSource
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.saket.telephoto.subsamplingimage.ImageBitmapOptions
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import me.saket.telephoto.subsamplingimage.util.canBeSubSampled
import me.saket.telephoto.zoomable.ZoomableImageSource
import coil3.size.Size as CoilSize
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.floor
import kotlin.math.sqrt

internal enum class ViewerImagePhase { LOADING, PREPARING_TILES, READY, PREVIEW, FAILED }
internal enum class ViewerImageFallback { SOURCE_UNAVAILABLE, CACHE_READ, DECODER, REQUEST_TIMEOUT, FIRST_FRAME_TIMEOUT }

internal const val VIEWER_REQUEST_TIMEOUT_MILLIS = 45_000L
internal const val VIEWER_FIRST_FRAME_TIMEOUT_MILLIS = 15_000L
private const val MAX_PREVIEW_EDGE = 4096
private const val MAX_PREVIEW_PIXELS = 4_000_000L

/** One owner per retry. Neither requests nor decoder callbacks have access to navigation. */
internal class ViewerImageLoader(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val image: ViewerImage,
    private val instanceId: String,
    private val attempt: Int,
) {
    var phase by mutableStateOf(ViewerImagePhase.LOADING)
        private set
    var placeholder by mutableStateOf<Painter?>(null)
        private set
    var requestSucceeded by mutableStateOf(false)
        private set
    private var resolved by mutableStateOf(ZoomableImageSource.ResolveResult(delegate = null))
    private var preview: Painter? = null
    private var tiles: GuardedSubSamplingImageSource? = null
    private val closed = AtomicBoolean(false)
    private val decoderFailures = Channel<Unit>(Channel.CONFLATED)

    val source: ZoomableImageSource = object : ZoomableImageSource {
        @Composable
        override fun resolve(canvasSize: Flow<Size>): ZoomableImageSource.ResolveResult = resolved
    }

    suspend fun loadThumbnail(viewport: IntSize) {
        val thumbnail = image.thumbnail ?: image.source.takeIf { it is ImageSource.Resource } ?: return
        try {
            val result = withTimeoutOrNull(VIEWER_REQUEST_TIMEOUT_MILLIS) {
                imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(thumbnail.coilModel())
                        .size(minOf(viewport.width, 512), minOf(viewport.height, 512))
                        .scale(Scale.FIT)
                        .precision(Precision.INEXACT)
                        .maxBitmapSize(CoilSize(512, 512))
                        .build(),
                )
            }
            if (result is ErrorResult && result.throwable is CancellationException) throw result.throwable
            if (result is SuccessResult && !closed.get() && preview == null) {
                placeholder = result.image.asPainter(context)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A missing thumbnail must not fail the original image request.
        }
    }

    suspend fun load(viewport: IntSize) {
        ImageViewerDiagnostics.loading(instanceId, attempt, phase)
        try {
            val completed = withTimeoutOrNull(VIEWER_REQUEST_TIMEOUT_MILLIS) {
                val size = boundedPreviewSize(viewport)
                var result = request(size) ?: return@withTimeoutOrNull false
                acceptPreview(result)
                // A memory hit may outlive its disk entry. Refill it once, within the same deadline.
                if (image.source is ImageSource.RemoteUrl && result.dataSource == DataSource.MEMORY_CACHE &&
                    !hasDiskEntry(result)
                ) {
                    request(size, bypassMemory = true)?.let {
                        result = it
                        acceptPreview(it)
                    }
                }
                val rawSource = try {
                    subSamplingSource(result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    usePreview(ViewerImageFallback.CACHE_READ)
                    return@withTimeoutOrNull true
                }
                if (rawSource == null) {
                    usePreview(ViewerImageFallback.SOURCE_UNAVAILABLE)
                } else if (closed.get() || phase == ViewerImagePhase.PREVIEW) {
                    rawSource.closeSafely()
                } else {
                    val guarded = GuardedSubSamplingImageSource(rawSource) {
                        if (!closed.get()) decoderFailures.trySend(Unit)
                    }
                    tiles = guarded
                    resolved = ZoomableImageSource.ResolveResult(
                        delegate = ZoomableImageSource.SubSamplingDelegate(
                            source = guarded,
                            imageOptions = ImageBitmapOptions(from = (result.image as BitmapImage).bitmap),
                        ),
                    )
                }
                true
            }
            if (!closed.get()) {
                when (completed) {
                    null -> usePreview(ViewerImageFallback.REQUEST_TIMEOUT)
                    false -> fail()
                    true -> Unit
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            usePreview(ViewerImageFallback.CACHE_READ)
        }
    }

    suspend fun observeDecoderFailures() {
        for (ignored in decoderFailures) usePreview(ViewerImageFallback.DECODER)
    }

    fun markDisplayed() {
        if (!closed.get() && phase == ViewerImagePhase.PREPARING_TILES) updatePhase(ViewerImagePhase.READY)
    }

    fun usePreview(reason: ViewerImageFallback) {
        if (closed.get() || phase == ViewerImagePhase.PREVIEW || phase == ViewerImagePhase.FAILED) return
        ImageViewerDiagnostics.fallback(instanceId, attempt, reason)
        val painter = preview
        if (painter == null) {
            fail()
        } else {
            resolved = ZoomableImageSource.ResolveResult(ZoomableImageSource.PainterDelegate(painter))
            updatePhase(ViewerImagePhase.PREVIEW)
        }
        tiles?.close()
        tiles = null
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            decoderFailures.close()
            tiles?.close()
            tiles = null
        }
    }

    private suspend fun request(size: IntSize, bypassMemory: Boolean = false): SuccessResult? {
        val result = imageLoader.execute(
            ImageRequest.Builder(context)
                .data(image.source.coilModel())
                .size(size.width, size.height)
                .scale(Scale.FIT)
                .precision(Precision.EXACT)
                .allowHardware(false)
                .maxBitmapSize(CoilSize(size.width, size.height))
                .memoryCacheKeyExtra("viewer_preview", "bounded")
                .memoryCachePolicy(if (attempt > 0 || bypassMemory) CachePolicy.WRITE_ONLY else CachePolicy.ENABLED)
                .diskCachePolicy(if (attempt > 0) CachePolicy.WRITE_ONLY else CachePolicy.ENABLED)
                .build(),
        )
        if (result is ErrorResult && result.throwable is CancellationException) throw result.throwable
        return result as? SuccessResult
    }

    private fun acceptPreview(result: SuccessResult) {
        if (closed.get() || phase == ViewerImagePhase.PREVIEW || phase == ViewerImagePhase.FAILED) return
        val bitmap = (result.image as? BitmapImage)?.bitmap
        if (bitmap != null && (bitmap.width > MAX_PREVIEW_EDGE || bitmap.height > MAX_PREVIEW_EDGE ||
                bitmap.width.toLong() * bitmap.height > MAX_PREVIEW_PIXELS)
        ) {
            throw IOException("Viewer preview exceeds decode bounds")
        }
        preview = result.image.asPainter(context)
        placeholder = preview
        requestSucceeded = true
        updatePhase(ViewerImagePhase.PREPARING_TILES)
    }

    private suspend fun hasDiskEntry(result: SuccessResult): Boolean = withContext(Dispatchers.IO) {
        val cacheKey = result.diskCacheKey ?: return@withContext false
        imageLoader.diskCache?.openSnapshot(cacheKey)?.use { true } ?: false
    }

    private suspend fun subSamplingSource(result: SuccessResult): SubSamplingImageSource? {
        val bitmap = (result.image as? BitmapImage)?.bitmap ?: return null
        val preview = bitmap.asImageBitmap()
        var source: SubSamplingImageSource? = null
        var accepted = false
        try {
            when (val original = image.source) {
                is ImageSource.Resource -> source = SubSamplingImageSource.resource(original.id, preview)
                is ImageSource.ContentUri -> source = SubSamplingImageSource.contentUri(Uri.parse(original.value), preview)
                is ImageSource.RemoteUrl -> withContext(Dispatchers.IO) {
                    val cacheKey = result.diskCacheKey ?: return@withContext
                    val snapshot = imageLoader.diskCache?.openSnapshot(cacheKey) ?: return@withContext
                    // Store ownership before switching dispatchers, so prompt cancellation cannot leak the snapshot.
                    source = SubSamplingImageSource.file(snapshot.data, preview, onClose = snapshot::close)
                }
            }
            accepted = source?.canBeSubSampled(context) == true
            return if (accepted) source else null
        } finally {
            if (!accepted) source?.closeSafely()
        }
    }

    private fun fail() {
        if (!closed.get()) {
            resolved = ZoomableImageSource.ResolveResult(delegate = null)
            updatePhase(ViewerImagePhase.FAILED)
        }
    }

    private fun updatePhase(value: ViewerImagePhase) {
        if (phase != value) {
            phase = value
            ImageViewerDiagnostics.loading(instanceId, attempt, value)
        }
    }
}

private fun SubSamplingImageSource.closeSafely() {
    try {
        close()
    } catch (_: Exception) {
        // Cleanup must not replace cancellation or prevent a preview fallback.
    }
}

/** Bounds the fitted preview by both edge length and pixel area; tiles retain the original size. */
private fun boundedPreviewSize(viewport: IntSize): IntSize {
    val scale = minOf(
        1.0,
        MAX_PREVIEW_EDGE.toDouble() / maxOf(viewport.width, viewport.height),
        sqrt(MAX_PREVIEW_PIXELS.toDouble() / (viewport.width.toLong() * viewport.height)),
    )
    return IntSize(
        floor(viewport.width * scale).toInt().coerceAtLeast(1),
        floor(viewport.height * scale).toInt().coerceAtLeast(1),
    )
}
