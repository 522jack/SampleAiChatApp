package com.claude.chat.data.local

import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Storage for app settings and message history using Multiplatform Settings
 */
class SettingsStorage(
    private val settings: Settings = Settings()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        private const val KEY_API_KEY = "claude_api_key"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_MESSAGES = "messages_history"
    }

    fun getApiKey(): String? {
        return settings.getStringOrNull(KEY_API_KEY)
    }

    fun saveApiKey(apiKey: String) {
        settings.putString(KEY_API_KEY, apiKey)
        Napier.d("API key saved")
    }

    fun getSystemPrompt(): String? {
        return settings.getStringOrNull(KEY_SYSTEM_PROMPT)
    }

    fun saveSystemPrompt(prompt: String) {
        settings.putString(KEY_SYSTEM_PROMPT, prompt)
    }

    fun getMessages(): List<Message> {
        val messagesJson = settings.getStringOrNull(KEY_MESSAGES) ?: return emptyList()

        return try {
            val storedMessages = json.decodeFromString<List<StoredMessage>>(messagesJson)
            storedMessages.map { it.toDomainModel() }
        } catch (e: Exception) {
            Napier.e("Error loading messages", e)
            emptyList()
        }
    }

    fun saveMessages(messages: List<Message>) {
        try {
            val storedMessages = messages.map { StoredMessage.fromDomainModel(it) }
            val messagesJson = json.encodeToString(storedMessages)
            settings.putString(KEY_MESSAGES, messagesJson)
            Napier.d("Saved ${messages.size} messages")
        } catch (e: Exception) {
            Napier.e("Error saving messages", e)
        }
    }

    fun clearMessages() {
        settings.remove(KEY_MESSAGES)
        Napier.d("Messages cleared")
    }

    fun clearAll() {
        settings.clear()
        Napier.d("All settings cleared")
    }
}

/**
 * Serializable version of Message for storage
 */
@Serializable
private data class StoredMessage(
    val id: String,
    val content: String,
    val role: String,
    val timestamp: Long,
    val isError: Boolean = false
) {
    fun toDomainModel(): Message {
        return Message(
            id = id,
            content = content,
            role = when (role) {
                "user" -> MessageRole.USER
                "assistant" -> MessageRole.ASSISTANT
                else -> MessageRole.USER
            },
            timestamp = Instant.fromEpochMilliseconds(timestamp),
            isError = isError
        )
    }

    companion object {
        fun fromDomainModel(message: Message): StoredMessage {
            return StoredMessage(
                id = message.id,
                content = message.content,
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                },
                timestamp = message.timestamp.toEpochMilliseconds(),
                isError = message.isError
            )
        }
    }
}
