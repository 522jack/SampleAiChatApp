package com.claude.chat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.chat.data.model.McpTool
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.domain.manager.ChatHistoryManager
import com.claude.chat.domain.manager.TechSpecManager
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import com.claude.chat.domain.service.MessageSendingOrchestrator
import com.claude.chat.presentation.chat.mvi.ChatIntent
import com.claude.chat.presentation.chat.mvi.ChatUiState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel for chat screen following MVI pattern.
 * Delegates business logic to specialized managers and orchestrators.
 * Handles only UI state management and coordination.
 */
@OptIn(ExperimentalUuidApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val chatHistoryManager: ChatHistoryManager,
    private val messageSendingOrchestrator: MessageSendingOrchestrator,
    private val techSpecManager: TechSpecManager
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
        loadRagMode()
        loadRagDocuments()
        loadModelProvider()
        loadUserProfileState()
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
            is ChatIntent.StartTaskSummaryPolling -> startTaskSummaryPolling()
            is ChatIntent.StopTaskSummaryPolling -> stopTaskSummaryPolling()
            is ChatIntent.RequestTaskSummary -> requestTaskSummary()
            is ChatIntent.IndexDocument -> indexDocument(intent.title, intent.content)
            is ChatIntent.RemoveRagDocument -> removeRagDocument(intent.documentId)
            is ChatIntent.LoadRagDocuments -> loadRagDocuments()
            is ChatIntent.LoadUserProfile -> loadUserProfile(intent.jsonContent)
            is ChatIntent.ClearUserProfile -> clearUserProfile()
            is ChatIntent.LoadUserProfileState -> loadUserProfileState()
        }
    }

    // ============================================================================
    // Initialization & Settings
    // ============================================================================

    private fun reloadSettings() {
        loadTechSpecMode()
        loadModelComparisonMode()
        loadRagMode()
        loadModelProvider()
        loadSelectedModel()
    }

    private fun loadRagMode() {
        viewModelScope.launch {
            try {
                val isRagMode = repository.getRagMode()
                _state.update { it.copy(isRagMode = isRagMode) }
                Napier.d("RAG mode loaded: $isRagMode")
            } catch (e: Exception) {
                Napier.e("Error loading RAG mode", e)
            }
        }
    }

    private fun loadRagDocuments() {
        viewModelScope.launch {
            try {
                val documents = repository.getIndexedDocuments()
                _state.update { it.copy(ragDocuments = documents) }
                Napier.d("Loaded ${documents.size} RAG documents")
            } catch (e: Exception) {
                Napier.e("Error loading RAG documents", e)
            }
        }
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
                val modelProvider = repository.getModelProvider()
                val selectedModel = if (modelProvider == "OLLAMA") {
                    repository.getOllamaModel()
                } else {
                    repository.getSelectedModel()
                }
                _state.update { it.copy(selectedModel = selectedModel) }
                Napier.d("Selected model loaded: $selectedModel (provider: $modelProvider)")
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

    private fun loadModelProvider() {
        viewModelScope.launch {
            try {
                val modelProvider = repository.getModelProvider()
                _state.update { it.copy(modelProvider = modelProvider) }
                Napier.d("Model provider loaded: $modelProvider")

                // Load Ollama models if Ollama is selected
                if (modelProvider == "OLLAMA") {
                    val modelsResult = repository.listOllamaModels()
                    if (modelsResult.isSuccess) {
                        val models = modelsResult.getOrThrow()
                        _state.update { it.copy(ollamaModels = models) }
                        Napier.d("Ollama models loaded: ${models.size} models")
                    } else {
                        Napier.e("Failed to load Ollama models: ${modelsResult.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error loading model provider", e)
            }
        }
    }

    private fun selectModel(modelId: String) {
        viewModelScope.launch {
            try {
                val modelProvider = _state.value.modelProvider

                if (modelProvider == "OLLAMA") {
                    // Save as Ollama model
                    repository.saveOllamaModel(modelId)
                } else {
                    // Save as Claude model
                    repository.saveSelectedModel(modelId)
                }

                _state.update { it.copy(selectedModel = modelId) }
                Napier.d("Model selected: $modelId (provider: $modelProvider)")
            } catch (e: Exception) {
                Napier.e("Error selecting model", e)
            }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            val result = chatHistoryManager.loadMessages()
            if (result.isSuccess) {
                _state.update { it.copy(messages = result.getOrThrow()) }
            } else {
                Napier.e("Error loading messages", result.exceptionOrNull())
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
                // Create user message showing tool call
                val userMessage = chatHistoryManager.createUserMessage(
                    "🔧 Executing tool: $toolName\nArguments: ${arguments.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
                )

                val updatedMessages = chatHistoryManager.addUserMessage(userMessage, _state.value.messages)
                _state.update { it.copy(messages = updatedMessages, isLoading = true) }
                chatHistoryManager.saveMessages(updatedMessages)

                // Call the tool through repository
                val result = repository.callMcpTool(toolName, arguments)

                // Create assistant message with result
                val assistantMessage = if (result.isSuccess) {
                    chatHistoryManager.createAssistantMessage(
                        content = "✅ Tool result:\n${result.getOrNull()}"
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
                chatHistoryManager.saveMessages(finalMessages)

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
                val userMessage = chatHistoryManager.createUserMessage(text)
                val updatedMessages = chatHistoryManager.addUserMessage(userMessage, _state.value.messages)
                _state.update {
                    it.copy(
                        messages = updatedMessages,
                        isLoading = true,
                        error = null
                    )
                }
                chatHistoryManager.saveMessages(updatedMessages)

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
        val assistantMessageId = Uuid.random().toString()
        var assistantContent = ""
        var inputTokens: Int? = null
        var outputTokens = 0
        var wasRagUsed = false

        try {
            // Delegate message sending to orchestrator
            messageSendingOrchestrator.sendMessage(
                MessageSendingOrchestrator.SendConfig(
                    userText = userText,
                    messages = messages,
                    isRagMode = _state.value.isRagMode,
                    isTechSpecMode = _state.value.isTechSpecMode,
                    techSpecState = _state.value.techSpecState,
                    onToolExecution = { toolName, result ->
                        assistantContent += "\n\n$result"
                        updateAssistantMessageInState(assistantMessageId, assistantContent, inputTokens, outputTokens, wasRagUsed)
                    },
                    onRagUsageDetected = { ragUsed ->
                        wasRagUsed = ragUsed
                        Napier.d("RAG usage detected: $ragUsed")
                    }
                )
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
                    updateAssistantMessageInState(assistantMessageId, assistantContent, inputTokens, outputTokens, wasRagUsed)
                }
            }

            // Finalize response
            if (_state.value.isTechSpecMode) {
                val newState = techSpecManager.updateState(userText, _state.value.techSpecState)
                _state.update { it.copy(techSpecState = newState) }
            }

            _state.update { it.copy(isLoading = false) }
            chatHistoryManager.saveMessages(_state.value.messages)

            // Check if compression is needed after sending message
            attemptCompression()
        } catch (e: Exception) {
            handleStreamingError(e)
        }
    }

    private suspend fun sendMessageComparison(messages: List<Message>) {
        try {
            messageSendingOrchestrator.sendMessageComparison(messages).collect { result ->
                when (result) {
                    is MessageSendingOrchestrator.ComparisonResult.Success -> {
                        val assistantMessage = Message(
                            id = Uuid.random().toString(),
                            content = "Model Comparison Response",
                            role = MessageRole.ASSISTANT,
                            timestamp = Clock.System.now(),
                            comparisonResponse = result.response
                        )

                        val updatedMessages = _state.value.messages + assistantMessage
                        _state.update {
                            it.copy(
                                messages = updatedMessages,
                                isLoading = false
                            )
                        }

                        chatHistoryManager.saveMessages(updatedMessages)
                    }

                    is MessageSendingOrchestrator.ComparisonResult.Error -> {
                        val errorMessage = chatHistoryManager.createErrorMessage(result.message)
                        val messagesWithError = _state.value.messages + errorMessage
                        _state.update {
                            it.copy(
                                messages = messagesWithError,
                                isLoading = false,
                                error = result.message
                            )
                        }
                        chatHistoryManager.saveMessages(messagesWithError)
                    }
                }
            }
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
    // State Update Helpers
    // ============================================================================

    private fun updateAssistantMessageInState(
        messageId: String,
        content: String,
        inputTokens: Int?,
        outputTokens: Int,
        isFromRag: Boolean = false
    ) {
        val updatedMessages = chatHistoryManager.updateAssistantMessage(
            messageId = messageId,
            content = content,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            isFromRag = isFromRag,
            currentMessages = _state.value.messages
        )
        _state.update { it.copy(messages = updatedMessages) }
    }

    // ============================================================================
    // Error Handling
    // ============================================================================

    private suspend fun handleStreamingError(error: Throwable) {
        Napier.e("Error receiving message", error)

        val errorMessage = chatHistoryManager.createErrorMessage(
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

        chatHistoryManager.saveMessages(messagesWithError)
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
    // History Operations
    // ============================================================================

    private fun clearHistory() {
        viewModelScope.launch {
            val result = chatHistoryManager.clearHistory()
            if (result.isSuccess) {
                val resetState = techSpecManager.resetState()
                _state.update {
                    it.copy(
                        messages = emptyList(),
                        error = null,
                        techSpecState = resetState
                    )
                }
            } else {
                Napier.e("Error clearing history", result.exceptionOrNull())
                _state.update { it.copy(error = "Failed to clear history") }
            }
        }
    }

    private fun retryLastMessage() {
        val retryInfo = chatHistoryManager.getMessagesForRetry(_state.value.messages)
        if (retryInfo != null) {
            val (messagesToKeep, lastUserMessage) = retryInfo
            _state.update { it.copy(messages = messagesToKeep) }
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
        if (!chatHistoryManager.shouldAttemptCompression(_state.value.messages)) {
            return
        }

        viewModelScope.launch {
            try {
                val result = repository.compressMessages()

                if (result.isSuccess && result.getOrNull() == true) {
                    // Compression was performed
                    val messagesResult = chatHistoryManager.loadMessages()
                    if (messagesResult.isSuccess) {
                        val messages = messagesResult.getOrThrow()
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

    // ============================================================================
    // Task Summary Polling
    // ============================================================================

    private fun startTaskSummaryPolling() {
        _state.update { it.copy(isTaskSummaryEnabled = true) }
        Napier.i("Task summary polling enabled")
    }

    private fun stopTaskSummaryPolling() {
        _state.update { it.copy(isTaskSummaryEnabled = false) }
        Napier.i("Task summary polling disabled")
    }

    private fun requestTaskSummary() {
        Napier.d("Manual task summary requested")
    }

    /**
     * Add task summary message to chat (called from Android-specific code)
     */
    fun addTaskSummaryMessage(summary: String) {
        val summaryMessage = Message(
            id = Uuid.random().toString(),
            content = summary,
            role = MessageRole.SYSTEM,
            timestamp = Clock.System.now()
        )

        val updatedMessages = _state.value.messages + summaryMessage
        _state.update { it.copy(messages = updatedMessages) }

        viewModelScope.launch {
            chatHistoryManager.saveMessages(updatedMessages)
        }
    }

    // ============================================================================
    // RAG (Retrieval-Augmented Generation)
    // ============================================================================

    private fun indexDocument(title: String, content: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }

                val result = repository.indexDocument(title, content)

                if (result.isSuccess) {
                    Napier.d("Document indexed successfully: $title")
                    loadRagDocuments()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to index document"
                    Napier.e("Error indexing document: $error")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = error
                        )
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error indexing document", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to index document"
                    )
                }
            }
        }
    }

    private fun removeRagDocument(documentId: String) {
        viewModelScope.launch {
            try {
                val removed = repository.removeRagDocument(documentId)
                if (removed) {
                    Napier.d("Document removed: $documentId")
                    loadRagDocuments()
                }
            } catch (e: Exception) {
                Napier.e("Error removing document", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // ============================================================================
    // User Profile Methods
    // ============================================================================

    private fun loadUserProfile(jsonContent: String) {
        viewModelScope.launch {
            try {
                val result = repository.loadUserProfile(jsonContent)
                if (result.isSuccess) {
                    val profile = result.getOrThrow()
                    _state.update {
                        it.copy(
                            userProfile = profile,
                            isUserProfileActive = true,
                            error = null
                        )
                    }
                    Napier.d("User profile loaded: ${profile.name}")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to load profile"
                    _state.update { it.copy(error = error) }
                    Napier.e("Error loading user profile: $error")
                }
            } catch (e: Exception) {
                Napier.e("Error loading user profile", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun clearUserProfile() {
        viewModelScope.launch {
            try {
                val cleared = repository.clearUserProfile()
                if (cleared) {
                    _state.update {
                        it.copy(
                            userProfile = null,
                            isUserProfileActive = false
                        )
                    }
                    Napier.d("User profile cleared")
                }
            } catch (e: Exception) {
                Napier.e("Error clearing user profile", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun loadUserProfileState() {
        viewModelScope.launch {
            try {
                val profile = repository.getUserProfile()
                _state.update {
                    it.copy(
                        userProfile = profile,
                        isUserProfileActive = profile != null
                    )
                }
                if (profile != null) {
                    Napier.d("User profile state loaded: ${profile.name}")
                } else {
                    Napier.d("No user profile found")
                }
            } catch (e: Exception) {
                Napier.e("Error loading user profile state", e)
            }
        }
    }
}