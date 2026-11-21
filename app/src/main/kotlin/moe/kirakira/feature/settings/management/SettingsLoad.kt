package moe.kirakira.feature.settings.management

import moe.kirakira.core.network.ApiFailure
import moe.kirakira.ui.components.status.messageRes

internal data class SettingsLoad<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: Int? = null,
    val appending: Boolean = false,
) {
    fun readFailed(failure: ApiFailure): SettingsLoad<T> = copy(
        data = data.takeIf { failure.retainsContent },
        loading = false,
        error = failure.messageRes(),
        appending = appending && failure.retainsContent,
    )
}
