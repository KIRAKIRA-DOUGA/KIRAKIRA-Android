package moe.kirakira.feature.imageviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.kirakira.R
import moe.kirakira.data.image.ImageExportRepository

internal enum class ImageExportOperation { SAVE, COPY }

/** Host-owned exports survive removal of the viewer entry. */
internal class ImageExportViewModel(
    private val context: Context,
    private val repository: ImageExportRepository,
) : ViewModel() {
    private val mutableOperations = MutableStateFlow<Map<String, ImageExportOperation>>(emptyMap())
    val operations = mutableOperations.asStateFlow()
    private val messages = Channel<Int>(Channel.BUFFERED)
    val feedback = messages.receiveAsFlow()
    private val copyMutex = Mutex()

    fun save(instanceId: String, image: ViewerImage) = export(instanceId, image, ImageExportOperation.SAVE)
    fun copy(instanceId: String, image: ViewerImage) = export(instanceId, image, ImageExportOperation.COPY)

    private fun export(instanceId: String, image: ViewerImage, operation: ImageExportOperation) {
        if (instanceId in mutableOperations.value) return
        mutableOperations.value += instanceId to operation
        viewModelScope.launch {
            val copy = operation == ImageExportOperation.COPY
            try {
                if (copy) {
                    // The clipboard is shared across viewer instances; preserve request order.
                    copyMutex.withLock {
                        val uri = repository.copyUri(image.source, image.fileName)
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                            ClipData.newUri(context.contentResolver, image.description, uri),
                        )
                        messages.send(R.string.image_copied)
                    }
                } else {
                    repository.save(image.source, image.fileName)
                    messages.send(R.string.image_saved)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages.send(if (copy) R.string.image_copy_failed else R.string.image_save_failed)
            } finally {
                mutableOperations.value -= instanceId
            }
        }
    }
}
