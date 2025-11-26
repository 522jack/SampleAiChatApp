package com.claude.chat.data.remote

import com.claude.chat.data.model.OllamaEmbeddingRequest
import com.claude.chat.data.model.OllamaEmbeddingResponse
import com.claude.chat.data.model.OllamaGenerateRequest
import com.claude.chat.data.model.OllamaGenerateResponse
import com.claude.chat.data.model.OllamaOptions
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Client for interacting with Ollama API
 */
class OllamaClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:11434"
) {
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
                val generateResponse = response.body<OllamaGenerateResponse>()
                Napier.d("Successfully generated completion")
                Result.success(generateResponse.response)
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