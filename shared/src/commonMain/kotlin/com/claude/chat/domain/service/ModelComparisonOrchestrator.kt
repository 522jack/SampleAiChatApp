package com.claude.chat.domain.service

import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.model.ClaudeModel
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.domain.model.ClaudePricing
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import com.claude.chat.domain.model.ModelComparisonResponse
import com.claude.chat.domain.model.ModelResponse
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Orchestrator for comparing responses from multiple Claude models
 * Sends the same prompt to multiple models in parallel and aggregates results
 */
@OptIn(ExperimentalUuidApi::class)
class ModelComparisonOrchestrator(
    private val apiClient: ClaudeApiClient
) {
    companion object {
        private const val HAIKU_3_MAX_TOKENS = 4096
        private const val DEFAULT_MAX_TOKENS = 8192

        // Models for comparison: Haiku 3 (fast), Sonnet 3.7 (balanced), Sonnet 4.5 (most capable)
        private val COMPARISON_MODELS = listOf(
            ClaudeModel.HAIKU_3,        // claude-3-haiku-20240307
            ClaudeModel.SONNET_3_7,     // claude-3-7-sonnet-20250219
            ClaudeModel.SONNET_4_5      // claude-sonnet-4-5-20250929
        )

        /**
         * Get maximum tokens for a specific model
         */
        private fun getMaxTokensForModel(modelId: String): Int {
            return if (modelId == ClaudeModel.HAIKU_3.modelId) {
                HAIKU_3_MAX_TOKENS
            } else {
                DEFAULT_MAX_TOKENS
            }
        }
    }

    /**
     * Send a message to multiple models and compare their responses
     *
     * @param messages Conversation messages
     * @param systemPrompt System prompt to use
     * @param temperature Temperature setting
     * @param apiKey API key for authentication
     * @param modelsToCompare Optional list of models to compare (defaults to standard comparison set)
     * @return Result containing ModelComparisonResponse or error
     */
    suspend fun compareModels(
        messages: List<Message>,
        systemPrompt: String,
        temperature: Double,
        apiKey: String,
        modelsToCompare: List<ClaudeModel> = COMPARISON_MODELS
    ): Result<ModelComparisonResponse> = coroutineScope {
        try {
            // Prepare messages for API (exclude comparison responses, but include summaries)
            val claudeMessages = messages
                .filter { it.comparisonResponse == null }
                .let { convertToClaudeMessages(it) }

            // Get user question (last user message)
            val userQuestion = messages.lastOrNull { it.role == MessageRole.USER }?.content
                ?: "Question"

            Napier.d("Sending comparison requests to ${modelsToCompare.size} models")

            // Send requests to all models in parallel
            val modelResponses = modelsToCompare.map { model ->
                async {
                    sendSingleModelRequest(model, claudeMessages, systemPrompt, temperature, apiKey)
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

    /**
     * Send a request to a single model and return its response with metrics
     */
    private suspend fun sendSingleModelRequest(
        model: ClaudeModel,
        claudeMessages: List<ClaudeMessage>,
        systemPrompt: String,
        temperature: Double,
        apiKey: String
    ): ModelResponse {
        val maxTokens = getMaxTokensForModel(model.modelId)

        val request = ClaudeMessageRequest(
            model = model.modelId,
            messages = claudeMessages,
            maxTokens = maxTokens,
            stream = false,
            system = systemPrompt,
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

        return ModelResponse(
            modelId = model.modelId,
            modelName = model.displayName,
            content = content,
            responseTimeMs = responseTimeMs,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalCost = cost
        )
    }

    /**
     * Convert domain Message objects to API ClaudeMessage format
     */
    private fun convertToClaudeMessages(messages: List<Message>): List<ClaudeMessage> {
        return messages.map { message ->
            ClaudeMessage(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "user" // Summary messages sent as user messages for context
                },
                content = com.claude.chat.data.model.ClaudeMessageContent.Text(message.content)
            )
        }
    }
}