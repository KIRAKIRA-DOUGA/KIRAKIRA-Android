package moe.kirakira.core.image

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Rosales stores Cloudflare image IDs; older profiles may contain a complete HTTPS URL. */
internal fun deliveryImageUrl(value: String?, pixelWidth: Int? = null): String? {
    val source = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    source.toHttpUrlOrNull()?.let { url ->
        return url.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }?.toString()
    }
    if (!source.matches(Regex("[a-zA-Z0-9_-]+"))) return null
    return "https://kirafile.com/cdn-cgi/imagedelivery/Gyz90amG54C4b_dtJiRpYg/".toHttpUrl()
        .newBuilder().addPathSegment(source)
        .addPathSegment(pixelWidth?.coerceIn(1, 2160)?.let { "w=$it,f=auto" } ?: "f=auto")
        .build().toString()
}

internal fun publicMediaUrl(value: String): String? = value.toHttpUrlOrNull()
    ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }?.toString()
