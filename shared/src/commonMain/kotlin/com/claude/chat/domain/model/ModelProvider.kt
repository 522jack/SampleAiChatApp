package com.claude.chat.domain.model

/**
 * Enum representing the type of AI model provider
 */
enum class ModelProvider(
    val displayName: String,
    val description: String
) {
    CLAUDE(
        displayName = "Claude (Cloud)",
        description = "Anthropic's Claude models via API"
    ),
    OLLAMA(
        displayName = "Ollama (Local)",
        description = "Local models via Ollama"
    );

    companion object {
        fun fromString(value: String): ModelProvider {
            return entries.find { it.name == value } ?: CLAUDE
        }
    }
}