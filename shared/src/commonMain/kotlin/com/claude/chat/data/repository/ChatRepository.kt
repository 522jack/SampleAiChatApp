package com.claude.chat.data.repository

import com.claude.chat.domain.model.Message
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
     * Check if API key is configured
     */
    suspend fun isApiKeyConfigured(): Boolean
}
