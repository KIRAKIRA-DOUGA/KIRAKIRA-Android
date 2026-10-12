package moe.kirakira.data.image

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Local images or public HTTPS URLs. URI permissions remain the caller's responsibility. */
@Serializable
@SerialName("moe.kirakira.feature.imageviewer.ImageSource")
sealed interface ImageSource {
    /** A bitmap drawable; XML/vector drawables cannot be exported as encoded image bytes. */
    @Serializable
    @SerialName("moe.kirakira.feature.imageviewer.ImageSource.Resource")
    data class Resource(@param:DrawableRes val id: Int) : ImageSource

    @Serializable
    @SerialName("moe.kirakira.feature.imageviewer.ImageSource.ContentUri")
    data class ContentUri(val value: String) : ImageSource {
        init {
            require(value.toUri().scheme == "content") { "Only content URIs are supported" }
        }
    }

    @Serializable
    @SerialName("moe.kirakira.feature.imageviewer.ImageSource.RemoteUrl")
    data class RemoteUrl(val value: String) : ImageSource {
        init {
            require(value.isPublicHttpsImageUrl()) { "Only public HTTPS images are supported" }
        }
    }
}

internal fun String.isPublicHttpsImageUrl(): Boolean {
    val url = toHttpUrlOrNull()
    return url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty()
}

internal fun ImageSource.open(context: Context) = when (this) {
    is ImageSource.Resource -> context.resources.openRawResource(id)
    is ImageSource.ContentUri -> checkNotNull(context.contentResolver.openInputStream(value.toUri()))
    is ImageSource.RemoteUrl -> error("Remote images must be downloaded before export")
}
