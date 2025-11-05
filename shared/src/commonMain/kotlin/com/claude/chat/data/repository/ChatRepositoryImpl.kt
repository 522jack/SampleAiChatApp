package com.claude.chat.data.repository

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
        val jsonModeEnabled = getJsonMode()

        val claudeMessages = messages.map { message ->
            ClaudeMessage(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                },
                content = message.content
            )
        }

        // Prepare system prompt with JSON instructions if JSON mode is enabled
        val finalSystemPrompt = if (jsonModeEnabled) {
            val basePrompt = systemPrompt ?: DEFAULT_SYSTEM_PROMPT
            // Get current date
            val currentDate = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()

            """$basePrompt

Today's date is: $currentDate

IMPORTANT: You must respond ONLY with valid JSON in the following format:
{
  "question": "the user's question or request",
  "answer": "your detailed answer",
  "date": "$currentDate"
}

Always use exactly "$currentDate" as the date value. Do not include any text outside of this JSON structure. The entire response must be valid JSON."""
        } else {
            systemPrompt ?: DEFAULT_SYSTEM_PROMPT
        }

        val request = ClaudeMessageRequest(
            model = MODEL,
            messages = claudeMessages,
            maxTokens = MAX_TOKENS,
            stream = true,
            system = finalSystemPrompt
        )

        Napier.d("Sending message to Claude API with ${messages.size} messages, JSON mode: $jsonModeEnabled")

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

    override suspend fun getJsonMode(): Boolean {
        return settingsStorage.getJsonMode()
    }

    override suspend fun saveJsonMode(enabled: Boolean) {
        settingsStorage.saveJsonMode(enabled)
    }

    override suspend fun isApiKeyConfigured(): Boolean {
        return !getApiKey().isNullOrBlank()
    }
}
