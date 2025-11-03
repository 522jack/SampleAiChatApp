package com.claude.chat.data.remote

import io.ktor.client.*
import io.ktor.client.engine.darwin.*

/**
 * iOS-specific HttpClient using Darwin engine
 */
actual fun createHttpClient(): HttpClient {
    return HttpClient(Darwin).configureClient()
}
