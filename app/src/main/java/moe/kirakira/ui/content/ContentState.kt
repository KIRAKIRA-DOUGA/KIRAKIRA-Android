package moe.kirakira.ui.content

import moe.kirakira.core.network.ApiFailure

internal data class ContentState<T>(val data: T? = null, val loading: Boolean = false, val error: ApiFailure? = null)
