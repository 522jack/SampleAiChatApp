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
    val comparisonResponse: ModelComparisonResponse? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val isSummary: Boolean = false,
    val summarizedMessageCount: Int? = null,
    val summarizedTokens: Int? = null,
    val tokensSaved: Int? = null,
    val isFromRag: Boolean = false  // Indicates if this response was generated using RAG
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
