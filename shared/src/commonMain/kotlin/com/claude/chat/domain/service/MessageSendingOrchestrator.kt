package com.claude.chat.domain.service

import com.claude.chat.data.model.StreamChunk
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.domain.manager.TechSpecManager
import com.claude.chat.domain.model.Message
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Orchestrator for message sending operations
 * Handles the complex logic of sending messages including:
 * - RAG context enrichment
 * - Tech Spec mode prompts
 * - Tool loop execution
 * - Model comparison mode
 */
class MessageSendingOrchestrator(
    private val repository: ChatRepository,
    private val techSpecManager: TechSpecManager
) {
    companion object {
        // RAG citation instructions
        private const val RAG_CITATION_INSTRUCTIONS = """

CRITICAL INSTRUCTIONS FOR USING THE KNOWLEDGE BASE:

1. **Clickable Citations** - You MUST include clickable markdown links:
   - Format: [text](URL)
   - URLs for each source are provided above
   - Example: "According to [the documentation](https://example.com/docs), ..."

2. **Quote Formatting** - When quoting directly from sources:
   - Use block quotes for longer citations: > quoted text
   - Use inline quotes for short phrases: "quoted text"
   - ALWAYS add a source link after the quote
   - Example:
     > "Authentication is performed using OAuth 2.0 tokens with a 1-hour expiration."

     — [Source 1](https://example.com/docs)

3. **Text Formatting** - Use markdown for emphasis:
   - **Bold** for important concepts: **OAuth 2.0**
   - *Italic* for emphasis: *highly recommended*
   - `Code` for technical terms: `access_token`

4. **Citation Examples**:
   ✅ CORRECT:
   - "According to [the API documentation](https://example.com/api), the endpoint requires authentication."
   - > "All requests must include an Authorization header."

     — [Source 1](https://example.com/api)
   - "The **authentication flow** uses `JWT` tokens, as described in [Source 2](https://example.com/auth)."

   ❌ WRONG:
   - "According to Source 1, ..." (missing clickable link)
   - "The documentation says..." (missing link and quote formatting)

5. **Best Practices**:
   - Prefer information from sources with higher relevance scores
   - Use block quotes (>) for direct citations from sources
   - Add source links immediately after quotes
   - If supplementing with general knowledge, clearly indicate this

ALWAYS make citations clickable and properly formatted!
"""
    }

    /**
     * Configuration for sending a message
     */
    data class SendConfig(
        val userText: String,
        val messages: List<Message>,
        val isRagMode: Boolean,
        val isTechSpecMode: Boolean,
        val techSpecState: TechSpecManager.TechSpecState,
        val onToolExecution: (toolName: String, result: String) -> Unit = { _, _ -> },
        val onRagUsageDetected: (Boolean) -> Unit = { _ -> }  // Callback when RAG is used
    )

    /**
     * Send a message with all necessary preprocessing
     *
     * @param config Send configuration
     * @return Flow of StreamChunks for UI updates
     */
    suspend fun sendMessage(config: SendConfig): Flow<StreamChunk> = flow {
        // Step 1: Resolve system prompt and detect RAG usage
        val (systemPrompt, wasRagUsed) = resolveSystemPrompt(config)

        // Notify caller about RAG usage
        config.onRagUsageDetected(wasRagUsed)

        // Step 2: Send via repository with tool loop support
        repository.sendMessageWithToolLoop(
            messages = config.messages,
            systemPrompt = systemPrompt,
            onToolCall = { toolName, arguments ->
                // Execute tool and notify caller
                Napier.d("Tool execution requested: $toolName")
                val result = repository.callMcpTool(toolName, arguments)

                if (result.isSuccess) {
                    val resultText = result.getOrNull() ?: ""
                    config.onToolExecution(toolName, "✅ $toolName:\n$resultText")
                } else {
                    val errorText = result.exceptionOrNull()?.message ?: "Unknown error"
                    config.onToolExecution(toolName, "❌ $toolName: $errorText")
                }

                result
            }
        ).collect { chunk ->
            emit(chunk)
        }
    }

    /**
     * Resolve system prompt with all necessary context
     * Returns Pair<systemPrompt, wasRagUsed>
     */
    private suspend fun resolveSystemPrompt(config: SendConfig): Pair<String, Boolean> {
        // Start with Tech Spec prompt if enabled, otherwise base prompt
        var systemPrompt = if (config.isTechSpecMode) {
            techSpecManager.buildSystemPrompt(config.userText, config.techSpecState)
        } else {
            repository.getSystemPrompt() ?: ""
        }

        var wasRagUsed = false

        // Add RAG context if enabled
        if (config.isRagMode) {
            val (enrichedPrompt, ragUsed) = enrichWithRagContext(systemPrompt, config.userText)
            systemPrompt = enrichedPrompt
            wasRagUsed = ragUsed
        }

        return Pair(systemPrompt, wasRagUsed)
    }

    /**
     * Enrich system prompt with RAG context
     * Returns Pair<enrichedPrompt, wasRagUsed>
     * If no relevant documents are found (score below threshold), returns base prompt
     * allowing the model to answer from general knowledge
     */
    private suspend fun enrichWithRagContext(basePrompt: String, userText: String): Pair<String, Boolean> {
        val ragContextResult = repository.searchRagIndex(userText, topK = 5)

        if (ragContextResult.isSuccess) {
            val ragContext = ragContextResult.getOrNull()
            if (!ragContext.isNullOrBlank()) {
                Napier.d("RAG mode: Using knowledge base context for this query")
                return Pair("$basePrompt\n\n$ragContext$RAG_CITATION_INSTRUCTIONS", true)
            } else {
                Napier.d("RAG mode: No relevant documents found, answering from general knowledge")
            }
        } else {
            Napier.w("RAG mode: Search failed, falling back to general knowledge: ${ragContextResult.exceptionOrNull()?.message}")
        }

        return Pair(basePrompt, false)
    }

    /**
     * Send message in model comparison mode
     *
     * @param messages Conversation messages
     * @return Result containing comparison response
     */
    suspend fun sendMessageComparison(messages: List<Message>) = flow<ComparisonResult> {
        try {
            val systemPrompt = repository.getSystemPrompt()
            val result = repository.sendMessageComparison(messages, systemPrompt)

            if (result.isSuccess) {
                val comparisonResponse = result.getOrThrow()
                emit(ComparisonResult.Success(comparisonResponse))
            } else {
                val error = result.exceptionOrNull()
                Napier.e("Model comparison failed", error)
                emit(ComparisonResult.Error(error?.message ?: "Comparison failed"))
            }
        } catch (e: Exception) {
            Napier.e("Error in model comparison", e)
            emit(ComparisonResult.Error(e.message ?: "Unknown error"))
        }
    }

    /**
     * Result of model comparison
     */
    sealed class ComparisonResult {
        data class Success(val response: com.claude.chat.domain.model.ModelComparisonResponse) : ComparisonResult()
        data class Error(val message: String) : ComparisonResult()
    }
}