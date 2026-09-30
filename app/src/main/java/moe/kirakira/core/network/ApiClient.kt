package moe.kirakira.core.network

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal enum class ApiFailure {
    NETWORK, TIMEOUT, SERVER, INVALID_RESPONSE, REJECTED, SESSION_EXPIRED, RATE_LIMITED, DAILY_LIMIT, STORAGE,
}

/** Sanitized failures never contain URLs, request bodies, credentials, or backend messages. */
internal class ApiException(val failure: ApiFailure) : Exception(failure.name)

internal class ApiClient(
    baseUrl: String,
    private val client: Call.Factory = defaultApiClient(),
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val base = baseUrl.toHttpUrl().also { require(it.isHttps) }
    val json = Json { ignoreUnknownKeys = true }

    suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        cookie: String? = null,
    ): T = decode(request(path, query = query, cookie = cookie))

    suspend inline fun <reified T> post(path: String, body: String, cookie: String? = null): T =
        decode(request(path, body = body, cookie = cookie))

    suspend inline fun <reified T> delete(path: String, body: String, cookie: String? = null): T =
        decode(request(path, body = body, cookie = cookie, method = "DELETE"))

    suspend inline fun <reified T> decode(body: String): T = withContext(decodeDispatcher) {
        try {
            json.decodeFromString<T>(body)
        } catch (_: SerializationException) {
            throw ApiException(ApiFailure.INVALID_RESPONSE)
        }
    }

    suspend fun request(
        path: String,
        body: String? = null,
        query: Map<String, String> = emptyMap(),
        cookie: String? = null,
        method: String = if (body == null) "GET" else "POST",
    ): String {
        // Paths are relative constants owned by API classes, never supplied by the server or UI.
        require(!path.startsWith('/') && !path.contains(":") && !path.contains(".."))
        val url = base.newBuilder().addPathSegments(path).apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("Cache-Control", "no-store")
            .apply {
                if (body != null) method(method, body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                if (cookie != null) header("Cookie", cookie)
            }.build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val failure = if (e is InterruptedIOException) ApiFailure.TIMEOUT else ApiFailure.NETWORK
                    continuation.resumeWithException(ApiException(failure))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            when {
                                it.code == 401 -> throw ApiException(ApiFailure.SESSION_EXPIRED)
                                it.code == 429 -> throw ApiException(ApiFailure.RATE_LIMITED)
                                it.code >= 500 -> throw ApiException(ApiFailure.SERVER)
                                !it.isSuccessful -> throw ApiException(ApiFailure.REJECTED)
                            }
                            // Bound untrusted response sizes, including chunked bodies.
                            val source = it.body.source()
                            if (source.request(MAX_RESPONSE_BYTES + 1)) {
                                throw ApiException(ApiFailure.INVALID_RESPONSE)
                            }
                            source.readUtf8()
                        }
                        continuation.resume(text)
                    } catch (error: ApiException) {
                        continuation.resumeWithException(error)
                    } catch (error: IOException) {
                        val failure = if (error is InterruptedIOException) ApiFailure.TIMEOUT else ApiFailure.NETWORK
                        continuation.resumeWithException(ApiException(failure))
                    }
                }
            })
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 1_048_576L
    }
}

private fun defaultApiClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    // Auth mutations and verification messages must never be replayed automatically.
    .retryOnConnectionFailure(false)
    .build()
