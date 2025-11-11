package com.claude.chat.domain.model

import kotlinx.datetime.Instant

/**
 * Domain model representing a chat message
 */
data class Message(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Instant,
    val isError: Boolean = false,
    val comparisonResponse: ModelComparisonResponse? = null
)

enum class MessageRole {
    USER,
    ASSISTANT
}
