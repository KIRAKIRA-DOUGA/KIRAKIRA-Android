package moe.kirakira.core.network

import okhttp3.OkHttpClient

/**
 * Process-wide transport base. Purpose-specific clients derive from it with `newBuilder()`, sharing only
 * the connection pool and dispatcher; interceptors, cookies, timeouts and redirect rules stay per client.
 */
internal val sharedOkHttpClient: OkHttpClient by lazy { OkHttpClient() }
