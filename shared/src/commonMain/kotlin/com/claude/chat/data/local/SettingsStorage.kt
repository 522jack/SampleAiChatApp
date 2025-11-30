package com.claude.chat.data.local

import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import com.claude.chat.platform.createFileStorage
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
    private val fileStorage = createFileStorage()

    companion object {
        private const val KEY_API_KEY = "claude_api_key"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_MESSAGES = "messages_history"  // Deprecated - use file storage
        private const val MESSAGES_FILE = "messages_history.json"
        private const val KEY_JSON_MODE = "json_mode"
        private const val KEY_TECH_SPEC_MODE = "tech_spec_mode"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MODEL_COMPARISON_MODE = "model_comparison_mode"
        private const val KEY_MCP_ENABLED = "mcp_enabled"
        private const val KEY_MCP_SERVERS = "mcp_servers"
        private const val KEY_RAG_MODE = "rag_mode"
        private const val KEY_RAG_INDEX = "rag_index"
        private const val KEY_RAG_RERANKING_ENABLED = "rag_reranking_enabled"
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

    fun getMcpEnabled(): Boolean {
        return settings.getBoolean(KEY_MCP_ENABLED, false)
    }

    fun saveMcpEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_MCP_ENABLED, enabled)
        Napier.d("MCP ${if (enabled) "enabled" else "disabled"}")
    }

    suspend fun getMessages(): List<Message> {
        // Try to load from file storage first
        val messagesJson = fileStorage.readTextFile(MESSAGES_FILE)

        if (messagesJson != null) {
            return try {
                val storedMessages = json.decodeFromString<List<StoredMessage>>(messagesJson)
                Napier.d("Loaded ${storedMessages.size} messages from file storage")
                storedMessages.map { it.toDomainModel() }
            } catch (e: Exception) {
                Napier.e("Error loading messages from file", e)
                emptyList()
            }
        }

        // Fallback: try to migrate from old Settings storage
        val oldMessagesJson = settings.getStringOrNull(KEY_MESSAGES)
        if (oldMessagesJson != null) {
            Napier.d("Found messages in old Settings storage, migrating to file storage...")
            return try {
                val storedMessages = json.decodeFromString<List<StoredMessage>>(oldMessagesJson)
                val messages = storedMessages.map { it.toDomainModel() }

                // Save to new file storage
                saveMessages(messages)

                // Clear old storage
                settings.remove(KEY_MESSAGES)
                Napier.d("Successfully migrated ${messages.size} messages to file storage")

                messages
            } catch (e: Exception) {
                Napier.e("Error migrating messages from Settings", e)
                emptyList()
            }
        }

        return emptyList()
    }

    suspend fun saveMessages(messages: List<Message>) {
        try {
            val storedMessages = messages.map { StoredMessage.fromDomainModel(it) }
            val messagesJson = json.encodeToString(storedMessages)

            val success = fileStorage.writeTextFile(MESSAGES_FILE, messagesJson)
            if (success) {
                Napier.d("Saved ${messages.size} messages to file (${messagesJson.length} chars)")
            } else {
                Napier.e("Failed to save messages to file")
            }
        } catch (e: Exception) {
            Napier.e("Error saving messages", e)
            throw e  // Re-throw to notify caller
        }
    }

    suspend fun clearMessages() {
        // Clear both old and new storage
        settings.remove(KEY_MESSAGES)
        fileStorage.deleteFile(MESSAGES_FILE)
        Napier.d("Messages cleared")
    }

    suspend fun clearAll() {
        settings.clear()
        fileStorage.deleteFile(MESSAGES_FILE)
        Napier.d("All settings cleared")
    }

    fun getMcpServers(): List<com.claude.chat.data.model.McpServerConfig> {
        val serversJson = settings.getStringOrNull(KEY_MCP_SERVERS) ?: return emptyList()

        return try {
            json.decodeFromString<List<com.claude.chat.data.model.McpServerConfig>>(serversJson)
        } catch (e: Exception) {
            Napier.e("Error loading MCP servers", e)
            emptyList()
        }
    }

    fun saveMcpServers(servers: List<com.claude.chat.data.model.McpServerConfig>) {
        try {
            val serversJson = json.encodeToString(servers)
            settings.putString(KEY_MCP_SERVERS, serversJson)
            Napier.d("Saved ${servers.size} MCP servers")
        } catch (e: Exception) {
            Napier.e("Error saving MCP servers", e)
        }
    }

    fun getRagMode(): Boolean {
        return settings.getBoolean(KEY_RAG_MODE, false)
    }

    fun saveRagMode(enabled: Boolean) {
        settings.putBoolean(KEY_RAG_MODE, enabled)
        Napier.d("RAG mode ${if (enabled) "enabled" else "disabled"}")
    }

    fun getRagIndex(): String? {
        return settings.getStringOrNull(KEY_RAG_INDEX)
    }

    fun saveRagIndex(indexJson: String) {
        settings.putString(KEY_RAG_INDEX, indexJson)
        Napier.d("RAG index saved")
    }

    fun clearRagIndex() {
        settings.remove(KEY_RAG_INDEX)
        Napier.d("RAG index cleared")
    }

    fun getRagRerankingEnabled(): Boolean {
        return settings.getBoolean(KEY_RAG_RERANKING_ENABLED, false)
    }

    fun saveRagRerankingEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_RAG_RERANKING_ENABLED, enabled)
        Napier.d("RAG reranking ${if (enabled) "enabled" else "disabled"}")
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
    val tokensSaved: Int? = null,
    val isFromRag: Boolean = false
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
            tokensSaved = tokensSaved,
            isFromRag = isFromRag
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
                tokensSaved = message.tokensSaved,
                isFromRag = message.isFromRag
            )
        }
    }
}
