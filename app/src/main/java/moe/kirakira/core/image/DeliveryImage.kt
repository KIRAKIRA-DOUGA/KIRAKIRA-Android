package moe.kirakira.core.image

import moe.kirakira.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Rosales stores Cloudflare image IDs; older profiles may contain a complete HTTPS URL. */
internal fun deliveryImageUrl(value: String?, pixelWidth: Int? = null): String? {
    val source = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    publicMediaHttpUrl(source)?.let { return it.toString() }
    if (!source.matches(Regex("[a-zA-Z0-9_-]+"))) return null
    return BuildConfig.IMAGE_DELIVERY_BASE_URL.toHttpUrl()
        .newBuilder().addPathSegment(source)
        .addPathSegment(pixelWidth?.coerceIn(1, 2160)?.let { "w=$it,f=auto" } ?: "f=auto")
        .build().toString()
}

internal fun publicMediaUrl(value: String): String? = publicMediaHttpUrl(value)?.toString()

internal fun publicMediaHttpUrl(value: String): HttpUrl? = value.toHttpUrlOrNull()
    ?.takeIf(::isPublicMediaHttpUrl)

internal fun isPublicMediaHttpUrl(url: HttpUrl): Boolean =
    url.isHttps && url.username.isEmpty() && url.password.isEmpty()
