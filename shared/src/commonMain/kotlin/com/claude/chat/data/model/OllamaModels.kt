package com.claude.chat.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request for Ollama embeddings API
 */
@Serializable
data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String
)

/**
 * Response from Ollama embeddings API
 */
@Serializable
data class OllamaEmbeddingResponse(
    val embedding: List<Double>
)

/**
 * Request for Ollama generate API (for chat completion)
 */
@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val context: List<String>? = null
)

/**
 * Response from Ollama generate API
 */
@Serializable
data class OllamaGenerateResponse(
    val model: String,
    val response: String,
    @SerialName("created_at")
    val createdAt: String,
    val done: Boolean
)