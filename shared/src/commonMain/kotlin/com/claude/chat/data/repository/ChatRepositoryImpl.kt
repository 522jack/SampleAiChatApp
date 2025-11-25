package com.claude.chat.data.repository

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.mcp.McpManager
import com.claude.chat.data.model.ClaudeContentBlock
import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageContent
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.model.ClaudeModel
import com.claude.chat.data.model.McpTool
import com.claude.chat.data.model.StreamChunk
import com.claude.chat.data.model.ToolUseInfo
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.chat.domain.model.ClaudePricing
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import com.claude.chat.domain.model.ModelComparisonResponse
import com.claude.chat.domain.model.ModelResponse
import com.claude.chat.domain.service.MessageCompressionService
import com.claude.chat.domain.service.RagService
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
    private val settingsStorage: SettingsStorage,
    private val mcpManager: McpManager,
    private val ragService: RagService
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
        private const val HAIKU_3_MAX_TOKENS = 4096
        private const val DEFAULT_SYSTEM_PROMPT = "You are Claude, a helpful AI assistant."
        private const val RAG_INDEX_FILENAME = "rag_index.json"

        // Models for comparison: Haiku 3 (fast), Sonnet 3.7 (balanced), Sonnet 4.5 (most capable)
        private val COMPARISON_MODELS = listOf(
            ClaudeModel.HAIKU_3,        // claude-3-haiku-20240307
            ClaudeModel.SONNET_3_7,     // claude-3-7-sonnet-20250219
            ClaudeModel.SONNET_4_5      // claude-sonnet-4-5-20250929
        )

        private fun getCurrentDate(): String {
            return Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()
        }

        private fun getMaxTokensForModel(modelId: String): Int {
            return if (modelId == ClaudeModel.HAIKU_3.modelId) {
                HAIKU_3_MAX_TOKENS
            } else {
                MAX_TOKENS
            }
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

    /**
     * Sends a single model comparison request and returns the model response
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
        val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")
        val jsonModeEnabled = getJsonMode()
        val mcpEnabled = getMcpEnabled()

        val finalSystemPrompt = prepareSystemPrompt(systemPrompt, jsonModeEnabled)
        val selectedModel = getSelectedModel()
        val temperature = getTemperature()

        // Add MCP tools if enabled
        val tools = if (mcpEnabled && mcpManager.isInitialized()) {
            val claudeTools = mcpManager.getClaudeTools()
            Napier.d("Tool loop: MCP enabled with ${claudeTools.size} tools")
            claudeTools
        } else {
            Napier.d("Tool loop: MCP not enabled")
            null
        }

        if (tools.isNullOrEmpty()) {
            Napier.w("Tool loop called but no tools available, falling back to normal flow")
            // No tools, just use normal flow
            sendMessageWithUsage(messages, systemPrompt).collect { emit(it) }
            return@flow
        }

        // Start tool use loop
        var conversationMessages = mapToClaudeMessages(messages).toMutableList()
        var continueLoop = true
        var loopCount = 0
        val maxLoops = 10 // Prevent infinite loops

        while (continueLoop && loopCount < maxLoops) {
            loopCount++
            Napier.d("Tool loop iteration $loopCount")

            val request = ClaudeMessageRequest(
                model = selectedModel,
                messages = conversationMessages,
                maxTokens = MAX_TOKENS,
                stream = false,
                system = finalSystemPrompt,
                temperature = temperature,
                tools = tools
            )

            // Send request to Claude
            val result = apiClient.sendMessageNonStreaming(request, apiKey)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: Exception("Unknown error")
            }

            val response = result.getOrThrow()

            // Emit usage information
            emit(StreamChunk(usage = response.usage))

            // Process response content
            val assistantBlocks = mutableListOf<ClaudeContentBlock>()
            val toolUseCalls = mutableListOf<ToolUseInfo>()

            response.content.forEach { content ->
                when (content.type) {
                    "text" -> {
                        content.text?.let {
                            emit(StreamChunk(text = it))
                            assistantBlocks.add(
                                ClaudeContentBlock(
                                    type = "text",
                                    text = it
                                )
                            )
                        }
                    }
                    "tool_use" -> {
                        if (content.id != null && content.name != null && content.input != null) {
                            Napier.d("Claude requested tool: ${content.name}")
                            val toolUse = ToolUseInfo(
                                id = content.id,
                                name = content.name,
                                input = content.input
                            )
                            toolUseCalls.add(toolUse)
                            assistantBlocks.add(
                                ClaudeContentBlock(
                                    type = "tool_use",
                                    id = content.id,
                                    name = content.name,
                                    input = content.input
                                )
                            )
                            emit(StreamChunk(toolUse = toolUse))
                        }
                    }
                }
            }

            // Add assistant's response to conversation
            if (assistantBlocks.isNotEmpty()) {
                conversationMessages.add(createMessageWithBlocks("assistant", assistantBlocks))
            }

            // Execute tool calls if any
            if (toolUseCalls.isNotEmpty()) {
                val toolResultBlocks = mutableListOf<ClaudeContentBlock>()

                for (toolUse in toolUseCalls) {
                    Napier.d("Executing tool: ${toolUse.name}")

                    // Convert JsonObject to Map<String, String>
                    val arguments = toolUse.input.entries.associate { (key, value) ->
                        key to when (value) {
                            is kotlinx.serialization.json.JsonPrimitive -> value.content
                            else -> value.toString()
                        }
                    }

                    val toolResult = onToolCall(toolUse.name, arguments)

                    val resultBlock = if (toolResult.isSuccess) {
                        val resultText = toolResult.getOrNull() ?: ""
                        Napier.d("Tool ${toolUse.name} succeeded, result length: ${resultText.length}")
                        ClaudeContentBlock(
                            type = "tool_result",
                            toolUseId = toolUse.id,
                            content = resultText
                        )
                    } else {
                        val errorMsg = toolResult.exceptionOrNull()?.message ?: "Unknown error"
                        Napier.e("Tool ${toolUse.name} failed: $errorMsg")
                        ClaudeContentBlock(
                            type = "tool_result",
                            toolUseId = toolUse.id,
                            content = "Error: $errorMsg",
                            isError = true
                        )
                    }

                    toolResultBlocks.add(resultBlock)
                }

                // Add tool results to conversation as a user message
                conversationMessages.add(createMessageWithBlocks("user", toolResultBlocks))

                // Continue loop to send results back to Claude
                continueLoop = true
            } else {
                // No tool calls, conversation is complete
                continueLoop = false
            }
        }

        if (loopCount >= maxLoops) {
            Napier.w("Tool loop reached max iterations ($maxLoops)")
        }

        // Emit completion
        emit(StreamChunk(isComplete = true))
        Napier.d("Tool loop completed after $loopCount iterations")
    }

    override suspend fun sendMessageComparison(
        messages: List<Message>,
        systemPrompt: String?
    ): Result<ModelComparisonResponse> = coroutineScope {
        try {
            val apiKey = getApiKey() ?: throw IllegalStateException("API key not configured")
            val finalSystemPrompt = systemPrompt ?: DEFAULT_SYSTEM_PROMPT
            val temperature = getTemperature()

            // Prepare messages for API (exclude comparison responses, but include summaries)
            val claudeMessages = messages
                .filter { it.comparisonResponse == null }
                .let { mapToClaudeMessages(it) }

            // Get user question (last user message)
            val userQuestion = messages.lastOrNull { it.role == MessageRole.USER }?.content
                ?: "Question"

            Napier.d("Sending comparison requests to ${COMPARISON_MODELS.size} models")

            // Send requests to all models in parallel
            val modelResponses = COMPARISON_MODELS.map { model ->
                async {
                    sendSingleModelRequest(model, claudeMessages, finalSystemPrompt, temperature, apiKey)
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
            val searchResult = ragService.search(query, topK)
            if (searchResult.isSuccess) {
                val results = searchResult.getOrThrow()
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
}
