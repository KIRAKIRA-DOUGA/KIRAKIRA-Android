package moe.kirakira.feature.imageviewer

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Local images or public HTTPS URLs. URI permissions remain the caller's responsibility. */
@Serializable
sealed interface ImageSource {
    @Serializable
    data class Resource(@param:DrawableRes val id: Int) : ImageSource

    @Serializable
    data class ContentUri(val value: String) : ImageSource {
        init {
            require(Uri.parse(value).scheme == "content") { "Only content URIs are supported" }
        }
    }

    @Serializable
    data class RemoteUrl(val value: String) : ImageSource {
        init {
            require(value.isPublicHttpsImageUrl()) {
                "Only public HTTPS images are supported"
            }
        }
    }
}

internal fun String.isPublicHttpsImageUrl(): Boolean {
    val url = toHttpUrlOrNull()
    return url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty()
}

@Serializable
data class ViewerImage(
    val source: ImageSource,
    val description: String,
    val fileName: String = "KIRAKIRA",
    val sharedKey: String? = null,
)

internal fun ImageSource.coilModel(): Any = when (this) {
    is ImageSource.Resource -> id
    is ImageSource.ContentUri -> Uri.parse(value)
    is ImageSource.RemoteUrl -> value
}

internal fun ImageSource.open(context: Context) = when (this) {
    is ImageSource.Resource -> context.resources.openRawResource(id)
    is ImageSource.ContentUri -> checkNotNull(context.contentResolver.openInputStream(Uri.parse(value)))
    is ImageSource.RemoteUrl -> error("Remote images must be downloaded before export")
}
