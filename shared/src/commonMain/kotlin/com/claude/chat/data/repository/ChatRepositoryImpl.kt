package com.claude.chat.data.repository

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.mcp.McpManager
import com.claude.chat.data.model.ClaudeContentBlock
import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageContent
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.model.ClaudeModel
import com.claude.chat.data.model.McpTool
import com.claude.chat.data.model.RagSearchConfig
import com.claude.chat.data.model.StreamChunk
import com.claude.chat.data.model.ToolUseInfo
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.data.remote.OllamaClient
import com.claude.chat.data.model.OllamaChatMessage
import com.claude.chat.data.model.OllamaOptions
import com.claude.chat.domain.model.ModelProvider
import com.claude.chat.domain.model.ClaudePricing
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import com.claude.chat.domain.model.ModelComparisonResponse
import com.claude.chat.domain.model.ModelResponse
import com.claude.chat.domain.service.MessageCompressionService
import com.claude.chat.domain.service.ModelComparisonOrchestrator
import com.claude.chat.domain.service.RagService
import com.claude.chat.domain.service.ToolExecutionService
import com.claude.chat.platform.createFileStorage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private val ollamaClient: OllamaClient,
    private val settingsStorage: SettingsStorage,
    private val mcpManager: McpManager,
    private val ragService: RagService,
    private val toolExecutionService: ToolExecutionService,
    private val modelComparisonOrchestrator: ModelComparisonOrchestrator
) : ChatRepository {

    private val compressionService = MessageCompressionService(apiClient)
    private val fileStorage = createFileStorage()

    override suspend fun initializeMcpTools() {
        mcpManager.initialize()
    }

    override fun getAvailableMcpTools(): List<McpTool> {
        return mcpManager.availableTools
    }

    override suspend fun getMcpEnabled(): Boolean {
        return settingsStorage.getMcpEnabled()
    }

    override suspend fun saveMcpEnabled(enabled: Boolean) {
        settingsStorage.saveMcpEnabled(enabled)
        if (enabled && !mcpManager.isInitialized()) {
            initializeMcpTools()
        }
    }

    override suspend fun callMcpTool(toolName: String, arguments: Map<String, String>): Result<String> {
        return mcpManager.callTool(toolName, arguments.mapValues { it.value as Any })
    }

    companion object {
        private const val MAX_TOKENS = 8192
        private const val DEFAULT_SYSTEM_PROMPT = "You are Claude, a helpful AI assistant."
        private const val RAG_INDEX_FILENAME = "rag_index.json"

        private fun getCurrentDate(): String {
            return Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()
        }
    }

    /**
     * Maps domain Message objects to API ClaudeMessage format
     */
    private fun mapToClaudeMessages(messages: List<Message>): List<ClaudeMessage> {
        return messages.map { message ->
            ClaudeMessage(
                role = when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "user" // Summary messages sent as user messages for context
                },
                content = ClaudeMessageContent.Text(message.content)
            )
        }
    }

    /**
     * Maps domain Message objects to Ollama chat message format
     */
    private fun mapToOllamaMessages(messages: List<Message>, systemPrompt: String?): List<OllamaChatMessage> {
        val ollamaMessages = mutableListOf<OllamaChatMessage>()

        // Add system prompt if provided
        if (systemPrompt != null) {
            ollamaMessages.add(OllamaChatMessage(role = "system", content = systemPrompt))
        }

        // Add conversation messages
        messages.forEach { message ->
            ollamaMessages.add(
                OllamaChatMessage(
                    role = when (message.role) {
                        MessageRole.USER -> "user"
                        MessageRole.ASSISTANT -> "assistant"
                        MessageRole.SYSTEM -> "system"
                    },
                    content = message.content
                )
            )
        }

        return ollamaMessages
    }

    /**
     * Creates a ClaudeMessage with content blocks for tool use conversations
     */
    private fun createMessageWithBlocks(
        role: String,
        blocks: List<ClaudeContentBlock>
    ): ClaudeMessage {
        return ClaudeMessage(
            role = role,
            content = ClaudeMessageContent.Blocks(blocks)
        )
    }

    /**
     * Prepares the final system prompt, adding JSON instructions if JSON mode is enabled
     */
    private fun prepareSystemPrompt(basePrompt: String?, jsonModeEnabled: Boolean): String {
        val prompt = basePrompt ?: DEFAULT_SYSTEM_PROMPT

        if (!jsonModeEnabled) {
            return prompt
        }

        val currentDate = getCurrentDate()
        return """$prompt

                Today's date is: $currentDate

                IMPORTANT: You must respond ONLY with valid JSON in the following format:
                {
                "question": "the user's question or request",
                "answer": "your detailed answer",
                "date": "$currentDate"
                }

                Always use exactly "$currentDate" as the date value. Do not include any text outside of this JSON structure. The entire response must be valid JSON."""
    }

    override suspend fun sendMessage(
        messages: List<Message>,
        systemPrompt: String?
    ): Flow<String> {
        val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")
        val jsonModeEnabled = getJsonMode()
        val mcpEnabled = getMcpEnabled()

        val claudeMessages = mapToClaudeMessages(messages)
        val finalSystemPrompt = prepareSystemPrompt(systemPrompt, jsonModeEnabled)
        val selectedModel = getSelectedModel()
        val temperature = getTemperature()

        // Add MCP tools if enabled
        val tools = if (mcpEnabled && mcpManager.isInitialized()) {
            mcpManager.getClaudeTools()
        } else {
            null
        }

        val request = ClaudeMessageRequest(
            model = selectedModel,
            messages = claudeMessages,
            maxTokens = MAX_TOKENS,
            stream = true,
            system = finalSystemPrompt,
            temperature = temperature,
            tools = tools
        )

        Napier.d("Sending message to Claude API with ${messages.size} messages, JSON mode: $jsonModeEnabled, MCP tools: ${tools?.size ?: 0}")

        // Map StreamChunk to String for backward compatibility
        return apiClient.sendMessage(request, apiKey).map { chunk ->
            chunk.text ?: ""
        }
    }

    override suspend fun sendMessageWithUsage(
        messages: List<Message>,
        systemPrompt: String?
    ): Flow<StreamChunk> = flow {
        // Check model provider
        val modelProvider = ModelProvider.fromString(getModelProvider())

        when (modelProvider) {
            ModelProvider.OLLAMA -> {
                // Use Ollama
                sendMessageToOllama(messages, systemPrompt).collect { emit(it) }
                return@flow
            }
            ModelProvider.CLAUDE -> {
                // Continue with Claude implementation below
            }
        }

        val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")
        val jsonModeEnabled = getJsonMode()
        val mcpEnabled = getMcpEnabled()

        val claudeMessages = mapToClaudeMessages(messages)
        val finalSystemPrompt = prepareSystemPrompt(systemPrompt, jsonModeEnabled)
        val selectedModel = getSelectedModel()
        val temperature = getTemperature()

        // Add MCP tools if enabled
        val tools = if (mcpEnabled && mcpManager.isInitialized()) {
            val claudeTools = mcpManager.getClaudeTools()
            Napier.d("MCP is enabled and initialized, got ${claudeTools.size} tools")
            claudeTools.forEach { tool ->
                Napier.d("  - Tool: ${tool.name}")
            }
            claudeTools
        } else {
            Napier.d("MCP tools NOT included: enabled=$mcpEnabled, initialized=${mcpManager.isInitialized()}")
            null
        }

        val request = ClaudeMessageRequest(
            model = selectedModel,
            messages = claudeMessages,
            maxTokens = MAX_TOKENS,
            stream = tools.isNullOrEmpty(), // Use non-streaming when tools are present
            system = finalSystemPrompt,
            temperature = temperature,
            tools = tools
        )

        Napier.d("Sending message to Claude API with token usage tracking, MCP tools: ${tools?.size ?: 0}, streaming: ${tools.isNullOrEmpty()}")

        // If tools are present, use non-streaming API to get complete tool parameters
        if (!tools.isNullOrEmpty()) {
            val result = apiClient.sendMessageNonStreaming(request, apiKey)
            if (result.isSuccess) {
                val response = result.getOrThrow()

                // Emit usage first
                emit(StreamChunk(usage = response.usage))

                // Process content blocks
                response.content.forEach { content ->
                    when (content.type) {
                        "text" -> {
                            content.text?.let { emit(StreamChunk(text = it)) }
                        }
                        "tool_use" -> {
                            if (content.id != null && content.name != null && content.input != null) {
                                Napier.d("Received tool_use from non-streaming API: ${content.name} with input: ${content.input}")
                                emit(StreamChunk(
                                    toolUse = ToolUseInfo(
                                        id = content.id,
                                        name = content.name,
                                        input = content.input
                                    )
                                ))
                            }
                        }
                    }
                }

                // Emit completion
                emit(StreamChunk(isComplete = true))
            } else {
                throw result.exceptionOrNull() ?: Exception("Unknown error")
            }
        } else {
            // No tools, use streaming API as normal
            apiClient.sendMessage(request, apiKey).collect { chunk ->
                emit(chunk)
            }
        }
    }

    override suspend fun sendMessageWithToolLoop(
        messages: List<Message>,
        systemPrompt: String?,
        onToolCall: suspend (toolName: String, arguments: Map<String, String>) -> Result<String>
    ): Flow<StreamChunk> = flow {
        // Check model provider
        val modelProvider = ModelProvider.fromString(getModelProvider())

        // For Ollama, tools are not supported yet, use regular flow
        if (modelProvider == ModelProvider.OLLAMA) {
            Napier.d("Ollama provider selected, bypassing tool loop")
            sendMessageWithUsage(messages, systemPrompt).collect { emit(it) }
            return@flow
        }

        // For Claude, continue with tool loop
        val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")
        val jsonModeEnabled = getJsonMode()
        val mcpEnabled = getMcpEnabled()

        // Check if tools are available
        if (!toolExecutionService.hasTools(mcpEnabled)) {
            Napier.w("Tool loop called but no tools available, falling back to normal flow")
            sendMessageWithUsage(messages, systemPrompt).collect { emit(it) }
            return@flow
        }

        // Prepare parameters
        val finalSystemPrompt = prepareSystemPrompt(systemPrompt, jsonModeEnabled)
        val selectedModel = getSelectedModel()
        val temperature = getTemperature()
        val claudeMessages = mapToClaudeMessages(messages)

        // Delegate to ToolExecutionService
        toolExecutionService.executeToolLoop(
            initialMessages = claudeMessages,
            systemPrompt = finalSystemPrompt,
            selectedModel = selectedModel,
            temperature = temperature,
            maxTokens = MAX_TOKENS,
            apiKey = apiKey,
            mcpEnabled = mcpEnabled,
            onToolCall = onToolCall
        ).collect { emit(it) }
    }

    override suspend fun sendMessageComparison(
        messages: List<Message>,
        systemPrompt: String?
    ): Result<ModelComparisonResponse> {
        // Check model provider - comparison mode only works with Claude
        val modelProvider = ModelProvider.fromString(getModelProvider())
        if (modelProvider == ModelProvider.OLLAMA) {
            return Result.failure(IllegalStateException("Model comparison mode is not supported with Ollama"))
        }

        val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")
        val finalSystemPrompt = systemPrompt ?: DEFAULT_SYSTEM_PROMPT
        val temperature = getTemperature()

        // Delegate to ModelComparisonOrchestrator
        return modelComparisonOrchestrator.compareModels(
            messages = messages,
            systemPrompt = finalSystemPrompt,
            temperature = temperature,
            apiKey = apiKey
        )
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

    // ============================================================================
    // RAG (Retrieval-Augmented Generation) Methods
    // ============================================================================

    override suspend fun indexDocument(title: String, content: String): Result<String> {
        return try {
            val result = ragService.indexDocument(title, content)
            if (result.isSuccess) {
                // Save index to storage
                saveRagIndex()
                Result.success(result.getOrThrow().id)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to index document"))
            }
        } catch (e: Exception) {
            Napier.e("Error indexing document", e)
            Result.failure(e)
        }
    }

    override suspend fun searchRagIndex(query: String, topK: Int): Result<String> {
        return try {
            val rerankingEnabled = getRagRerankingEnabled()

            val searchResult = if (rerankingEnabled) {
                // Use advanced search with reranking
                val config = RagSearchConfig(
                    topK = topK,
                    minSimilarity = 0.15,         // Low threshold for initial search - reranking will filter
                    enableReranking = true,
                    rerankTopN = topK * 4,        // Rerank 4x more candidates for better selection
                    minRerankScore = 0.85,        // Very high threshold to avoid false positives from small LLM
                    useHybridScoring = true,      // Combine similarity + rerank scores
                    similarityWeight = 0.4,       // Slightly favor rerank score
                    rerankWeight = 0.6
                )
                ragService.searchWithConfig(query, config)
            } else {
                // Use simple search without reranking
                ragService.search(query, topK, minSimilarity = 0.25)  // Moderate threshold without reranking
            }

            if (searchResult.isSuccess) {
                val results = searchResult.getOrThrow()

                // Log search details for debugging
                Napier.d("RAG search query: \"$query\", reranking: $rerankingEnabled")

                // Check if we have any meaningful results
                if (results.isEmpty()) {
                    Napier.d("No relevant documents found (filtered out by thresholds). Model will answer from general knowledge.")
                    // Return empty string - model will answer without RAG context
                    return Result.success("")
                }

                // Log relevance scores for debugging
                val maxScore = results.maxOfOrNull { result ->
                    result.rerankScore ?: result.similarity
                } ?: 0.0
                val avgScore = results.map { it.rerankScore ?: it.similarity }.average()

                // Detailed logging for each result
                Napier.d("RAG search results for query: \"$query\"")
                results.forEachIndexed { index, result ->
                    val simScore = (result.similarity * 1000).toInt() / 1000.0
                    val rerankScore = result.rerankScore?.let { (it * 1000).toInt() / 1000.0 }
                    Napier.d("  ${index + 1}. ${result.documentTitle} - sim: $simScore, rerank: $rerankScore")
                }
                Napier.d("Score summary - max: ${(maxScore * 1000).toInt() / 1000.0}, avg: ${(avgScore * 1000).toInt() / 1000.0}")

                // Additional quality check: if max score is too low, documents are not relevant enough
                // Higher thresholds to ensure only truly relevant documents are used
                // Without reranking: cosine similarity alone is unreliable, need high threshold (0.75+)
                // With reranking: Small LLM (3B params) can give false positives, need high threshold (0.85+)
                val minAcceptableScore = if (rerankingEnabled) 0.85 else 0.75
                if (maxScore < minAcceptableScore) {
                    Napier.d("Documents found but max score ($maxScore) below threshold ($minAcceptableScore). Model will answer from general knowledge.")
                    return Result.success("")
                }

                val context = ragService.generateContext(results)
                Result.success(context)
            } else {
                Result.failure(searchResult.exceptionOrNull() ?: Exception("Failed to search"))
            }
        } catch (e: Exception) {
            Napier.e("Error searching RAG index", e)
            Result.failure(e)
        }
    }

    override suspend fun getIndexedDocuments(): List<com.claude.chat.data.model.RagDocument> {
        return ragService.getIndexedDocuments()
    }

    override suspend fun removeRagDocument(documentId: String): Boolean {
        val removed = ragService.removeDocument(documentId)
        if (removed) {
            saveRagIndex()
        }
        return removed
    }

    override suspend fun clearRagIndex() {
        ragService.clearIndex()
        settingsStorage.clearRagIndex()

        // Also delete the file
        val deleted = fileStorage.deleteFile(RAG_INDEX_FILENAME)
        if (deleted) {
            Napier.d("RAG index file deleted")
        }
    }

    override suspend fun saveRagIndex(): Result<Boolean> {
        return try {
            val indexJson = ragService.saveIndexToJson()
            if (indexJson != null) {
                // Save to file only (Preferences has size limits)
                val fileSaved = fileStorage.writeTextFile(RAG_INDEX_FILENAME, indexJson)
                if (fileSaved) {
                    Napier.i("RAG index saved to file: $RAG_INDEX_FILENAME (${indexJson.length} chars)")
                    Result.success(true)
                } else {
                    Napier.w("Failed to save RAG index to file")
                    Result.failure(Exception("Failed to save RAG index to file"))
                }
            } else {
                Napier.d("No RAG index to save")
                Result.success(false)
            }
        } catch (e: Exception) {
            Napier.e("Error saving RAG index", e)
            Result.failure(e)
        }
    }

    override suspend fun loadRagIndex(): Result<Boolean> {
        return try {
            // Try to load from file first
            val indexJson = fileStorage.readTextFile(RAG_INDEX_FILENAME)
            if (indexJson != null) {
                val loaded = ragService.loadIndexFromJson(indexJson)
                Napier.i("RAG index loaded from file: ${indexJson.length} chars, loaded=$loaded")
                Result.success(loaded)
            } else {
                // Fallback: try old storage in Preferences (for backward compatibility)
                val oldIndexJson = settingsStorage.getRagIndex()
                if (oldIndexJson != null) {
                    Napier.d("Found RAG index in old storage, migrating to file...")
                    val loaded = ragService.loadIndexFromJson(oldIndexJson)
                    if (loaded) {
                        // Save to new file storage and clear old storage
                        saveRagIndex()
                        settingsStorage.clearRagIndex()
                    }
                    Result.success(loaded)
                } else {
                    Napier.d("No RAG index found")
                    Result.success(false)
                }
            }
        } catch (e: Exception) {
            Napier.e("Error loading RAG index", e)
            Result.failure(e)
        }
    }

    override suspend fun getRagMode(): Boolean {
        return settingsStorage.getRagMode()
    }

    override suspend fun saveRagMode(enabled: Boolean) {
        settingsStorage.saveRagMode(enabled)
        if (enabled) {
            // Load index when RAG mode is enabled
            loadRagIndex()
        }
    }

    override suspend fun getRagRerankingEnabled(): Boolean {
        return settingsStorage.getRagRerankingEnabled()
    }

    override suspend fun saveRagRerankingEnabled(enabled: Boolean) {
        settingsStorage.saveRagRerankingEnabled(enabled)
    }

    // ============================================================================
    // Model Provider Methods
    // ============================================================================

    override suspend fun getModelProvider(): String {
        return settingsStorage.getModelProvider()
    }

    override suspend fun saveModelProvider(provider: String) {
        settingsStorage.saveModelProvider(provider)
    }

    override suspend fun getOllamaBaseUrl(): String {
        return settingsStorage.getOllamaBaseUrl()
    }

    override suspend fun saveOllamaBaseUrl(url: String) {
        settingsStorage.saveOllamaBaseUrl(url)
    }

    override suspend fun getOllamaModel(): String {
        return settingsStorage.getOllamaModel()
    }

    override suspend fun saveOllamaModel(model: String) {
        settingsStorage.saveOllamaModel(model)
    }

    override suspend fun listOllamaModels(): Result<List<String>> {
        return ollamaClient.listModels()
    }

    override suspend fun checkOllamaHealth(): Boolean {
        return ollamaClient.checkHealth()
    }

    /**
     * Send message to Ollama and return as Flow
     */
    private suspend fun sendMessageToOllama(
        messages: List<Message>,
        systemPrompt: String?
    ): Flow<StreamChunk> = flow {
        try {
            val ollamaMessages = mapToOllamaMessages(messages, systemPrompt)
            val ollamaModel = getOllamaModel()
            val temperature = getTemperature()

            val options = OllamaOptions(temperature = temperature)

            Napier.d("Sending message to Ollama with model $ollamaModel")

            val result = ollamaClient.sendChatMessage(
                messages = ollamaMessages,
                model = ollamaModel,
                options = options
            )

            if (result.isSuccess) {
                val response = result.getOrThrow()

                // Emit the response text
                emit(StreamChunk(text = response.message.content))

                // Emit completion
                emit(StreamChunk(isComplete = true))

                Napier.d("Ollama response received successfully")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Napier.e("Ollama request failed: $error")
                throw Exception("Ollama error: $error")
            }
        } catch (e: Exception) {
            Napier.e("Error in Ollama message flow", e)
            throw e
        }
    }
}
