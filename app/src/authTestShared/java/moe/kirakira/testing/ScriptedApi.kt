package moe.kirakira.testing

import java.io.IOException
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineDispatcher
import moe.kirakira.core.network.ApiClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout

/** No sockets, DNS, OkHttpClient or production URL: every response is supplied by the test. */
internal class ScriptedApi {
    val requests = mutableListOf<Request>()
    private val actions = ArrayDeque<(MemoryCall) -> Unit>()
    private val calls = mutableListOf<MemoryCall>()

    fun client(dispatcher: CoroutineDispatcher) = ApiClient(
        BASE_URL,
        client = Call.Factory { request ->
            check(request.url.host == "auth.example.invalid") { "Only the isolated test host is allowed" }
            check(actions.isNotEmpty()) { "Unscripted API request: ${request.url.encodedPath}" }
            requests += request
            MemoryCall(request, actions.removeFirst()).also { calls += it }
        },
        decodeDispatcher = dispatcher,
    )

    fun respond(json: String, status: Int = 200, headers: Map<String, String> = emptyMap()) {
        actions += { it.respond(json, status, headers) }
    }

    fun fail(error: IOException) {
        actions += { it.fail(error) }
    }

    fun hold(): PendingResponse = PendingResponse().also { pending -> actions += { pending.call = it } }

    fun cancelAll() = calls.forEach { it.cancel() }

    internal class PendingResponse {
        internal var call: MemoryCall? = null
        val isCanceled: Boolean get() = call?.isCanceled() == true
        fun respond(json: String) = checkNotNull(call).respond(json)
    }

    internal class MemoryCall(
        private val request: Request,
        private val action: (MemoryCall) -> Unit,
    ) : Call {
        private var callback: Callback? = null
        private var executed = false
        private var canceled = false
        private var completed = false
        private val tags = mutableMapOf<Class<*>, Any>()

        override fun request(): Request = request
        override fun execute(): Response = error("Tests use the asynchronous API only")
        override fun enqueue(responseCallback: Callback) {
            check(!executed)
            executed = true
            callback = responseCallback
            if (canceled) fail(IOException("Canceled")) else action(this)
        }

        override fun cancel() {
            canceled = true
            if (callback != null && !completed) fail(IOException("Canceled"))
        }

        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = MemoryCall(request, action)
        override fun <T : Any> tag(type: KClass<T>): T? = tag(type.java)
        override fun <T> tag(type: Class<out T>): T? = type.cast(tags[type])
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T =
            tag(type.java, computeIfAbsent)
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
            tag(type) ?: computeIfAbsent().also { tags[type] = it }

        fun respond(json: String, status: Int = 200, headers: Map<String, String> = emptyMap()) {
            if (completed) return
            completed = true
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("Local fixture")
                .body(json.toResponseBody("application/json".toMediaType()))
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
            checkNotNull(callback).onResponse(this, response)
        }

        fun fail(error: IOException) {
            if (completed) return
            completed = true
            checkNotNull(callback).onFailure(this, error)
        }
    }

    companion object {
        const val BASE_URL = "https://auth.example.invalid/"
    }
}

internal fun Request.bodyText(): String = Buffer().also { body?.writeTo(it) }.readUtf8()
