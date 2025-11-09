package com.claude.chat.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel for chat screen following MVI pattern
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
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.ClearHistory -> clearHistory()
            is ChatIntent.RetryLastMessage -> retryLastMessage()
            is ChatIntent.CopyMessage -> copyMessage(intent.message)
            is ChatIntent.LoadMessages -> loadMessages()
            is ChatIntent.CheckApiKey -> checkApiKey()
            is ChatIntent.SelectModel -> selectModel(intent.modelId)
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
                val selectedModel = repository.getSelectedModel()
                _state.update { it.copy(selectedModel = selectedModel) }
                Napier.d("Selected model loaded: $selectedModel")
            } catch (e: Exception) {
                Napier.e("Error loading selected model", e)
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

    private fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                // Add user message
                val userMessage = Message(
                    id = Uuid.random().toString(),
                    content = text,
                    role = MessageRole.USER,
                    timestamp = Clock.System.now()
                )

                val updatedMessages = _state.value.messages + userMessage
                _state.update {
                    it.copy(
                        messages = updatedMessages,
                        isLoading = true,
                        error = null
                    )
                }

                // Save messages
                repository.saveMessages(updatedMessages)

                // Determine system prompt based on Tech Spec mode
                val baseSystemPrompt = repository.getSystemPrompt() ?: "You are a helpful assistant."
                val systemPrompt = if (_state.value.isTechSpecMode) {
                    buildTechSpecSystemPrompt(text, baseSystemPrompt)
                } else {
                    baseSystemPrompt
                }

                // Get streaming response
                val assistantMessageId = Uuid.random().toString()
                var assistantContent = ""

                repository.sendMessage(
                    messages = updatedMessages,
                    systemPrompt = systemPrompt
                ).catch { error ->
                    Napier.e("Error receiving message", error)

                    val errorMessage = Message(
                        id = Uuid.random().toString(),
                        content = "Error: ${error.message ?: "Failed to get response"}",
                        role = MessageRole.ASSISTANT,
                        timestamp = Clock.System.now(),
                        isError = true
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
                }.collect { chunk ->
                    assistantContent += chunk

                    val assistantMessage = Message(
                        id = assistantMessageId,
                        content = assistantContent,
                        role = MessageRole.ASSISTANT,
                        timestamp = Clock.System.now()
                    )

                    // Update UI with partial message
                    val currentMessages = _state.value.messages
                    val existingIndex = currentMessages.indexOfLast { it.id == assistantMessageId }

                    val newMessages = if (existingIndex >= 0) {
                        currentMessages.toMutableList().apply {
                            set(existingIndex, assistantMessage)
                        }
                    } else {
                        currentMessages + assistantMessage
                    }

                    _state.update { it.copy(messages = newMessages) }
                }

                // Update Tech Spec state after receiving response
                if (_state.value.isTechSpecMode) {
                    updateTechSpecState(text, assistantContent)
                }

                // Save final messages
                _state.update { it.copy(isLoading = false) }
                repository.saveMessages(_state.value.messages)

            } catch (e: Exception) {
                Napier.e("Error sending message", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to send message"
                    )
                }
            }
        }
    }

    private fun buildTechSpecSystemPrompt(userText: String, basePrompt: String): String {
        return when {
            // First message: initiate questions
            _state.value.techSpecInitialRequest == null -> {
                """You are an AI assistant helping to create a technical specification through a structured interview process.

CRITICAL RULES:
1. You MUST ask EXACTLY ONE question in your response
2. Do NOT provide multiple questions or numbered lists of questions
3. Do NOT create a specification yet - only ask ONE clarifying question
4. Your entire response should be a SINGLE question about the user's request

The user's request is: "$userText"

Your task: Ask the FIRST clarifying question to better understand their requirements. Make it specific and relevant to creating a technical specification.

Remember: ONE QUESTION ONLY. Stop after asking it."""
            }
            // Questions 2-5: continue asking
            _state.value.techSpecQuestionsAsked < 5 -> {
                val questionNumber = _state.value.techSpecQuestionsAsked + 1
                val questionsLeft = 5 - _state.value.techSpecQuestionsAsked
                """You are continuing a structured interview to create a technical specification.

CRITICAL RULES:
1. You MUST ask EXACTLY ONE question in your response
2. Do NOT provide multiple questions or numbered lists
3. Do NOT create the specification yet
4. Your entire response should be ONE SINGLE question

Context:
- Original request: "${_state.value.techSpecInitialRequest}"
- You have already asked ${_state.value.techSpecQuestionsAsked} question(s)
- This will be question #$questionNumber out of 5
- You have $questionsLeft questions remaining after this one

Task: Ask question #$questionNumber. Make it build on the previous answers to gather comprehensive requirements.

Remember: ONE QUESTION ONLY. Stop immediately after asking it."""
            }
            // All questions collected: create specification
            else -> {
                """You have completed a structured interview with 5 clarifying questions about a technical specification request.

Original request: "${_state.value.techSpecInitialRequest}"

You have asked 5 clarifying questions and received answers to all of them. Now you MUST create a comprehensive technical specification document.

CRITICAL: Do NOT ask any more questions. Create the specification now.

The technical specification should include:
- Project Overview: Brief description and goals
- Functional Requirements: What the system should do
- Technical Requirements: Technologies, platforms, performance needs
- User Interface Requirements: If applicable, UI/UX considerations
- Data Requirements: Data structures, storage, security
- System Architecture: High-level design overview
- Testing Requirements: Testing strategy and criteria
- Deployment Requirements: Deployment process and environment

Format the specification with clear markdown sections and subsections. Be comprehensive and detailed based on all the information gathered."""
            }
        }
    }

    private fun updateTechSpecState(userText: String, assistantResponse: String) {
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
    val selectedModel: String = "claude-3-5-haiku-20241022"
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
}
