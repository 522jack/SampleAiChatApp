package com.claude.chat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.chat.data.model.McpTool
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import com.claude.chat.domain.prompts.TechSpecPrompts
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel for chat screen following MVI pattern.
 * Handles chat interactions, message streaming, model selection, and special modes
 * (Tech Spec mode, Model Comparison mode).
 */
@OptIn(ExperimentalUuidApi::class)
class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        loadMessages()
        checkApiKey()
        loadTechSpecMode()
        loadSelectedModel()
        loadModelComparisonMode()
        loadMcpTools()
    }

    // ============================================================================
    // Public API
    // ============================================================================

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.ClearHistory -> clearHistory()
            is ChatIntent.RetryLastMessage -> retryLastMessage()
            is ChatIntent.CopyMessage -> copyMessage(intent.message)
            is ChatIntent.LoadMessages -> loadMessages()
            is ChatIntent.CheckApiKey -> checkApiKey()
            is ChatIntent.SelectModel -> selectModel(intent.modelId)
            is ChatIntent.ReloadSettings -> reloadSettings()
            is ChatIntent.DismissCompressionNotification -> dismissCompressionNotification()
            is ChatIntent.ExecuteMcpTool -> executeMcpTool(intent.toolName, intent.arguments)
        }
    }

    // ============================================================================
    // Initialization & Settings
    // ============================================================================

    private fun reloadSettings() {
        loadTechSpecMode()
        loadModelComparisonMode()
    }

    private fun checkApiKey() {
        viewModelScope.launch {
            try {
                val isConfigured = repository.isApiKeyConfigured()
                _state.update { it.copy(isApiKeyConfigured = isConfigured) }
            } catch (e: Exception) {
                Napier.e("Error checking API key", e)
            }
        }
    }

    private fun loadTechSpecMode() {
        viewModelScope.launch {
            try {
                val isTechSpecMode = repository.getTechSpecMode()
                _state.update { it.copy(isTechSpecMode = isTechSpecMode) }
                Napier.d("Tech Spec mode loaded: $isTechSpecMode")
            } catch (e: Exception) {
                Napier.e("Error loading Tech Spec mode", e)
            }
        }
    }

    private fun loadSelectedModel() {
        viewModelScope.launch {
            try {
                val selectedModel = repository.getSelectedModel()
                _state.update { it.copy(selectedModel = selectedModel) }
                Napier.d("Selected model loaded: $selectedModel")
            } catch (e: Exception) {
                Napier.e("Error loading selected model", e)
            }
        }
    }

    private fun loadModelComparisonMode() {
        viewModelScope.launch {
            try {
                val isComparisonMode = repository.getModelComparisonMode()
                _state.update { it.copy(isModelComparisonMode = isComparisonMode) }
                Napier.d("Model comparison mode loaded: $isComparisonMode")
            } catch (e: Exception) {
                Napier.e("Error loading model comparison mode", e)
            }
        }
    }

    private fun selectModel(modelId: String) {
        viewModelScope.launch {
            try {
                repository.saveSelectedModel(modelId)
                _state.update { it.copy(selectedModel = modelId) }
                Napier.d("Model selected: $modelId")
            } catch (e: Exception) {
                Napier.e("Error selecting model", e)
            }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            try {
                val messages = repository.getMessages()
                _state.update { it.copy(messages = messages) }
            } catch (e: Exception) {
                Napier.e("Error loading messages", e)
                _state.update { it.copy(error = "Failed to load messages") }
            }
        }
    }

    // ============================================================================
    // MCP Tools
    // ============================================================================

    private fun loadMcpTools() {
        viewModelScope.launch {
            try {
                val mcpEnabled = repository.getMcpEnabled()
                if (mcpEnabled) {
                    repository.initializeMcpTools()
                }
                val tools = repository.getAvailableMcpTools()
                _state.update {
                    it.copy(
                        mcpEnabled = mcpEnabled,
                        availableMcpTools = tools
                    )
                }
                Napier.d("MCP tools loaded: ${tools.size} tools available")
            } catch (e: Exception) {
                Napier.e("Error loading MCP tools", e)
            }
        }
    }

    private fun executeMcpTool(toolName: String, arguments: Map<String, String>) {
        viewModelScope.launch {
            try {
                // Add user message showing tool call
                val userMessage = Message(
                    id = Uuid.random().toString(),
                    content = "🔧 Executing tool: $toolName\nArguments: ${arguments.entries.joinToString(", ") { "${it.key}=${it.value}" }}",
                    role = MessageRole.USER,
                    timestamp = Clock.System.now()
                )

                val updatedMessages = _state.value.messages + userMessage
                _state.update { it.copy(messages = updatedMessages, isLoading = true) }
                repository.saveMessages(updatedMessages)

                // Call the tool through repository
                val result = repository.callMcpTool(toolName, arguments)

                // Create assistant message with result
                val assistantMessage = if (result.isSuccess) {
                    Message(
                        id = Uuid.random().toString(),
                        content = "✅ Tool result:\n${result.getOrNull()}",
                        role = MessageRole.ASSISTANT,
                        timestamp = Clock.System.now()
                    )
                } else {
                    Message(
                        id = Uuid.random().toString(),
                        content = "❌ Tool error: ${result.exceptionOrNull()?.message}",
                        role = MessageRole.ASSISTANT,
                        timestamp = Clock.System.now(),
                        isError = true
                    )
                }

                val finalMessages = _state.value.messages + assistantMessage
                _state.update { it.copy(messages = finalMessages, isLoading = false) }
                repository.saveMessages(finalMessages)

                Napier.d("MCP tool executed: $toolName")
            } catch (e: Exception) {
                Napier.e("Error executing MCP tool", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to execute tool: ${e.message}"
                    )
                }
            }
        }
    }

    // ============================================================================
    // Message Sending
    // ============================================================================

    private fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                val userMessage = createUserMessage(text)
                val updatedMessages = addUserMessageToState(userMessage)
                repository.saveMessages(updatedMessages)

                // Route to appropriate sending mode
                if (_state.value.isModelComparisonMode) {
                    sendMessageComparison(updatedMessages)
                } else {
                    sendMessageStreaming(text, updatedMessages)
                }
            } catch (e: Exception) {
                handleSendError(e)
            }
        }
    }

    private suspend fun sendMessageStreaming(userText: String, messages: List<Message>) {
        val systemPrompt = resolveSystemPrompt(userText)
        val assistantMessageId = Uuid.random().toString()
        var assistantContent = ""
        var inputTokens: Int? = null
        var outputTokens = 0

        repository.sendMessageWithUsage(
            messages = messages,
            systemPrompt = systemPrompt
        ).catch { error ->
            handleStreamingError(error)
        }.collect { chunk ->
            // Accumulate text content
            chunk.text?.let { assistantContent += it }

            // Update token usage
            chunk.usage?.let { usage ->
                usage.inputTokens?.let { inputTokens = it }
                usage.outputTokens?.let { outputTokens = it }
                Napier.d("Token usage: input=$inputTokens, output=$outputTokens")
            }

            // Update UI if we have new content or usage data
            if (chunk.text != null || chunk.usage != null) {
                updateAssistantMessage(
                    assistantMessageId,
                    assistantContent,
                    inputTokens,
                    outputTokens
                )
            }
        }

        // Finalize response
        if (_state.value.isTechSpecMode) {
            updateTechSpecState(userText)
        }

        _state.update { it.copy(isLoading = false) }
        repository.saveMessages(_state.value.messages)

        // Check if compression is needed after sending message
        attemptCompression()
    }

    private suspend fun sendMessageComparison(messages: List<Message>) {
        try {
            val systemPrompt = repository.getSystemPrompt()
            val result = repository.sendMessageComparison(messages, systemPrompt)

            if (result.isFailure) {
                val error = result.exceptionOrNull()
                Napier.e("Error in comparison mode", error)

                val errorMessage = createErrorMessage(
                    error?.message ?: "Failed to get comparison responses"
                )

                val messagesWithError = _state.value.messages + errorMessage
                _state.update {
                    it.copy(
                        messages = messagesWithError,
                        isLoading = false,
                        error = error?.message
                    )
                }

                repository.saveMessages(messagesWithError)
                return
            }

            val comparisonResponse = result.getOrThrow()

            // Create assistant message with comparison response
            val assistantMessage = Message(
                id = Uuid.random().toString(),
                content = "Model Comparison Response",
                role = MessageRole.ASSISTANT,
                timestamp = Clock.System.now(),
                comparisonResponse = comparisonResponse
            )

            val updatedMessages = _state.value.messages + assistantMessage
            _state.update {
                it.copy(
                    messages = updatedMessages,
                    isLoading = false
                )
            }

            repository.saveMessages(updatedMessages)

        } catch (e: Exception) {
            Napier.e("Error in comparison mode", e)
            _state.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to get comparison responses"
                )
            }
        }
    }

    // ============================================================================
    // Message Creation Helpers
    // ============================================================================

    private fun createUserMessage(text: String): Message {
        return Message(
            id = Uuid.random().toString(),
            content = text,
            role = MessageRole.USER,
            timestamp = Clock.System.now()
        )
    }

    private fun createErrorMessage(errorText: String): Message {
        return Message(
            id = Uuid.random().toString(),
            content = "Error: $errorText",
            role = MessageRole.ASSISTANT,
            timestamp = Clock.System.now(),
            isError = true
        )
    }

    private fun createAssistantMessage(
        id: String,
        content: String,
        inputTokens: Int? = null,
        outputTokens: Int = 0
    ): Message {
        return Message(
            id = id,
            content = content,
            role = MessageRole.ASSISTANT,
            timestamp = Clock.System.now(),
            inputTokens = inputTokens,
            outputTokens = outputTokens
        )
    }

    // ============================================================================
    // State Update Helpers
    // ============================================================================

    private fun addUserMessageToState(userMessage: Message): List<Message> {
        val updatedMessages = _state.value.messages + userMessage
        _state.update {
            it.copy(
                messages = updatedMessages,
                isLoading = true,
                error = null
            )
        }
        return updatedMessages
    }

    private fun updateAssistantMessage(
        messageId: String,
        content: String,
        inputTokens: Int?,
        outputTokens: Int
    ) {
        val assistantMessage = createAssistantMessage(
            id = messageId,
            content = content,
            inputTokens = inputTokens,
            outputTokens = outputTokens
        )

        val currentMessages = _state.value.messages
        val existingIndex = currentMessages.indexOfLast { it.id == messageId }

        val newMessages = if (existingIndex >= 0) {
            currentMessages.toMutableList().apply {
                set(existingIndex, assistantMessage)
            }
        } else {
            currentMessages + assistantMessage
        }

        _state.update { it.copy(messages = newMessages) }
    }

    // ============================================================================
    // Error Handling
    // ============================================================================

    private suspend fun handleStreamingError(error: Throwable) {
        Napier.e("Error receiving message", error)

        val errorMessage = createErrorMessage(
            error.message ?: "Failed to get response"
        )

        val messagesWithError = _state.value.messages + errorMessage
        _state.update {
            it.copy(
                messages = messagesWithError,
                isLoading = false,
                error = error.message
            )
        }

        repository.saveMessages(messagesWithError)
    }

    private fun handleSendError(error: Exception) {
        Napier.e("Error sending message", error)
        _state.update {
            it.copy(
                isLoading = false,
                error = error.message ?: "Failed to send message"
            )
        }
    }

    // ============================================================================
    // System Prompt Resolution
    // ============================================================================

    private suspend fun resolveSystemPrompt(userText: String): String {
        val baseSystemPrompt = repository.getSystemPrompt()
            ?: "You are a helpful assistant."

        return if (_state.value.isTechSpecMode) {
            buildTechSpecSystemPrompt(userText)
        } else {
            baseSystemPrompt
        }
    }

    // ============================================================================
    // Tech Spec Mode
    // ============================================================================

    private fun buildTechSpecSystemPrompt(userText: String): String {
        return when {
            // First message: initiate questions
            _state.value.techSpecInitialRequest == null -> {
                TechSpecPrompts.getInitialPrompt(userText)
            }
            // Questions 2-5: continue asking
            _state.value.techSpecQuestionsAsked < 5 -> {
                val questionNumber = _state.value.techSpecQuestionsAsked + 1
                TechSpecPrompts.getContinuationPrompt(
                    initialRequest = _state.value.techSpecInitialRequest ?: userText,
                    questionsAsked = _state.value.techSpecQuestionsAsked,
                    questionNumber = questionNumber
                )
            }
            // All questions collected: create specification
            else -> {
                TechSpecPrompts.getFinalSpecificationPrompt(
                    initialRequest = _state.value.techSpecInitialRequest ?: userText
                )
            }
        }
    }

    private fun updateTechSpecState(userText: String) {
        _state.update { currentState ->
            when {
                // First message: save initial request and increment counter
                currentState.techSpecInitialRequest == null -> {
                    Napier.d("Tech Spec: Saved initial request and asked first question")
                    currentState.copy(
                        techSpecInitialRequest = userText,
                        techSpecQuestionsAsked = 1
                    )
                }
                // Questions 2-5: increment counter
                currentState.techSpecQuestionsAsked < 5 -> {
                    val newCount = currentState.techSpecQuestionsAsked + 1
                    Napier.d("Tech Spec: Asked question $newCount of 5")
                    currentState.copy(techSpecQuestionsAsked = newCount)
                }
                // All questions collected: reset state for next session
                else -> {
                    Napier.d("Tech Spec: Created final specification, resetting state")
                    currentState.copy(
                        techSpecInitialRequest = null,
                        techSpecQuestionsAsked = 0
                    )
                }
            }
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            try {
                repository.clearMessages()
                _state.update {
                    it.copy(
                        messages = emptyList(),
                        error = null,
                        techSpecInitialRequest = null,
                        techSpecQuestionsAsked = 0
                    )
                }
            } catch (e: Exception) {
                Napier.e("Error clearing history", e)
                _state.update { it.copy(error = "Failed to clear history") }
            }
        }
    }

    private fun retryLastMessage() {
        val lastUserMessage = _state.value.messages
            .lastOrNull { it.role == MessageRole.USER }

        if (lastUserMessage != null) {
            // Remove messages after the last user message
            val messagesToKeep = _state.value.messages
                .takeWhile { it.id != lastUserMessage.id } + lastUserMessage

            _state.update { it.copy(messages = messagesToKeep) }

            // Resend the message
            sendMessage(lastUserMessage.content)
        }
    }

    private fun copyMessage(message: Message) {
        // This will be handled by platform-specific code
        Napier.d("Copy message: ${message.content}")
    }

    // ============================================================================
    // Message Compression
    // ============================================================================

    private fun attemptCompression() {
        viewModelScope.launch {
            try {
                val result = repository.compressMessages()

                if (result.isSuccess && result.getOrNull() == true) {
                    // Compression was performed
                    val messages = repository.getMessages()
                    val lastSummary = messages.lastOrNull { it.isSummary }

                    val notification = if (lastSummary != null) {
                        val count = lastSummary.summarizedMessageCount ?: 0
                        "History compressed: $count messages summarized to save tokens"
                    } else {
                        "History compressed successfully"
                    }

                    _state.update {
                        it.copy(
                            messages = messages,
                            compressionNotification = notification
                        )
                    }

                    Napier.d("Compression notification shown: $notification")
                }
            } catch (e: Exception) {
                Napier.e("Error during compression attempt", e)
                // Don't show error to user - compression is optional optimization
            }
        }
    }

    private fun dismissCompressionNotification() {
        _state.update { it.copy(compressionNotification = null) }
    }
}

/**
 * UI State for chat screen
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isApiKeyConfigured: Boolean = false,
    val isTechSpecMode: Boolean = false,
    val techSpecInitialRequest: String? = null,
    val techSpecQuestionsAsked: Int = 0,
    val selectedModel: String = "claude-3-5-haiku-20241022",
    val isModelComparisonMode: Boolean = false,
    val compressionNotification: String? = null,
    val mcpEnabled: Boolean = false,
    val availableMcpTools: List<McpTool> = emptyList()
)

/**
 * User intents for chat screen
 */
sealed class ChatIntent {
    data class SendMessage(val text: String) : ChatIntent()
    data object ClearHistory : ChatIntent()
    data object RetryLastMessage : ChatIntent()
    data class CopyMessage(val message: Message) : ChatIntent()
    data object LoadMessages : ChatIntent()
    data object CheckApiKey : ChatIntent()
    data class SelectModel(val modelId: String) : ChatIntent()
    data object ReloadSettings : ChatIntent()
    data object DismissCompressionNotification : ChatIntent()
    data class ExecuteMcpTool(val toolName: String, val arguments: Map<String, String>) : ChatIntent()
}
