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
 * Options for Ollama generate API
 */
@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null
)

/**
 * Request for Ollama generate API (for chat completion)
 */
@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val context: List<String>? = null,
    val options: OllamaOptions? = null
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

/**
 * Message for Ollama chat API
 */
@Serializable
data class OllamaChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

/**
 * Request for Ollama chat API
 */
@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

/**
 * Response from Ollama chat API
 */
@Serializable
data class OllamaChatResponse(
    val model: String,
    val message: OllamaChatMessage,
    @SerialName("created_at")
    val createdAt: String,
    val done: Boolean,
    @SerialName("done_reason")
    val doneReason: String? = null,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("load_duration")
    val loadDuration: Long? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("prompt_eval_duration")
    val promptEvalDuration: Long? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
    @SerialName("eval_duration")
    val evalDuration: Long? = null
)

/**
 * Model information from Ollama
 */
@Serializable
data class OllamaModel(
    val name: String,
    @SerialName("modified_at")
    val modifiedAt: String,
    val size: Long,
    val digest: String,
    val details: OllamaModelDetails? = null
)

/**
 * Details about Ollama model
 */
@Serializable
data class OllamaModelDetails(
    val format: String? = null,
    val family: String? = null,
    @SerialName("parameter_size")
    val parameterSize: String? = null,
    @SerialName("quantization_level")
    val quantizationLevel: String? = null
)

/**
 * Response from Ollama list models API
 */
@Serializable
data class OllamaModelsResponse(
    val models: List<OllamaModel>
)