package moe.kirakira.data.image

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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class ImageExportRepository(
    private val context: Context,
    private val imageClient: OkHttpClient,
) {
    private data class ImageFile(val name: String, val mime: String)

    private fun metadata(fileName: String, open: () -> InputStream): ImageFile {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, options) }
        val mime = requireNotNull(options.outMimeType) { "Unsupported image" }
        require(mime.startsWith("image/") && options.outWidth > 0 && options.outHeight > 0)
        val extension = requireNotNull(MimeTypeMap.getSingleton().getExtensionFromMimeType(mime))
        val base = fileName.substringBeforeLast('.', fileName)
            .replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(64).ifBlank { "KIRAKIRA" }
        return ImageFile("${base}_${UUID.randomUUID()}.$extension", mime)
    }

    suspend fun save(source: ImageSource, fileName: String): Unit = withContext(Dispatchers.IO) {
        withSource(source) { open -> saveSource(fileName, open) }
    }

    private suspend fun saveSource(fileName: String, open: () -> InputStream) {
        val file = metadata(fileName, open)
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

    suspend fun copyUri(source: ImageSource, fileName: String): Uri = withContext(Dispatchers.IO) {
        withSource(source) { open ->
            val directory = File(context.cacheDir, "copied_images")
            check(directory.isDirectory || directory.mkdirs())
            val cutoff = System.currentTimeMillis() - 7.days.inWholeMilliseconds
            directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
            val target = File(directory, metadata(fileName, open).name)
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

    private suspend fun <T> withSource(source: ImageSource, block: suspend (() -> InputStream) -> T): T {
        val remote = source as? ImageSource.RemoteUrl
        // Own the temporary file before suspending, including prompt cancellation on dispatcher return.
        val temporary = remote?.let { File.createTempFile("remote_image_", ".tmp", context.cacheDir) }
        return try {
            if (remote != null && temporary != null) download(remote.value, temporary)
            block { temporary?.inputStream() ?: source.open(context) }
        } finally {
            temporary?.delete()
        }
    }

    private suspend fun download(value: String, target: File) {
        var url = value.toHttpUrl()
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val next = downloadResponse(url, target, redirectCount) ?: return
            url = next
        }
        error("Too many redirects")
    }

    private suspend fun downloadResponse(url: HttpUrl, target: File, redirectCount: Int): HttpUrl? =
        suspendCancellableCoroutine { continuation ->
            val call = imageClient.newCall(Request.Builder().url(url).header("Accept", "image/*").build())
            // Keep the bridge suspended through body consumption so cancellation interrupts both headers and reads.
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val next = response.use {
                            if (response.code in 300..399) {
                                check(redirectCount < MAX_REDIRECTS)
                                val location = response.header("Location")?.let(url::resolve)
                                check(location != null && location.isHttps &&
                                    location.username.isEmpty() && location.password.isEmpty())
                                location
                            } else {
                                check(response.isSuccessful)
                                val body = checkNotNull(response.body)
                                check(body.contentLength() <= MAX_REMOTE_BYTES)
                                body.byteStream().use { input ->
                                    target.outputStream().use { output ->
                                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                        var total = 0L
                                        while (continuation.isActive) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            total += count
                                            check(total <= MAX_REMOTE_BYTES)
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                }
                                check(target.length() > 0)
                                null
                            }
                        }
                        if (continuation.isActive) continuation.resume(next)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally {
                        if (!continuation.isActive && call.isCanceled()) target.delete()
                    }
                }
            })
    }

    private suspend fun copy(input: InputStream, output: OutputStream): Unit = withContext(Dispatchers.IO) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    private companion object {
        const val MAX_REMOTE_BYTES = 32L * 1024 * 1024
        const val MAX_REDIRECTS = 3
    }
}
