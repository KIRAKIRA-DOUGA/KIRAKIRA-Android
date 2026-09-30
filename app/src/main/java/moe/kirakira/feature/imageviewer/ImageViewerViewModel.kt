package moe.kirakira.feature.imageviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.kirakira.R

internal class ImageViewerViewModel(
    private val context: Context,
    private val image: ViewerImage,
) : ViewModel() {
    private val repository = ImageExportRepository(context)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val messages = Channel<Int>(Channel.BUFFERED)
    val feedback = messages.receiveAsFlow()

    fun save() = export(copy = false)
    fun copy() = export(copy = true)

    private fun export(copy: Boolean) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        viewModelScope.launch {
            try {
                if (copy) {
                    val uri = repository.copyUri(image)
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newUri(context.contentResolver, image.description, uri),
                    )
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) messages.send(R.string.image_copied)
                } else {
                    repository.save(image)
                    messages.send(R.string.image_saved)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                messages.send(if (copy) R.string.image_copy_failed else R.string.image_save_failed)
            } finally {
                mutableBusy.value = false
            }
        }
    }
}
