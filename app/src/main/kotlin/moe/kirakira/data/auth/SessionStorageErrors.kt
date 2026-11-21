package moe.kirakira.data.auth

import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.coroutines.CancellationException
import moe.kirakira.core.network.ApiException
import moe.kirakira.core.network.ApiFailure

/** Preserve only a safe category, never provider messages or sensitive exception causes. */
internal inline fun <T> sessionStorageOperation(
    fallback: ApiFailure = ApiFailure.STORAGE,
    block: () -> T,
): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: ApiException) {
    throw error
} catch (error: Exception) {
    // File and provider exceptions can wrap the actual filesystem failure.
    val storageFull = generateSequence<Throwable>(error) { it.cause }.take(16).any {
        it is ErrnoException && it.errno == OsConstants.ENOSPC
    }
    val failure = if (storageFull) ApiFailure.STORAGE_FULL else fallback
    throw ApiException(failure)
}
