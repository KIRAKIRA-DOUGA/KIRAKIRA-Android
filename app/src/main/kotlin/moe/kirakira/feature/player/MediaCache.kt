package moe.kirakira.feature.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.DefaultDashChunkSource
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient

private const val MEDIA_CACHE_BYTES = 256L * 1024 * 1024

/** SimpleCache locks its directory, so the process keeps exactly one instance. */
@OptIn(UnstableApi::class)
private object MediaCache {
    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }
}

/** Public media only: the upstream client carries no API cookies or authentication. */
@OptIn(UnstableApi::class)
internal fun publicMediaSource(
    context: Context,
    client: OkHttpClient,
    item: MediaItem,
    bypassCache: AtomicBoolean,
): MediaSource {
    val local = requireNotNull(item.localConfiguration)
    val upstream = OkHttpDataSource.Factory(client)
    val cacheFactory by lazy {
        CacheDataSource.Factory()
            .setCache(MediaCache.get(context))
            .setUpstreamDataSourceFactory(upstream)
    }
    val media = DataSource.Factory { CachedMediaDataSource(upstream, { cacheFactory.createDataSource() }, bypassCache) }
    // Adaptive manifests can change at the same URL while segments remain cacheable.
    val factory = when (Util.inferContentTypeForUriAndMimeType(local.uri, local.mimeType)) {
        C.CONTENT_TYPE_HLS -> HlsMediaSource.Factory(HlsDataSourceFactory { type ->
            if (type == C.DATA_TYPE_MEDIA) media.createDataSource() else upstream.createDataSource()
        })
        C.CONTENT_TYPE_DASH -> DashMediaSource.Factory(DefaultDashChunkSource.Factory(media), upstream)
        else -> DefaultMediaSourceFactory(media)
    }
    return factory.setLoadErrorHandlingPolicy(MediaCacheLoadErrorPolicy).createMediaSource(item)
}

@OptIn(UnstableApi::class)
private object MediaCacheLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long =
        if (loadErrorInfo.exception.isMediaCacheError()) 0L else super.getRetryDelayMsFor(loadErrorInfo)
}

/** Choose the source at each open so even a reused Media3 loader can retry at its current offset. */
@OptIn(UnstableApi::class)
private class CachedMediaDataSource(
    private val upstream: DataSource.Factory,
    private val cached: DataSource.Factory,
    private val bypassCache: AtomicBoolean,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        active?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long = onCacheError {
        val source = (if (bypassCache.get()) upstream else cached).createDataSource()
        active = source
        listeners.forEach(source::addTransferListener)
        source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = onCacheError {
        checkNotNull(active).read(buffer, offset, length)
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

    override fun close() {
        val source = active
        active = null
        onCacheError { source?.close() }
    }

    private inline fun <T> onCacheError(block: () -> T): T = try {
        block()
    } catch (error: IOException) {
        if (error.isMediaCacheError()) bypassCache.set(true)
        throw error
    }
}

@OptIn(UnstableApi::class)
private fun IOException.isMediaCacheError(): Boolean =
    this is Cache.CacheException || this is FileDataSource.FileDataSourceException
