package moe.kirakira.data.profile

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import moe.kirakira.core.network.ApiClient
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure
import moe.kirakira.data.auth.AccountProfile
import moe.kirakira.data.auth.AuthRepository
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response

/** One editor owns its upload and commit checkpoints; credentials remain in this layer. */
internal class ProfileRepository(private val api: ApiClient, private val auth: AuthRepository) {
    val session = auth.session
    private var snapshot: AuthRepository.RequestSession? = null
    private var uploadedFile: File? = null
    private var uploadedId: String? = null
    private var remoteSaved = false
    private var refreshed: AccountProfile? = null
    var publishedRevision: Long? = null
        private set
    val needsCompletion: Boolean get() = remoteSaved

    suspend fun load(): AccountProfile {
        val request = auth.requestSession()
        snapshot = request
        uploadedFile = null
        uploadedId = null
        remoteSaved = false
        refreshed = null
        publishedRevision = null
        return guarded(request) { auth.fetchProfile(request) }
    }

    suspend fun save(profile: AccountProfile, avatar: File?): AccountProfile {
        val request = snapshot ?: throw ApiException(ApiFailure.SESSION_EXPIRED)
        return guarded(request) {
            val account = request.account ?: throw ApiException(ApiFailure.SESSION_EXPIRED)
            if (!remoteSaved) {
                if (avatar != null && (uploadedFile != avatar || uploadedId == null)) {
                    val upload = api.get<UploadDto>("user/avatar/preUpload", cookie = account.cookie())
                    if (!upload.success) throw ApiException(ApiFailure.REJECTED)
                    val id = upload.userAvatarFilename?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]+")) }
                        ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
                    uploadAvatar(upload.userAvatarUploadSignedUrl.orEmpty(), avatar)
                    checkCurrent(request)
                    uploadedFile = avatar
                    uploadedId = id
                }
                val body = buildJsonObject {
                    put("username", profile.username)
                    put("userNickname", profile.nickname)
                    put("signature", profile.signature)
                    put("gender", profile.gender)
                    put("userBirthday", profile.birthday)
                    put("avatar", if (avatar != null) uploadedId.orEmpty() else profile.avatar.orEmpty())
                    // Preserve the banner; the editor deliberately has no banner upload action.
                    put("userBannerImage", profile.banner.orEmpty())
                    put("label", api.json.encodeToJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(moe.kirakira.data.auth.ProfileLabel.serializer()),
                        profile.labels,
                    ))
                }
                val result = api.post<MutationDto>("user/update/info", body.toString(), account.cookie())
                if (!result.success) throw ApiException(ApiFailure.REJECTED)
                remoteSaved = true
            }
            val updated = refreshed ?: auth.fetchProfile(request).also { refreshed = it }
            checkCurrent(request)
            try {
                auth.publishProfile(request, updated) { publishedRevision = it }
            } catch (error: Exception) {
                publishedRevision = null
                throw error
            }
            // Publishing deliberately advances the revision; further requests use the new snapshot.
            snapshot = auth.requestSession()
            remoteSaved = false
            refreshed = null
            uploadedFile = null
            uploadedId = null
            updated
        }
    }

    suspend fun usernameAvailable(username: String): Boolean = auth.checkUsername(username)

    private fun checkCurrent(request: AuthRepository.RequestSession) {
        if (!auth.isCurrent(request)) throw CancellationException("Account changed")
    }

    private suspend fun <T> guarded(request: AuthRepository.RequestSession, block: suspend () -> T): T {
        checkCurrent(request)
        try {
            return block()
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            checkCurrent(request)
            if (error is ApiException && error.failure == ApiFailure.SESSION_EXPIRED) {
                auth.expireRequestSession(request)
            }
            throw error
        }
    }

    private suspend fun uploadAvatar(value: String, file: File) = withContext(Dispatchers.IO) {
        val url = value.toHttpUrlOrNull()?.takeIf {
            it.isHttps && it.host == "upload.imagedelivery.net" &&
                it.username.isEmpty() && it.password.isEmpty() && it.port == 443
        } ?: throw ApiException(ApiFailure.INVALID_RESPONSE)
        val request = Request.Builder().url(url).post(MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "avatar.jpg", file.asRequestBody("image/jpeg".toMediaType()))
            .build()).build()
        val response = suspendCancellableCoroutine<String> { continuation ->
            val call = uploadClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(ApiException(
                        if (e is InterruptedIOException) ApiFailure.TIMEOUT else ApiFailure.NETWORK))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (!it.isSuccessful) throw ApiException(ApiFailure.REJECTED)
                            val source = it.body.source()
                            if (source.request(1_048_577)) throw ApiException(ApiFailure.INVALID_RESPONSE)
                            source.readUtf8()
                        }
                        continuation.resume(text)
                    } catch (error: IOException) {
                        continuation.resumeWithException(ApiException(ApiFailure.NETWORK))
                    } catch (error: ApiException) {
                        continuation.resumeWithException(error)
                    }
                }
            })
        }
        if (!api.decode<MutationDto>(response).success) throw ApiException(ApiFailure.REJECTED)
    }

    private companion object {
        val uploadClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build()
    }
}

@Serializable
private data class UploadDto(
    val success: Boolean,
    val userAvatarUploadSignedUrl: String? = null,
    val userAvatarFilename: String? = null,
)

@Serializable
private data class MutationDto(val success: Boolean)
