package com.claude.chat.data.repository

import com.claude.chat.data.model.McpTool
import com.claude.chat.data.model.StreamChunk
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.ModelComparisonResponse
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chat operations
 */
interface ChatRepository {
    /**
     * Initialize MCP tools
     */
    suspend fun initializeMcpTools()

    /**
     * Get available MCP tools
     */
    fun getAvailableMcpTools(): List<McpTool>

    /**
     * Check if MCP is enabled
     */
    suspend fun getMcpEnabled(): Boolean

    /**
     * Set MCP enabled state
     */
    suspend fun saveMcpEnabled(enabled: Boolean)

    /**
     * Call an MCP tool with given arguments
     */
    suspend fun callMcpTool(toolName: String, arguments: Map<String, String>): Result<String>

    /**
     * Send a message and receive streaming response
     */
    suspend fun sendMessage(
        messages: List<Message>,
        systemPrompt: String? = null
    ): Flow<String>

    /**
     * Send a message and receive streaming response with token usage
     */
    suspend fun sendMessageWithUsage(
        messages: List<Message>,
        systemPrompt: String? = null
    ): Flow<StreamChunk>

    /**
     * Send a message with tool use support and automatic tool execution loop
     */
    suspend fun sendMessageWithToolLoop(
        messages: List<Message>,
        systemPrompt: String? = null,
        onToolCall: suspend (toolName: String, arguments: Map<String, String>) -> Result<String>
    ): Flow<StreamChunk>

    /**
     * Send a message to multiple models and get comparison responses
     */
    suspend fun sendMessageComparison(
        messages: List<Message>,
        systemPrompt: String? = null
    ): Result<ModelComparisonResponse>

    /**
     * Get all messages from history
     */
    suspend fun getMessages(): List<Message>

    /**
     * Save messages to history
     */
    suspend fun saveMessages(messages: List<Message>)

    /**
     * Clear all messages
     */
    suspend fun clearMessages()

    /**
     * Get API key
     */
    suspend fun getApiKey(): String?

    /**
     * Save API key
     */
    suspend fun saveApiKey(apiKey: String)

    /**
     * Get system prompt
     */
    suspend fun getSystemPrompt(): String?

    /**
     * Save system prompt
     */
    suspend fun saveSystemPrompt(prompt: String)

    /**
     * Get JSON mode setting
     */
    suspend fun getJsonMode(): Boolean

    /**
     * Save JSON mode setting
     */
    suspend fun saveJsonMode(enabled: Boolean)

    /**
     * Get Tech Spec mode setting
     */
    suspend fun getTechSpecMode(): Boolean

    /**
     * Save Tech Spec mode setting
     */
    suspend fun saveTechSpecMode(enabled: Boolean)

    /**
     * Get selected model
     */
    suspend fun getSelectedModel(): String

    /**
     * Save selected model
     */
    suspend fun saveSelectedModel(modelId: String)

    /**
     * Get temperature setting
     */
    suspend fun getTemperature(): Double

    /**
     * Save temperature setting
     */
    suspend fun saveTemperature(temperature: Double)

    /**
     * Get model comparison mode setting
     */
    suspend fun getModelComparisonMode(): Boolean

    /**
     * Save model comparison mode setting
     */
    suspend fun saveModelComparisonMode(enabled: Boolean)

    /**
     * Check if API key is configured
     */
    suspend fun isApiKeyConfigured(): Boolean

    /**
     * Compress message history by summarizing old messages
     * Returns true if compression was performed
     */
    suspend fun compressMessages(): Result<Boolean>

    // ============================================================================
    // RAG (Retrieval-Augmented Generation) Methods
    // ============================================================================

    /**
     * Index a document for RAG
     */
    suspend fun indexDocument(title: String, content: String): Result<String>

    /**
     * Search for relevant content in indexed documents
     */
    suspend fun searchRagIndex(query: String, topK: Int = 5): Result<String>

    /**
     * Get all indexed documents
     */
    suspend fun getIndexedDocuments(): List<com.claude.chat.data.model.RagDocument>

    /**
     * Remove a document from RAG index
     */
    suspend fun removeRagDocument(documentId: String): Boolean

    /**
     * Clear RAG index
     */
    suspend fun clearRagIndex()

    /**
     * Save RAG index to storage
     */
    suspend fun saveRagIndex(): Result<Boolean>

    /**
     * Load RAG index from storage
     */
    suspend fun loadRagIndex(): Result<Boolean>

    /**
     * Check if RAG mode is enabled
     */
    suspend fun getRagMode(): Boolean

    /**
     * Set RAG mode
     */
    suspend fun saveRagMode(enabled: Boolean)

    /**
     * Check if RAG reranking is enabled
     */
    suspend fun getRagRerankingEnabled(): Boolean

    /**
     * Set RAG reranking enabled state
     */
    suspend fun saveRagRerankingEnabled(enabled: Boolean)

    // ============================================================================
    // User Profile Methods
    // ============================================================================

    /**
     * Load user profile from JSON content
     */
    suspend fun loadUserProfile(jsonContent: String): Result<com.claude.chat.domain.model.UserProfile>

    /**
     * Get current user profile
     */
    suspend fun getUserProfile(): com.claude.chat.domain.model.UserProfile?

    /**
     * Clear user profile
     */
    suspend fun clearUserProfile(): Boolean

    // ============================================================================
    // Model Provider Methods
    // ============================================================================

    /**
     * Get current model provider (CLAUDE or OLLAMA)
     */
    suspend fun getModelProvider(): String

    /**
     * Save model provider
     */
    suspend fun saveModelProvider(provider: String)

    /**
     * Get Ollama base URL
     */
    suspend fun getOllamaBaseUrl(): String

    /**
     * Save Ollama base URL
     */
    suspend fun saveOllamaBaseUrl(url: String)

    /**
     * Get Ollama model
     */
    suspend fun getOllamaModel(): String

    /**
     * Save Ollama model
     */
    suspend fun saveOllamaModel(model: String)

    /**
     * List available Ollama models
     */
    suspend fun listOllamaModels(): Result<List<String>>

    /**
     * Check if Ollama is available
     */
    suspend fun checkOllamaHealth(): Boolean

    // ============================================================================
    // Ollama Configuration Methods
    // ============================================================================

    /**
     * Get Ollama temperature setting
     */
    suspend fun getOllamaTemperature(): Double?

    /**
     * Save Ollama temperature setting
     */
    suspend fun saveOllamaTemperature(temperature: Double)

    /**
     * Get Ollama Top P setting
     */
    suspend fun getOllamaTopP(): Double?

    /**
     * Save Ollama Top P setting
     */
    suspend fun saveOllamaTopP(topP: Double)

    /**
     * Get Ollama Top K setting
     */
    suspend fun getOllamaTopK(): Int?

    /**
     * Save Ollama Top K setting
     */
    suspend fun saveOllamaTopK(topK: Int)

    /**
     * Get Ollama context window size
     */
    suspend fun getOllamaNumCtx(): Int?

    /**
     * Save Ollama context window size
     */
    suspend fun saveOllamaNumCtx(numCtx: Int)

    /**
     * Get Ollama max tokens to predict
     */
    suspend fun getOllamaNumPredict(): Int?

    /**
     * Save Ollama max tokens to predict
     */
    suspend fun saveOllamaNumPredict(numPredict: Int)

    /**
     * Get Ollama repeat penalty
     */
    suspend fun getOllamaRepeatPenalty(): Double?

    /**
     * Save Ollama repeat penalty
     */
    suspend fun saveOllamaRepeatPenalty(repeatPenalty: Double)

    /**
     * Get Ollama repeat last N
     */
    suspend fun getOllamaRepeatLastN(): Int?

    /**
     * Save Ollama repeat last N
     */
    suspend fun saveOllamaRepeatLastN(repeatLastN: Int)

    /**
     * Get Ollama seed
     */
    suspend fun getOllamaSeed(): Int?

    /**
     * Save Ollama seed
     */
    suspend fun saveOllamaSeed(seed: Int?)

    /**
     * Get Ollama stop sequences
     */
    suspend fun getOllamaStopSequences(): List<String>?

    /**
     * Save Ollama stop sequences
     */
    suspend fun saveOllamaStopSequences(sequences: List<String>?)

    /**
     * Get Ollama number of threads
     */
    suspend fun getOllamaNumThread(): Int?

    /**
     * Save Ollama number of threads
     */
    suspend fun saveOllamaNumThread(numThread: Int?)

    /**
     * Get Ollama system prompt template
     */
    suspend fun getOllamaSystemPrompt(): String?

    /**
     * Save Ollama system prompt template
     */
    suspend fun saveOllamaSystemPrompt(prompt: String)
}
