package com.claude.chat.data.remote

import com.claude.chat.data.model.OllamaEmbeddingRequest
import com.claude.chat.data.model.OllamaEmbeddingResponse
import com.claude.chat.data.model.OllamaGenerateRequest
import com.claude.chat.data.model.OllamaGenerateResponse
import com.claude.chat.data.model.OllamaOptions
import com.claude.chat.data.model.OllamaChatRequest
import com.claude.chat.data.model.OllamaChatResponse
import com.claude.chat.data.model.OllamaChatMessage
import com.claude.chat.data.model.OllamaModelsResponse
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

/**
 * Client for interacting with Ollama API
 */
class OllamaClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:11434"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    /**
     * Generate embeddings for a given text using Ollama
     */
    suspend fun generateEmbedding(
        text: String,
        model: String = "nomic-embed-text"
    ): Result<List<Double>> {
        return try {
            Napier.d("Generating embedding for text of length ${text.length} with model $model")

            val request = OllamaEmbeddingRequest(
                model = model,
                prompt = text
            )

            val response: HttpResponse = httpClient.post("$baseUrl/api/embeddings") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                val embeddingResponse = response.body<OllamaEmbeddingResponse>()
                Napier.d("Successfully generated embedding of dimension ${embeddingResponse.embedding.size}")
                Result.success(embeddingResponse.embedding)
            } else {
                val error = "Ollama embedding request failed: ${response.status}"
                Napier.e(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Napier.e("Error generating embedding", e)
            val errorMessage = when {
                e.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "Cannot connect to OLLAMA at $baseUrl. Please start OLLAMA and ensure the '$model' model is installed."
                e.message?.contains("ConnectException", ignoreCase = true) == true ->
                    "Cannot connect to OLLAMA at $baseUrl. Please start OLLAMA and ensure the '$model' model is installed."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "OLLAMA connection timeout. Please check if OLLAMA is running."
                else -> "OLLAMA error: ${e.message ?: "Unknown error"}"
            }
            Result.failure(Exception(errorMessage, e))
        }
    }

    /**
     * Generate embeddings for multiple texts in batch
     */
    suspend fun generateEmbeddings(
        texts: List<String>,
        model: String = "nomic-embed-text"
    ): Result<List<List<Double>>> {
        return try {
            val embeddings = mutableListOf<List<Double>>()

            texts.forEachIndexed { index, text ->
                Napier.d("Generating embedding ${index + 1}/${texts.size}")
                val result = generateEmbedding(text, model)

                if (result.isSuccess) {
                    embeddings.add(result.getOrThrow())
                } else {
                    return Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
                }
            }

            Result.success(embeddings)
        } catch (e: Exception) {
            Napier.e("Error generating embeddings batch", e)
            Result.failure(e)
        }
    }

    /**
     * Generate text completion using Ollama
     */
    suspend fun generateCompletion(
        prompt: String,
        model: String = "llama2",
        context: List<String>? = null,
        options: OllamaOptions? = null
    ): Result<String> {
        return try {
            Napier.d("Generating completion with model $model")

            val request = OllamaGenerateRequest(
                model = model,
                prompt = prompt,
                stream = false,
                context = context,
                options = options
            )

            val response: HttpResponse = httpClient.post("$baseUrl/api/generate") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                // Get raw response text for debugging
                val rawResponse = response.bodyAsText()

                // Try to parse the response - Ollama may return NDJSON (newline-delimited JSON)
                try {
                    val json = kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }

                    // Parse NDJSON - split by newlines and parse each JSON object
                    val lines = rawResponse.trim().lines().filter { it.isNotBlank() }
                    val fullResponse = StringBuilder()

                    for (line in lines) {
                        val chunkResponse = json.decodeFromString<OllamaGenerateResponse>(line)
                        fullResponse.append(chunkResponse.response)
                    }

                    val finalText = fullResponse.toString()
                    Napier.d("Successfully generated completion: '$finalText' (length: ${finalText.length})")
                    Result.success(finalText)
                } catch (e: Exception) {
                    Napier.e("Failed to parse Ollama response: ${e.message}")
                    Napier.e("Raw response was (first 500 chars): ${rawResponse.take(500)}")
                    Result.failure(Exception("Failed to parse Ollama response: ${e.message}"))
                }
            } else {
                val error = "Ollama generate request failed: ${response.status}"
                Napier.e(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Napier.e("Error generating completion", e)
            Result.failure(e)
        }
    }

    /**
     * Send chat message using Ollama Chat API (streaming)
     */
    suspend fun sendChatMessage(
        messages: List<OllamaChatMessage>,
        model: String = "llama2",
        options: OllamaOptions? = null
    ): Result<OllamaChatResponse> {
        return try {
            Napier.d("Sending chat message with model $model")

            val request = OllamaChatRequest(
                model = model,
                messages = messages,
                stream = true, // Enable streaming to get NDJSON response
                options = options
            )

            val response: HttpResponse = httpClient.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.isSuccess()) {
                // Read the entire body as text (NDJSON format)
                val bodyText = response.bodyAsText()

                // Split by newlines and parse all JSON objects
                val lines = bodyText.trim().split("\n").filter { it.isNotBlank() }

                if (lines.isEmpty()) {
                    return Result.failure(Exception("Empty response from Ollama"))
                }

                // Collect all content from streaming chunks
                val fullContent = StringBuilder()
                var lastResponse: OllamaChatResponse? = null

                lines.forEach { line ->
                    try {
                        val chunk = json.decodeFromString<OllamaChatResponse>(line)
                        // Accumulate content from each chunk
                        if (chunk.message.content.isNotEmpty()) {
                            fullContent.append(chunk.message.content)
                        }
                        // Keep the last response for metadata
                        if (chunk.done) {
                            lastResponse = chunk
                        }
                    } catch (e: Exception) {
                        Napier.w("Failed to parse NDJSON line: $line", e)
                    }
                }

                // Use the last response but with full accumulated content
                val finalResponse = lastResponse ?: json.decodeFromString(lines.last())
                val responseWithFullContent = finalResponse.copy(
                    message = finalResponse.message.copy(content = fullContent.toString())
                )

                Napier.d("Successfully received chat response with ${fullContent.length} characters")
                Result.success(responseWithFullContent)
            } else {
                val error = "Ollama chat request failed: ${response.status}"
                Napier.e(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Napier.e("Error sending chat message", e)
            val errorMessage = when {
                e.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "Cannot connect to Ollama at $baseUrl. Please start Ollama and ensure the '$model' model is installed."
                e.message?.contains("ConnectException", ignoreCase = true) == true ->
                    "Cannot connect to Ollama at $baseUrl. Please start Ollama and ensure the '$model' model is installed."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Ollama connection timeout. Please check if Ollama is running."
                else -> "Ollama error: ${e.message ?: "Unknown error"}"
            }
            Result.failure(Exception(errorMessage, e))
        }
    }

    /**
     * List available Ollama models
     */
    suspend fun listModels(): Result<List<String>> {
        return try {
            Napier.d("Fetching list of available Ollama models")

            val response: HttpResponse = httpClient.get("$baseUrl/api/tags")

            if (response.status.isSuccess()) {
                val modelsResponse = response.body<OllamaModelsResponse>()
                val modelNames = modelsResponse.models.map { it.name }
                Napier.d("Found ${modelNames.size} models: $modelNames")
                Result.success(modelNames)
            } else {
                val error = "Ollama list models request failed: ${response.status}"
                Napier.e(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Napier.e("Error listing models", e)
            val errorMessage = when {
                e.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "Cannot connect to Ollama at $baseUrl. Please start Ollama."
                e.message?.contains("ConnectException", ignoreCase = true) == true ->
                    "Cannot connect to Ollama at $baseUrl. Please start Ollama."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Ollama connection timeout. Please check if Ollama is running."
                else -> "Ollama error: ${e.message ?: "Unknown error"}"
            }
            Result.failure(Exception(errorMessage, e))
        }
    }

    /**
     * Check if Ollama server is available
     */
    suspend fun checkHealth(): Boolean {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/tags")
            response.status.isSuccess()
        } catch (e: Exception) {
            Napier.e("Ollama health check failed", e)
            false
        }
    }
}