package com.claude.chat.data.remote

import com.claude.chat.data.model.ClaudeErrorResponse
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.model.ClaudeMessageResponse
import com.claude.chat.data.model.ClaudeStreamEvent
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Implementation of Claude API client using Ktor
 */
class ClaudeApiClientImpl(
    private val httpClient: HttpClient
) : ClaudeApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1"
        private const val MESSAGES_ENDPOINT = "$BASE_URL/messages"
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    override suspend fun sendMessage(
        request: ClaudeMessageRequest,
        apiKey: String
    ): Flow<String> = flow {
        try {
            // Validate API key before making request
            val trimmedKey = apiKey.trim()
            if (!trimmedKey.startsWith("sk-ant-")) {
                throw IllegalArgumentException("Invalid API key format. Please check your API key in Settings.")
            }

            val streamingRequest = request.copy(stream = true)

            httpClient.preparePost(MESSAGES_ENDPOINT) {
                headers {
                    append("x-api-key", trimmedKey)
                    append("anthropic-version", ANTHROPIC_VERSION)
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                }
                setBody(streamingRequest)
                timeout {
                    requestTimeoutMillis = 120000 // 2 minutes
                }
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    val errorBody = response.bodyAsText()
                    Napier.e("API Error: ${response.status} - $errorBody")

                    val errorMessage = when (response.status.value) {
                        401 -> "Authentication failed. Please check your API key in Settings."
                        403 -> "Access forbidden. Your API key may not have the required permissions."
                        429 -> "Rate limit exceeded. Please try again later."
                        500, 502, 503, 504 -> "Claude API is temporarily unavailable. Please try again later."
                        else -> "API Error: ${response.status.description}"
                    }

                    throw Exception(errorMessage)
                }

                val channel: ByteReadChannel = response.body()

                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break

                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()

                        if (data == "[DONE]") {
                            break
                        }

                        try {
                            val event = json.decodeFromString<ClaudeStreamEvent>(data)

                            when (event.type) {
                                "content_block_delta" -> {
                                    event.delta?.text?.let { text ->
                                        emit(text)
                                    }
                                }
                                "error" -> {
                                    Napier.e("Streaming error: $data")
                                    throw Exception("Streaming error occurred")
                                }
                            }
                        } catch (e: Exception) {
                            Napier.w("Failed to parse event: $data", e)
                        }
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            Napier.e("Invalid API key", e)
            throw e
        } catch (e: Exception) {
            Napier.e("Error in streaming API call", e)

            // Check if it's a header validation error
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("illegal character", ignoreCase = true) ||
                errorMessage.contains("IllegalHeaderValueException", ignoreCase = true)) {
                throw IllegalArgumentException("Invalid API key. The saved key contains invalid characters. Please update your API key in Settings.")
            }

            throw e
        }
    }

    override suspend fun sendMessageNonStreaming(
        request: ClaudeMessageRequest,
        apiKey: String
    ): Result<ClaudeMessageResponse> {
        return try {
            val nonStreamingRequest = request.copy(stream = false)

            val response: HttpResponse = httpClient.post(MESSAGES_ENDPOINT) {
                headers {
                    append("x-api-key", apiKey)
                    append("anthropic-version", ANTHROPIC_VERSION)
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                }
                setBody(nonStreamingRequest)
                timeout {
                    requestTimeoutMillis = 120000 // 2 minutes
                }
            }

            if (response.status == HttpStatusCode.OK) {
                val messageResponse = response.body<ClaudeMessageResponse>()
                Result.success(messageResponse)
            } else {
                val errorBody = response.bodyAsText()
                Napier.e("API Error: ${response.status} - $errorBody")

                try {
                    val errorResponse = json.decodeFromString<ClaudeErrorResponse>(errorBody)
                    Result.failure(Exception(errorResponse.error.message))
                } catch (e: Exception) {
                    Result.failure(Exception("API Error: ${response.status.description}"))
                }
            }
        } catch (e: Exception) {
            Napier.e("Error in API call", e)
            Result.failure(e)
        }
    }
}
