package moe.kirakira

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration.Companion.seconds
import moe.kirakira.core.credentials.passwordCredentialGateway
import moe.kirakira.core.network.ApiClient
import moe.kirakira.data.auth.AuthApi
import moe.kirakira.data.auth.AuthRepository
import moe.kirakira.data.auth.SessionStore
import moe.kirakira.data.content.ContentRepository
import moe.kirakira.data.history.HistoryRepository
import moe.kirakira.data.kaomoji.RecentKaomojiStore
import moe.kirakira.data.image.ImageExportRepository
import moe.kirakira.data.profile.ProfileRepository
import moe.kirakira.data.security.SecurityRepository
import moe.kirakira.data.settings.AccountSettingsRepository
import okhttp3.OkHttpClient

/** Retained host dependencies; account-bound history work ends with this ViewModel's scope. */
internal class AppDependenciesViewModel(application: Application) : AndroidViewModel(application) {
    private val api = ApiClient(BuildConfig.API_BASE_URL)
    val credentials = passwordCredentialGateway(application)
    val auth = AuthRepository(
        AuthApi(api),
        SessionStore(application, BuildConfig.API_BASE_URL),
        BuildConfig.API_BASE_URL,
    )
    val content = ContentRepository(api, auth)
    val recentKaomoji = RecentKaomojiStore(application)
    val settings = AccountSettingsRepository(api, auth)
    val history = HistoryRepository(api, auth, viewModelScope)
    private val imageClient = OkHttpClient.Builder()
        .connectTimeout(15.seconds)
        .readTimeout(20.seconds)
        .callTimeout(30.seconds)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
    val imageExports = ImageExportRepository(application, imageClient)

    // Completion checkpoints belong to the editor/security entry, while HTTP resources are shared.
    fun createProfileRepository() = ProfileRepository(api, auth)

    fun createSecurityRepository() = SecurityRepository(api, auth)
}
