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
}
