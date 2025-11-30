package com.claude.chat.domain.service

import com.claude.chat.data.mcp.McpManager
import com.claude.chat.data.model.ClaudeContentBlock
import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageContent
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.model.StreamChunk
import com.claude.chat.data.model.ToolUseInfo
import com.claude.chat.data.remote.ClaudeApiClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Service for executing tool loops with Claude API
 * Handles multi-turn conversations with tool use
 */
class ToolExecutionService(
    private val apiClient: ClaudeApiClient,
    private val mcpManager: McpManager
) {
    companion object {
        private const val MAX_TOOL_LOOP_ITERATIONS = 10
    }

    /**
     * Execute a tool loop - multi-turn conversation with tool execution
     *
     * @param initialMessages Initial conversation messages (already converted to Claude format)
     * @param systemPrompt System prompt to use
     * @param selectedModel Model ID to use
     * @param temperature Temperature setting
     * @param maxTokens Maximum tokens for response
     * @param apiKey API key for authentication
     * @param mcpEnabled Whether MCP tools are enabled
     * @param onToolCall Callback for executing tool calls
     * @return Flow of StreamChunks with tool responses
     */
    suspend fun executeToolLoop(
        initialMessages: List<ClaudeMessage>,
        systemPrompt: String,
        selectedModel: String,
        temperature: Double,
        maxTokens: Int,
        apiKey: String,
        mcpEnabled: Boolean,
        onToolCall: suspend (toolName: String, arguments: Map<String, String>) -> Result<String>
    ): Flow<StreamChunk> = flow {
        // Get tools if MCP is enabled
        val tools = if (mcpEnabled && mcpManager.isInitialized()) {
            val claudeTools = mcpManager.getClaudeTools()
            Napier.d("Tool loop: MCP enabled with ${claudeTools.size} tools")
            claudeTools
        } else {
            Napier.d("Tool loop: MCP not enabled")
            null
        }

        // If no tools available, this shouldn't have been called
        if (tools.isNullOrEmpty()) {
            Napier.w("Tool loop called but no tools available")
            return@flow
        }

        // Start tool loop
        var conversationMessages = initialMessages.toMutableList()
        var continueLoop = true
        var loopCount = 0

        while (continueLoop && loopCount < MAX_TOOL_LOOP_ITERATIONS) {
            loopCount++
            Napier.d("Tool loop iteration $loopCount")

            // Create request for current conversation state
            val request = ClaudeMessageRequest(
                model = selectedModel,
                messages = conversationMessages,
                maxTokens = maxTokens,
                stream = false,
                system = systemPrompt,
                temperature = temperature,
                tools = tools
            )

            // Send request to Claude
            val result = apiClient.sendMessageNonStreaming(request, apiKey)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: Exception("Unknown error in tool loop")
            }

            val response = result.getOrThrow()

            // Emit token usage information
            emit(StreamChunk(usage = response.usage))

            // Process response content
            val assistantBlocks = mutableListOf<ClaudeContentBlock>()
            val toolUseCalls = mutableListOf<ToolUseInfo>()

            response.content.forEach { content ->
                when (content.type) {
                    "text" -> {
                        content.text?.let { text ->
                            emit(StreamChunk(text = text))
                            assistantBlocks.add(
                                ClaudeContentBlock(
                                    type = "text",
                                    text = text
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
                val toolResultBlocks = executeToolCalls(toolUseCalls, onToolCall)

                // Add tool results to conversation as a user message
                conversationMessages.add(createMessageWithBlocks("user", toolResultBlocks))

                // Continue loop to send results back to Claude
                continueLoop = true
            } else {
                // No tool calls, conversation is complete
                continueLoop = false
            }
        }

        if (loopCount >= MAX_TOOL_LOOP_ITERATIONS) {
            Napier.w("Tool loop reached max iterations ($MAX_TOOL_LOOP_ITERATIONS)")
        }

        // Emit completion
        emit(StreamChunk(isComplete = true))
        Napier.d("Tool loop completed after $loopCount iterations")
    }

    /**
     * Execute multiple tool calls and return result blocks
     */
    private suspend fun executeToolCalls(
        toolUseCalls: List<ToolUseInfo>,
        onToolCall: suspend (toolName: String, arguments: Map<String, String>) -> Result<String>
    ): List<ClaudeContentBlock> {
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

        return toolResultBlocks
    }

    /**
     * Check if tools are available
     */
    fun hasTools(mcpEnabled: Boolean): Boolean {
        return mcpEnabled && mcpManager.isInitialized() && mcpManager.availableTools.isNotEmpty()
    }

    /**
     * Get available tools if MCP is enabled
     */
    fun getTools(mcpEnabled: Boolean): List<com.claude.chat.data.model.ClaudeTool>? {
        return if (mcpEnabled && mcpManager.isInitialized()) {
            mcpManager.getClaudeTools()
        } else {
            null
        }
    }

    /**
     * Creates a ClaudeMessage with content blocks
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
}