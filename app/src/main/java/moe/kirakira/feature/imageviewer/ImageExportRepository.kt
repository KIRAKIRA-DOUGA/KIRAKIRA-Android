package moe.kirakira.feature.imageviewer

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal class ImageExportRepository(private val context: Context) {
    private data class ImageFile(val name: String, val mime: String)
    private val imageClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    private fun metadata(image: ViewerImage, open: () -> InputStream): ImageFile {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, options) }
        val mime = requireNotNull(options.outMimeType) { "Unsupported image" }
        require(mime.startsWith("image/") && options.outWidth > 0 && options.outHeight > 0)
        val extension = requireNotNull(MimeTypeMap.getSingleton().getExtensionFromMimeType(mime))
        val base = image.fileName.substringBeforeLast('.', image.fileName)
            .replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(64).ifBlank { "KIRAKIRA" }
        return ImageFile("${base}_${UUID.randomUUID()}.$extension", mime)
    }

    suspend fun save(image: ViewerImage): Unit = withContext(Dispatchers.IO) {
        withSource(image) { open -> saveSource(image, open) }
    }

    private suspend fun saveSource(image: ViewerImage, open: () -> InputStream) {
        val file = metadata(image, open)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val uri = checkNotNull(
                resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                        put(MediaStore.Images.Media.MIME_TYPE, file.mime)
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/KIRAKIRA")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    },
                ),
            )
            var published = false
            try {
                open().use { input ->
                    checkNotNull(resolver.openOutputStream(uri)).use { output -> copy(input, output) }
                }
                currentCoroutineContext().ensureActive()
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0)
                published = true
            } finally {
                if (!published) resolver.delete(uri, null, null)
            }
        } else {
            saveLegacy(open, file)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(open: () -> InputStream, file: ImageFile) {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "KIRAKIRA")
        check(directory.isDirectory || directory.mkdirs())
        val target = File(directory, file.name)
        var complete = false
        try {
            open().use { input -> target.outputStream().use { copy(input, it) } }
            currentCoroutineContext().ensureActive()
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(file.mime), null)
            complete = true
        } finally {
            if (!complete) target.delete()
        }
    }

    suspend fun copyUri(image: ViewerImage): Uri = withContext(Dispatchers.IO) {
        withSource(image) { open ->
            val directory = File(context.cacheDir, "copied_images")
            check(directory.isDirectory || directory.mkdirs())
            val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
            val target = File(directory, metadata(image, open).name)
            var complete = false
            try {
                open().use { input -> target.outputStream().use { copy(input, it) } }
                currentCoroutineContext().ensureActive()
                FileProvider.getUriForFile(context, "${context.packageName}.images", target).also { complete = true }
            } finally {
                if (!complete) target.delete()
            }
        }
    }

    private suspend fun <T> withSource(image: ViewerImage, block: suspend (() -> InputStream) -> T): T {
        val remote = image.source as? ImageSource.RemoteUrl
        val temporary = remote?.let { download(it.value) }
        return try {
            block { temporary?.inputStream() ?: image.source.open(context) }
        } finally {
            temporary?.delete()
        }
    }

    private suspend fun download(value: String): File {
        val target = File.createTempFile("remote_image_", ".tmp", context.cacheDir)
        try {
            var url = value.toHttpUrl()
            repeat(MAX_REDIRECTS + 1) { redirectCount ->
                currentCoroutineContext().ensureActive()
                val call = imageClient.newCall(Request.Builder().url(url).header("Accept", "image/*").build())
                val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { response ->
                        if (response.code in 300..399) {
                            check(redirectCount < MAX_REDIRECTS)
                            val next = response.header("Location")?.let(url::resolve)
                            check(next != null && next.isHttps && next.username.isEmpty() && next.password.isEmpty())
                            url = next
                        } else {
                            check(response.isSuccessful)
                            val body = checkNotNull(response.body)
                            check(body.contentLength() <= MAX_REMOTE_BYTES)
                            body.byteStream().use { input ->
                                target.outputStream().use { output -> copy(input, output, MAX_REMOTE_BYTES) }
                            }
                            check(target.length() > 0)
                            return target
                        }
                    }
                } finally {
                    cancellation.dispose()
                }
            }
            error("Too many redirects")
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private suspend fun copy(input: InputStream, output: OutputStream, limit: Long = Long.MAX_VALUE) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            check(total <= limit)
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val MAX_REMOTE_BYTES = 32L * 1024 * 1024
        const val MAX_REDIRECTS = 3
    }
}
