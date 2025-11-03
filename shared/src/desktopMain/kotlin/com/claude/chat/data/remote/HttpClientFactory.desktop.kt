package com.claude.chat.data.remote

import io.ktor.client.*
import io.ktor.client.engine.cio.*

/**
 * Desktop-specific HttpClient using CIO engine
 */
actual fun createHttpClient(): HttpClient {
    return HttpClient(CIO).configureClient()
}
