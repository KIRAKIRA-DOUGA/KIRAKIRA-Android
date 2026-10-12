package moe.kirakira.feature.imageviewer

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Local images or public HTTPS URLs. URI permissions remain the caller's responsibility. */
@Serializable
sealed interface ImageSource {
    /** A bitmap drawable; XML/vector drawables cannot be exported as encoded image bytes. */
    @Serializable
    data class Resource(@param:DrawableRes val id: Int) : ImageSource

    @Serializable
    data class ContentUri(val value: String) : ImageSource {
        init {
            require(value.toUri().scheme == "content") { "Only content URIs are supported" }
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
    val thumbnail: ImageSource? = null,
)

internal fun ImageSource.coilModel(): Any = when (this) {
    is ImageSource.Resource -> id
    is ImageSource.ContentUri -> value.toUri()
    is ImageSource.RemoteUrl -> value
}

internal fun ImageSource.open(context: Context) = when (this) {
    is ImageSource.Resource -> context.resources.openRawResource(id)
    is ImageSource.ContentUri -> checkNotNull(context.contentResolver.openInputStream(value.toUri()))
    is ImageSource.RemoteUrl -> error("Remote images must be downloaded before export")
}
