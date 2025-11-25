package com.claude.chat.di

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.mcp.McpManager
import com.claude.chat.data.model.McpServerPresets
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.data.remote.ClaudeApiClientImpl
import com.claude.chat.data.remote.OllamaClient
import com.claude.chat.data.remote.createHttpClient
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.data.repository.ChatRepositoryImpl
import com.claude.chat.domain.service.RagService
import com.claude.chat.domain.service.TextChunker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch

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
            weatherApiKey = null, // Use demo key, or set your OpenWeather API key here
            settingsStorage = settingsStorage
        )
    }

    val ollamaClient by lazy {
        OllamaClient(httpClient)
    }

    private val textChunker by lazy {
        TextChunker()
    }

    val ragService by lazy {
        RagService(ollamaClient, textChunker)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(apiClient, settingsStorage, mcpManager, ragService).also {
            // Auto-load RAG index on startup
            kotlinx.coroutines.MainScope().launch {
                try {
                    it.loadRagIndex()
                } catch (e: Exception) {
                    Napier.e("Failed to load RAG index on startup", e)
                }
            }
        }
    }

    /**
     * Initialize external MCP servers for orchestration
     * Call this after app startup to enable weather and currency tools
     */
    suspend fun initializeExternalMcpServers() {
        try {
            Napier.i("========================================")
            Napier.i("Initializing external MCP servers for orchestration...")
            Napier.i("========================================")

            // Check if servers already exist
            val existingServers = mcpManager.getExternalServers()
            Napier.i("Existing servers in storage: ${existingServers.size}")
            existingServers.forEach { server ->
                Napier.i("  - ${server.name} (${server.id}): enabled=${server.enabled}")
            }

            // Only add if not already present
            if (existingServers.none { it.id == "local-weather" }) {
                Napier.i("Adding Weather Server (http://localhost:3000)...")
                mcpManager.addExternalServer(
                    McpServerPresets.localWeatherServer(port = 3000)
                )
                Napier.i("✓ Weather Server added (port 3000)")
            } else {
                Napier.i("Weather Server already exists in storage")
            }

            if (existingServers.none { it.id == "local-currency" }) {
                Napier.i("Adding Currency Server (http://localhost:3001)...")
                mcpManager.addExternalServer(
                    McpServerPresets.localCurrencyServer(port = 3001)
                )
                Napier.i("✓ Currency Server added (port 3001)")
            } else {
                Napier.i("Currency Server already exists in storage")
            }

            Napier.i("========================================")
            Napier.i("✓ External MCP servers initialized successfully!")
            Napier.i("Available tools: ${mcpManager.availableTools.size}")
            if (mcpManager.availableTools.isEmpty()) {
                Napier.w("⚠️ WARNING: No MCP tools available!")
                Napier.w("This usually means:")
                Napier.w("  1. Servers failed to initialize (check logs above)")
                Napier.w("  2. Servers are disabled in storage")
                Napier.w("  3. Network connection issue")
            } else {
                mcpManager.availableTools.forEach { tool ->
                    Napier.i("  - ${tool.name}: ${tool.description}")
                }
            }
            Napier.i("========================================")
        } catch (e: Exception) {
            Napier.e("Failed to initialize external MCP servers", e)
            e.printStackTrace()
        }
    }
}
