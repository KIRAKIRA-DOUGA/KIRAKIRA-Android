package moe.kirakira.core.serialization

import kotlinx.serialization.json.Json

/** Shared lenient decoder for API responses and the app's own versioned JSON files. */
internal val AppJson = Json { ignoreUnknownKeys = true }
