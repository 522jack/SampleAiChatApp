package com.claude.chat.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Claude API request/response models
 * Based on Claude Messages API: https://docs.anthropic.com/claude/reference/messages_post
 */

@Serializable
data class ClaudeMessageRequest(
    val model: String,
    val messages: List<ClaudeMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val stream: Boolean = false,
    val system: String? = null,
    val temperature: Double? = null
)

@Serializable
data class ClaudeMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

@Serializable
data class ClaudeMessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ClaudeContent>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    @SerialName("stop_sequence")
    val stopSequence: String? = null,
    val usage: ClaudeUsage
)

@Serializable
data class ClaudeContent(
    val type: String, // "text"
    val text: String
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)

@Serializable
data class ClaudeErrorResponse(
    val type: String,
    val error: ClaudeError
)

@Serializable
data class ClaudeError(
    val type: String,
    val message: String
)

// Streaming event models
@Serializable
data class ClaudeStreamEvent(
    val type: String,
    val index: Int? = null,
    val delta: ClaudeStreamDelta? = null,
    val message: ClaudeMessageResponse? = null,
    @SerialName("content_block")
    val contentBlock: ClaudeContent? = null,
    val usage: ClaudeUsage? = null
)

@Serializable
data class ClaudeStreamDelta(
    val type: String,
    val text: String? = null,
    @SerialName("stop_reason")
    val stopReason: String? = null
)
