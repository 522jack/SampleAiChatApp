package com.claude.chat.di

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.mcp.McpManager
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.data.remote.ClaudeApiClientImpl
import com.claude.chat.data.remote.createHttpClient
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.data.repository.ChatRepositoryImpl

/**
 * Simple dependency injection container
 */
class AppContainer {
    val httpClient by lazy { createHttpClient() }

    private val apiClient: ClaudeApiClient by lazy {
        ClaudeApiClientImpl(httpClient)
    }

    private val settingsStorage by lazy {
        SettingsStorage()
    }

    val mcpManager by lazy {
        McpManager(
            httpClient = httpClient,
            weatherApiKey = null // Use demo key, or set your OpenWeather API key here
        )
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(apiClient, settingsStorage, mcpManager)
    }
}
