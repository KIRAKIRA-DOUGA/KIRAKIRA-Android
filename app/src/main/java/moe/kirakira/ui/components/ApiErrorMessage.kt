package moe.kirakira.ui.components

import androidx.annotation.StringRes
import moe.kirakira.R
import moe.kirakira.core.network.ApiFailure

@StringRes
internal fun ApiFailure.messageRes(): Int = when (this) {
    ApiFailure.NETWORK -> R.string.api_network_error
    ApiFailure.TIMEOUT -> R.string.api_timeout
    ApiFailure.SERVER -> R.string.api_server_error
    ApiFailure.INVALID_RESPONSE -> R.string.api_invalid_response
    ApiFailure.REJECTED -> R.string.api_rejected
    ApiFailure.SESSION_EXPIRED -> R.string.account_session_expired
    ApiFailure.RATE_LIMITED -> R.string.auth_cooling_down
    ApiFailure.DAILY_LIMIT -> R.string.auth_daily_limit
    ApiFailure.STORAGE -> R.string.account_storage_error
}
