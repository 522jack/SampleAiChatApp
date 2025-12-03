package com.claude.chat.di

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.mcp.McpManager
import com.claude.chat.data.model.McpServerPresets
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.data.remote.ClaudeApiClientImpl
import com.claude.chat.data.remote.OllamaClient
import com.claude.chat.data.remote.SupportApiClient
import com.claude.chat.data.remote.createHttpClient
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.data.repository.ChatRepositoryImpl
import com.claude.chat.domain.manager.ApiConfigurationManager
import com.claude.chat.domain.manager.ChatHistoryManager
import com.claude.chat.domain.manager.ModelConfigurationManager
import com.claude.chat.domain.manager.RagConfigurationManager
import com.claude.chat.domain.manager.TechSpecManager
import com.claude.chat.domain.service.MessageSendingOrchestrator
import com.claude.chat.domain.service.ModelComparisonOrchestrator
import com.claude.chat.domain.service.RagService
import com.claude.chat.domain.service.TextChunker
import com.claude.chat.domain.service.ToolExecutionService
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

    val settingsStorage by lazy {
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

    val supportApiClient by lazy {
        SupportApiClient(
            httpClient = httpClient,
            baseUrl = "http://localhost:8080"
        )
    }

    // ============================================================================
    // Business Logic Services
    // ============================================================================

    /**
     * Service for executing tool loops with Claude API
     */
    private val toolExecutionService by lazy {
        ToolExecutionService(apiClient, mcpManager)
    }

    /**
     * Orchestrator for comparing responses from multiple Claude models
     */
    private val modelComparisonOrchestrator by lazy {
        ModelComparisonOrchestrator(apiClient)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(
            apiClient = apiClient,
            settingsStorage = settingsStorage,
            mcpManager = mcpManager,
            ragService = ragService,
            toolExecutionService = toolExecutionService,
            modelComparisonOrchestrator = modelComparisonOrchestrator
        ).also {
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

    // ============================================================================
    // Configuration Managers
    // ============================================================================

    /**
     * Manager for API configuration (API key, system prompt, temperature)
     */
    val apiConfigurationManager by lazy {
        ApiConfigurationManager(chatRepository)
    }

    /**
     * Manager for model configuration (JSON mode, Tech Spec, comparison mode, MCP)
     */
    val modelConfigurationManager by lazy {
        ModelConfigurationManager(chatRepository)
    }

    /**
     * Manager for RAG configuration (RAG mode, reranking, document indexing)
     */
    val ragConfigurationManager by lazy {
        RagConfigurationManager(chatRepository, ollamaClient)
    }

    /**
     * Manager for Tech Spec mode state machine
     */
    val techSpecManager by lazy {
        TechSpecManager()
    }

    /**
     * Manager for chat message history operations
     */
    val chatHistoryManager by lazy {
        ChatHistoryManager(chatRepository)
    }

    /**
     * Orchestrator for message sending with RAG and tool support
     */
    val messageSendingOrchestrator by lazy {
        MessageSendingOrchestrator(chatRepository, techSpecManager)
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
