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
 *
 * @param temperature Controls randomness (0.0-2.0). Lower = more deterministic, Higher = more creative. Default: 0.8
 * @param topP Nucleus sampling threshold (0.0-1.0). Only consider tokens with cumulative probability up to topP. Default: 0.9
 * @param topK Top-K sampling. Only consider top K tokens. Default: 40
 * @param numPredict Maximum number of tokens to generate. Default: 128, -1 for infinite, -2 for context length
 * @param numCtx Context window size (number of tokens). Larger = more context but slower. Default: 2048
 * @param repeatPenalty Penalty for repeating tokens (1.0 = no penalty). Default: 1.1
 * @param repeatLastN Number of last tokens to consider for repeat penalty. Default: 64, 0 to disable, -1 for num_ctx
 * @param seed Random seed for reproducible generation. Default: random
 * @param stop Stop sequences - list of strings where generation should stop
 * @param numThread Number of threads to use for generation
 * @param mirostat Mirostat sampling mode (0=disabled, 1=Mirostat, 2=Mirostat 2.0)
 * @param mirostatTau Target entropy for Mirostat
 * @param mirostatEta Learning rate for Mirostat
 */
@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
    @SerialName("num_ctx")
    val numCtx: Int? = null,
    @SerialName("repeat_penalty")
    val repeatPenalty: Double? = null,
    @SerialName("repeat_last_n")
    val repeatLastN: Int? = null,
    val seed: Int? = null,
    val stop: List<String>? = null,
    @SerialName("num_thread")
    val numThread: Int? = null,
    val mirostat: Int? = null,
    @SerialName("mirostat_tau")
    val mirostatTau: Double? = null,
    @SerialName("mirostat_eta")
    val mirostatEta: Double? = null
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