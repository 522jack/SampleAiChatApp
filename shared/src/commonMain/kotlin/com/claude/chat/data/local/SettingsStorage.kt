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
        private const val KEY_JSON_MODE = "json_mode"
        private const val KEY_TECH_SPEC_MODE = "tech_spec_mode"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MODEL_COMPARISON_MODE = "model_comparison_mode"
        private const val DEFAULT_MODEL = "claude-3-5-haiku-20241022"
        private const val DEFAULT_TEMPERATURE = 1.0
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

    fun getJsonMode(): Boolean {
        return settings.getBoolean(KEY_JSON_MODE, false)
    }

    fun saveJsonMode(enabled: Boolean) {
        settings.putBoolean(KEY_JSON_MODE, enabled)
        Napier.d("JSON mode ${if (enabled) "enabled" else "disabled"}")
    }

    fun getTechSpecMode(): Boolean {
        return settings.getBoolean(KEY_TECH_SPEC_MODE, false)
    }

    fun saveTechSpecMode(enabled: Boolean) {
        settings.putBoolean(KEY_TECH_SPEC_MODE, enabled)
        Napier.d("Tech Spec mode ${if (enabled) "enabled" else "disabled"}")
    }

    fun getSelectedModel(): String {
        return settings.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL)
    }

    fun saveSelectedModel(modelId: String) {
        settings.putString(KEY_SELECTED_MODEL, modelId)
        Napier.d("Selected model: $modelId")
    }

    fun getTemperature(): Double {
        return settings.getDouble(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
    }

    fun saveTemperature(temperature: Double) {
        settings.putDouble(KEY_TEMPERATURE, temperature)
        Napier.d("Temperature set to: $temperature")
    }

    fun getModelComparisonMode(): Boolean {
        return settings.getBoolean(KEY_MODEL_COMPARISON_MODE, false)
    }

    fun saveModelComparisonMode(enabled: Boolean) {
        settings.putBoolean(KEY_MODEL_COMPARISON_MODE, enabled)
        Napier.d("Model comparison mode ${if (enabled) "enabled" else "disabled"}")
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
    val isError: Boolean = false,
    val isSummary: Boolean = false,
    val summarizedMessageCount: Int? = null,
    val summarizedTokens: Int? = null,
    val tokensSaved: Int? = null
) {
    fun toDomainModel(): Message {
        return Message(
            id = id,
            content = content,
            role = when (role) {
                "user" -> MessageRole.USER
                "assistant" -> MessageRole.ASSISTANT
                "system" -> MessageRole.SYSTEM
                else -> MessageRole.USER
            },
            timestamp = Instant.fromEpochMilliseconds(timestamp),
            isError = isError,
            isSummary = isSummary,
            summarizedMessageCount = summarizedMessageCount,
            summarizedTokens = summarizedTokens,
            tokensSaved = tokensSaved
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
                    MessageRole.SYSTEM -> "system"
                },
                timestamp = message.timestamp.toEpochMilliseconds(),
                isError = message.isError,
                isSummary = message.isSummary,
                summarizedMessageCount = message.summarizedMessageCount,
                summarizedTokens = message.summarizedTokens,
                tokensSaved = message.tokensSaved
            )
        }
    }
}
