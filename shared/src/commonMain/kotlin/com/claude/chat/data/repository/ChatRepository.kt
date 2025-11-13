package com.claude.chat.data.repository

import com.claude.chat.data.model.StreamChunk
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.ModelComparisonResponse
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for chat operations
 */
interface ChatRepository {
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
}
