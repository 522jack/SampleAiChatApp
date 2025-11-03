package com.claude.chat.domain.model

/**
 * Represents the current state of the chat
 */
data class ChatState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isApiKeyConfigured: Boolean = false
)
