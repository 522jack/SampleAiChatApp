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
}
