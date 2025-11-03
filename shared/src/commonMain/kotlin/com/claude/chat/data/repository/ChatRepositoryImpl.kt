package com.claude.chat.data.repository

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Implementation of ChatRepository
 */
class ChatRepositoryImpl(
    private val apiClient: ClaudeApiClient,
    private val settingsStorage: SettingsStorage
) : ChatRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    companion object {
        private const val MODEL = "claude-3-5-haiku-20241022"
        private const val MAX_TOKENS = 8192
        private const val DEFAULT_SYSTEM_PROMPT = "You are Claude, a helpful AI assistant."
    }

    override suspend fun sendMessage(
        messages: List<Message>,
        systemPrompt: String?
    ): Flow<String> {
        val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")

        val claudeMessages = messages.map { message ->
            ClaudeMessage(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                },
                content = message.content
            )
        }

        val request = ClaudeMessageRequest(
            model = MODEL,
            messages = claudeMessages,
            maxTokens = MAX_TOKENS,
            stream = true,
            system = systemPrompt ?: DEFAULT_SYSTEM_PROMPT
        )

        Napier.d("Sending message to Claude API with ${messages.size} messages")

        return apiClient.sendMessage(request, apiKey)
    }

    override suspend fun getMessages(): List<Message> {
        return settingsStorage.getMessages()
    }

    override suspend fun saveMessages(messages: List<Message>) {
        settingsStorage.saveMessages(messages)
    }

    override suspend fun clearMessages() {
        settingsStorage.clearMessages()
    }

    override suspend fun getApiKey(): String? {
        return settingsStorage.getApiKey()
    }

    override suspend fun saveApiKey(apiKey: String) {
        settingsStorage.saveApiKey(apiKey)
    }

    override suspend fun getSystemPrompt(): String? {
        return settingsStorage.getSystemPrompt()
    }

    override suspend fun saveSystemPrompt(prompt: String) {
        settingsStorage.saveSystemPrompt(prompt)
    }

    override suspend fun isApiKeyConfigured(): Boolean {
        return !getApiKey().isNullOrBlank()
    }
}
