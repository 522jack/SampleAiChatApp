package com.claude.chat.domain.manager

import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Manager for chat message history operations
 * Handles loading, saving, clearing, and manipulating message history
 */
@OptIn(ExperimentalUuidApi::class)
class ChatHistoryManager(
    private val repository: ChatRepository
) {
    /**
     * Load messages from storage
     */
    suspend fun loadMessages(): Result<List<Message>> {
        return try {
            val messages = repository.getMessages()
            Napier.d("Loaded ${messages.size} messages")
            Result.success(messages)
        } catch (e: Exception) {
            Napier.e("Error loading messages", e)
            Result.failure(e)
        }
    }

    /**
     * Save messages to storage
     */
    suspend fun saveMessages(messages: List<Message>): Result<Unit> {
        return try {
            repository.saveMessages(messages)
            Napier.d("Saved ${messages.size} messages")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error saving messages", e)
            Result.failure(e)
        }
    }

    /**
     * Clear all message history
     */
    suspend fun clearHistory(): Result<Unit> {
        return try {
            repository.clearMessages()
            Napier.d("Message history cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error clearing history", e)
            Result.failure(e)
        }
    }

    /**
     * Create a new user message
     *
     * @param text Message content
     * @return Created message
     */
    fun createUserMessage(text: String): Message {
        return Message(
            id = Uuid.random().toString(),
            content = text,
            role = MessageRole.USER,
            timestamp = Clock.System.now()
        )
    }

    /**
     * Create a new assistant message
     *
     * @param id Message ID (can be pre-generated for streaming updates)
     * @param content Message content
     * @param inputTokens Optional input token count
     * @param outputTokens Optional output token count
     * @param isFromRag Indicates if this message was generated using RAG
     * @return Created message
     */
    fun createAssistantMessage(
        id: String = Uuid.random().toString(),
        content: String,
        inputTokens: Int? = null,
        outputTokens: Int? = null,
        isFromRag: Boolean = false
    ): Message {
        return Message(
            id = id,
            content = content,
            role = MessageRole.ASSISTANT,
            timestamp = Clock.System.now(),
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            isFromRag = isFromRag
        )
    }

    /**
     * Create an error message from assistant
     *
     * @param errorText Error description
     * @return Error message
     */
    fun createErrorMessage(errorText: String): Message {
        return Message(
            id = Uuid.random().toString(),
            content = "Error: $errorText",
            role = MessageRole.ASSISTANT,
            timestamp = Clock.System.now(),
            isError = true
        )
    }

    /**
     * Add user message to message list
     *
     * @param userMessage The user message to add
     * @param currentMessages Current message list
     * @return Updated message list with user message appended
     */
    fun addUserMessage(userMessage: Message, currentMessages: List<Message>): List<Message> {
        return currentMessages + userMessage
    }

    /**
     * Update or add assistant message in message list
     *
     * @param messageId ID of the message to update
     * @param content New message content
     * @param inputTokens Optional input token count
     * @param outputTokens Optional output token count
     * @param isFromRag Indicates if this message was generated using RAG
     * @param currentMessages Current message list
     * @return Updated message list
     */
    fun updateAssistantMessage(
        messageId: String,
        content: String,
        inputTokens: Int?,
        outputTokens: Int?,
        isFromRag: Boolean = false,
        currentMessages: List<Message>
    ): List<Message> {
        val existingIndex = currentMessages.indexOfFirst { it.id == messageId }

        return if (existingIndex >= 0) {
            // Update existing message
            val updated = currentMessages[existingIndex].copy(
                content = content,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                isFromRag = isFromRag
            )
            currentMessages.toMutableList().apply {
                set(existingIndex, updated)
            }
        } else {
            // Add new message
            currentMessages + createAssistantMessage(messageId, content, inputTokens, outputTokens, isFromRag)
        }
    }

    /**
     * Get messages for retry - everything up to and including the last user message
     *
     * @param currentMessages Current message list
     * @return Pair of (messages to keep, last user message) or null if no user message found
     */
    fun getMessagesForRetry(currentMessages: List<Message>): Pair<List<Message>, Message>? {
        val lastUserMessage = currentMessages.lastOrNull { it.role == MessageRole.USER }
            ?: return null

        val messagesToKeep = currentMessages
            .takeWhile { it.id != lastUserMessage.id } + lastUserMessage

        return messagesToKeep to lastUserMessage
    }

    /**
     * Check if compression should be attempted based on message count
     *
     * @param currentMessages Current message list
     * @return True if there are enough messages to consider compression
     */
    fun shouldAttemptCompression(currentMessages: List<Message>): Boolean {
        // Attempt compression if we have more than 10 messages
        return currentMessages.size > 10
    }
}