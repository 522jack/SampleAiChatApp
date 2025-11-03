package com.claude.chat.data.remote

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*

/**
 * Android-specific HttpClient using OkHttp engine
 */
actual fun createHttpClient(): HttpClient {
    return HttpClient(OkHttp).configureClient()
}
