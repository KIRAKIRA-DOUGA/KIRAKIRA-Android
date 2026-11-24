package moe.kirakira.feature.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.File
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
internal fun publicMediaDataSourceFactory(context: Context, client: OkHttpClient): DataSource.Factory =
    CacheDataSource.Factory()
        .setCache(MediaCache.get(context))
        .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(client))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
