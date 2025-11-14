package com.claude.chat.data.repository

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.model.ClaudeModel
import com.claude.chat.data.model.StreamChunk
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.domain.model.ClaudePricing
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import com.claude.chat.domain.model.ModelComparisonResponse
import com.claude.chat.domain.model.ModelResponse
import com.claude.chat.domain.service.MessageCompressionService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Implementation of ChatRepository
 */
@OptIn(ExperimentalUuidApi::class)
class ChatRepositoryImpl(
    private val apiClient: ClaudeApiClient,
    private val settingsStorage: SettingsStorage
) : ChatRepository {

    private val compressionService = MessageCompressionService(apiClient)

    companion object {
        private const val MAX_TOKENS = 8192
        private const val DEFAULT_SYSTEM_PROMPT = "You are Claude, a helpful AI assistant."

        // Models for comparison: Haiku 3 (fast), Sonnet 3.7 (balanced), Sonnet 4.5 (most capable)
        private val COMPARISON_MODELS = listOf(
            ClaudeModel.HAIKU_3,        // claude-3-haiku-20240307
            ClaudeModel.SONNET_3_7,     // claude-3-7-sonnet-20250219
            ClaudeModel.SONNET_4_5      // claude-sonnet-4-5-20250929
        )
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
                    MessageRole.SYSTEM -> "user" // Summary messages sent as user messages for context
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

        val selectedModel = getSelectedModel()
        val temperature = getTemperature()

        val request = ClaudeMessageRequest(
            model = selectedModel,
            messages = claudeMessages,
            maxTokens = MAX_TOKENS,
            stream = true,
            system = finalSystemPrompt,
            temperature = temperature
        )

        Napier.d("Sending message to Claude API with ${messages.size} messages, JSON mode: $jsonModeEnabled")

        // Map StreamChunk to String for backward compatibility
        return apiClient.sendMessage(request, apiKey).map { chunk ->
            chunk.text ?: ""
        }
    }

    override suspend fun sendMessageWithUsage(
        messages: List<Message>,
        systemPrompt: String?
    ): Flow<StreamChunk> {
        val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")
        val jsonModeEnabled = getJsonMode()

        val claudeMessages = messages.map { message ->
            ClaudeMessage(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "user" // Summary messages sent as user messages for context
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

        val selectedModel = getSelectedModel()
        val temperature = getTemperature()

        val request = ClaudeMessageRequest(
            model = selectedModel,
            messages = claudeMessages,
            maxTokens = MAX_TOKENS,
            stream = true,
            system = finalSystemPrompt,
            temperature = temperature
        )

        Napier.d("Sending message to Claude API with token usage tracking")

        return apiClient.sendMessage(request, apiKey)
    }

    override suspend fun sendMessageComparison(
        messages: List<Message>,
        systemPrompt: String?
    ): Result<ModelComparisonResponse> = coroutineScope {
        try {
            val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")
            val finalSystemPrompt = systemPrompt ?: DEFAULT_SYSTEM_PROMPT
            val temperature = getTemperature()

            // Prepare messages for API
            val claudeMessages = messages
                .filter { it.comparisonResponse == null } // Exclude comparison responses, but include summaries
                .map { message ->
                    ClaudeMessage(
                        role = when (message.role) {
                            MessageRole.USER -> "user"
                            MessageRole.ASSISTANT -> "assistant"
                            MessageRole.SYSTEM -> "user" // Summary messages sent as user messages for context
                        },
                        content = message.content
                    )
                }

            // Get user question (last user message)
            val userQuestion = messages.lastOrNull { it.role == MessageRole.USER }?.content
                ?: "Question"

            Napier.d("Sending comparison requests to ${COMPARISON_MODELS.size} models")

            // Send requests to all models in parallel
            val modelResponses = COMPARISON_MODELS.map { model ->
                async {
                    // Haiku 3 has a lower max token limit (4096)
                    val maxTokens = if (model.modelId == "claude-3-haiku-20240307") {
                        4096
                    } else {
                        MAX_TOKENS
                    }

                    val request = ClaudeMessageRequest(
                        model = model.modelId,
                        messages = claudeMessages,
                        maxTokens = maxTokens,
                        stream = false,
                        system = finalSystemPrompt,
                        temperature = temperature
                    )

                    val startTime = Clock.System.now()
                    val apiResult = apiClient.sendMessageNonStreaming(request, apiKey)
                    val responseResult = apiResult.getOrThrow()
                    val endTime = Clock.System.now()
                    val responseTimeMs = (endTime - startTime).inWholeMilliseconds

                    val content = responseResult.content.firstOrNull()?.text ?: ""
                    val inputTokens = responseResult.usage.inputTokens ?: 0
                    val outputTokens = responseResult.usage.outputTokens ?: 0
                    val cost = ClaudePricing.calculateCost(model.modelId, inputTokens, outputTokens)

                    Napier.d("${model.displayName} response: ${responseTimeMs}ms, tokens: $inputTokens/$outputTokens, cost: \$${cost}")

                    ModelResponse(
                        modelId = model.modelId,
                        modelName = model.displayName,
                        content = content,
                        responseTimeMs = responseTimeMs,
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        totalCost = cost
                    )
                }
            }.awaitAll()

            val comparisonResponse = ModelComparisonResponse(
                id = Uuid.random().toString(),
                userQuestion = userQuestion,
                responses = modelResponses,
                timestamp = Clock.System.now()
            )

            Napier.d("Model comparison completed successfully")
            Result.success(comparisonResponse)

        } catch (e: Exception) {
            Napier.e("Error in model comparison", e)
            Result.failure(e)
        }
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

    override suspend fun getTechSpecMode(): Boolean {
        return settingsStorage.getTechSpecMode()
    }

    override suspend fun saveTechSpecMode(enabled: Boolean) {
        settingsStorage.saveTechSpecMode(enabled)
    }

    override suspend fun getSelectedModel(): String {
        return settingsStorage.getSelectedModel()
    }

    override suspend fun saveSelectedModel(modelId: String) {
        settingsStorage.saveSelectedModel(modelId)
    }

    override suspend fun getTemperature(): Double {
        return settingsStorage.getTemperature()
    }

    override suspend fun saveTemperature(temperature: Double) {
        settingsStorage.saveTemperature(temperature)
    }

    override suspend fun getModelComparisonMode(): Boolean {
        return settingsStorage.getModelComparisonMode()
    }

    override suspend fun saveModelComparisonMode(enabled: Boolean) {
        settingsStorage.saveModelComparisonMode(enabled)
    }

    override suspend fun isApiKeyConfigured(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    override suspend fun compressMessages(): Result<Boolean> {
        try {
            val messages = getMessages()

            if (!compressionService.shouldCompress(messages)) {
                return Result.success(false)
            }

            val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")

            val compressionResult = compressionService.compressMessages(messages, apiKey)

            if (compressionResult.isFailure) {
                return Result.failure(compressionResult.exceptionOrNull()!!)
            }

            val compressedMessages = compressionResult.getOrThrow()
            saveMessages(compressedMessages)

            Napier.d("Messages compressed successfully")
            return Result.success(true)

        } catch (e: Exception) {
            Napier.e("Error compressing messages", e)
            return Result.failure(e)
        }
    }
}
