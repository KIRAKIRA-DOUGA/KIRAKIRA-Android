package moe.kirakira.feature.imageviewer

import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import moe.kirakira.data.image.ImageSource

@Serializable
data class ViewerImage(
    val source: ImageSource,
    val description: String,
    val fileName: String = "KIRAKIRA",
    val sharedKey: String? = null,
    val thumbnail: ImageSource? = null,
)

internal fun ImageSource.coilModel(): Any = when (this) {
    is ImageSource.Resource -> id
    is ImageSource.ContentUri -> value.toUri()
    is ImageSource.RemoteUrl -> value
}
