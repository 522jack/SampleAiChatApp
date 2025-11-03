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
    }

    fun onIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.ClearHistory -> clearHistory()
            is ChatIntent.RetryLastMessage -> retryLastMessage()
            is ChatIntent.CopyMessage -> copyMessage(intent.message)
            is ChatIntent.LoadMessages -> loadMessages()
            is ChatIntent.CheckApiKey -> checkApiKey()
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

                // Get streaming response
                val assistantMessageId = Uuid.random().toString()
                var assistantContent = ""

                repository.sendMessage(
                    messages = updatedMessages,
                    systemPrompt = repository.getSystemPrompt()
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

    private fun clearHistory() {
        viewModelScope.launch {
            try {
                repository.clearMessages()
                _state.update { it.copy(messages = emptyList(), error = null) }
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
    val isApiKeyConfigured: Boolean = false
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
}
