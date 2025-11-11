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

/**
 * Available Claude models via API
 * Model IDs from: https://docs.anthropic.com/en/docs/about-claude/models
 */
enum class ClaudeModel(
    val modelId: String,
    val displayName: String,
    val description: String
) {
    // Latest models (as of January 2025)
    SONNET_4_5("claude-sonnet-4-5-20250929", "Claude Sonnet 4.5", "Most capable model"),
    SONNET_4("claude-sonnet-4-20250514", "Claude Sonnet 4", "High performance"),
    SONNET_3_7("claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet", "Advanced reasoning"),
    SONNET_3_5("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", "High intelligence"),
    HAIKU_3_5("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", "Fast and efficient"),

    // Older models
    OPUS_3("claude-3-opus-20240229", "Claude 3 Opus", "Powerful reasoning"),
    SONNET_3_OLD("claude-3-5-sonnet-20240620", "Claude 3.5 Sonnet (Old)", "Previous version"),
    SONNET_3("claude-3-sonnet-20240229", "Claude 3 Sonnet", "Balanced"),
    HAIKU_3("claude-3-haiku-20240307", "Claude 3 Haiku", "Fastest response");

    companion object {
        fun fromModelId(modelId: String): ClaudeModel {
            return entries.find { it.modelId == modelId } ?: HAIKU_3_5
        }
    }
}
