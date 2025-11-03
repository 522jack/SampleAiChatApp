package com.claude.chat.di

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.data.remote.ClaudeApiClientImpl
import com.claude.chat.data.remote.createHttpClient
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.data.repository.ChatRepositoryImpl

/**
 * Simple dependency injection container
 */
class AppContainer {
    private val httpClient by lazy { createHttpClient() }

    private val apiClient: ClaudeApiClient by lazy {
        ClaudeApiClientImpl(httpClient)
    }

    private val settingsStorage by lazy {
        SettingsStorage()
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(apiClient, settingsStorage)
    }
}
