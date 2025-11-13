package com.claude.chat.data.remote

import com.claude.chat.data.model.ClaudeErrorResponse
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.model.ClaudeMessageResponse
import com.claude.chat.data.model.ClaudeStreamEvent
import com.claude.chat.data.model.StreamChunk
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
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
        private const val API_KEY_PREFIX = "sk-ant-"
        private const val REQUEST_TIMEOUT_MILLIS = 120000L // 2 minutes
        private const val STREAM_DATA_PREFIX = "data: "
        private const val STREAM_DONE_MARKER = "[DONE]"

        // Error messages
        private const val ERROR_INVALID_API_KEY_FORMAT =
            "Invalid API key format. Please check your API key in Settings."
        private const val ERROR_INVALID_API_KEY_CHARACTERS =
            "Invalid API key. The saved key contains invalid characters. Please update your API key in Settings."
        private const val ERROR_AUTH_FAILED =
            "Authentication failed. Please check your API key in Settings."
        private const val ERROR_FORBIDDEN =
            "Access forbidden. Your API key may not have the required permissions."
        private const val ERROR_RATE_LIMIT =
            "Rate limit exceeded. Please try again later."
        private const val ERROR_SERVER_UNAVAILABLE =
            "Claude API is temporarily unavailable. Please try again later."
        private const val ERROR_STREAMING = "Streaming error occurred"
    }

    override suspend fun sendMessage(
        request: ClaudeMessageRequest,
        apiKey: String
    ): Flow<StreamChunk> = flow {
        try {
            val validatedKey = validateApiKey(apiKey)
            val streamingRequest = request.copy(stream = true)

            httpClient.preparePost(MESSAGES_ENDPOINT) {
                configureHeaders(validatedKey)
                setBody(streamingRequest)
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
            }.execute { response ->
                handleErrorResponse(response)
                processStreamingResponse(response)
            }
        } catch (e: IllegalArgumentException) {
            Napier.e("Invalid API key", e)
            throw e
        } catch (e: Exception) {
            Napier.e("Error in streaming API call", e)
            handleStreamingException(e)
        }
    }

    /**
     * Validates the API key format and returns a trimmed version
     */
    private fun validateApiKey(apiKey: String): String {
        val trimmedKey = apiKey.trim()
        if (!trimmedKey.startsWith(API_KEY_PREFIX)) {
            throw IllegalArgumentException(ERROR_INVALID_API_KEY_FORMAT)
        }
        return trimmedKey
    }

    /**
     * Configures HTTP headers for API requests
     */
    private fun HttpRequestBuilder.configureHeaders(apiKey: String) {
        headers {
            append("x-api-key", apiKey)
            append("anthropic-version", ANTHROPIC_VERSION)
            append(HttpHeaders.ContentType, ContentType.Application.Json)
        }
    }

    /**
     * Handles error responses from the API
     */
    private suspend fun handleErrorResponse(response: HttpResponse) {
        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            Napier.e("API Error: ${response.status} - $errorBody")

            val errorMessage = when (response.status.value) {
                401 -> ERROR_AUTH_FAILED
                403 -> ERROR_FORBIDDEN
                429 -> ERROR_RATE_LIMIT
                500, 502, 503, 504 -> ERROR_SERVER_UNAVAILABLE
                else -> "API Error: ${response.status.description}"
            }

            throw Exception(errorMessage)
        }
    }

    /**
     * Processes streaming response from the API
     */
    private suspend fun FlowCollector<StreamChunk>.processStreamingResponse(response: HttpResponse) {
        val channel: ByteReadChannel = response.body()

        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break

            if (line.startsWith(STREAM_DATA_PREFIX)) {
                val data = line.removePrefix(STREAM_DATA_PREFIX).trim()

                if (data == STREAM_DONE_MARKER) {
                    break
                }

                processStreamEvent(data)
            }
        }
    }

    /**
     * Processes a single stream event
     */
    private suspend fun FlowCollector<StreamChunk>.processStreamEvent(data: String) {
        try {
            // Log raw events for debugging
            Napier.d("Raw event: type=${data.substringAfter("\"type\":\"").substringBefore("\"")}")

            val event = json.decodeFromString<ClaudeStreamEvent>(data)

            when (event.type) {
                "message_start" -> handleMessageStart(event)
                "content_block_delta" -> handleContentBlockDelta(event)
                "message_delta" -> handleMessageDelta(event)
                "message_stop" -> handleMessageStop()
                "error" -> handleStreamError(data)
                else -> Napier.d("Received event type: ${event.type}")
            }
        } catch (e: Exception) {
            Napier.w("Failed to parse event: $data", e)
        }
    }

    /**
     * Handles message_start event
     */
    private suspend fun FlowCollector<StreamChunk>.handleMessageStart(event: ClaudeStreamEvent) {
        event.message?.usage?.let { usage ->
            Napier.d("Received message_start with usage: input=${usage.inputTokens}, output=${usage.outputTokens}")
            // Only emit if we have input tokens (output will come in message_delta)
            if (usage.inputTokens != null) {
                emit(StreamChunk(usage = usage))
            }
        }
    }

    /**
     * Handles content_block_delta event
     */
    private suspend fun FlowCollector<StreamChunk>.handleContentBlockDelta(event: ClaudeStreamEvent) {
        event.delta?.text?.let { text ->
            emit(StreamChunk(text = text))
        }
    }

    /**
     * Handles message_delta event
     */
    private suspend fun FlowCollector<StreamChunk>.handleMessageDelta(event: ClaudeStreamEvent) {
        event.usage?.let { usage ->
            emit(StreamChunk(usage = usage))
        }
        event.delta?.let { delta ->
            Napier.d("Received message_delta with delta: type=${delta.type}, stopReason=${delta.stopReason}")
        }
        if (event.usage == null && event.delta == null) {
            Napier.w("Received message_delta without usage or delta!")
        }
    }

    /**
     * Handles message_stop event
     */
    private suspend fun FlowCollector<StreamChunk>.handleMessageStop() {
        Napier.d("Received message_stop")
        emit(StreamChunk(isComplete = true))
    }

    /**
     * Handles stream error event
     */
    private fun handleStreamError(data: String): Nothing {
        Napier.e("Streaming error: $data")
        throw Exception(ERROR_STREAMING)
    }

    /**
     * Handles exceptions during streaming
     */
    private fun handleStreamingException(e: Exception): Nothing {
        val errorMessage = e.message.orEmpty()
        if (errorMessage.contains("illegal character", ignoreCase = true) ||
            errorMessage.contains("IllegalHeaderValueException", ignoreCase = true)) {
            throw IllegalArgumentException(ERROR_INVALID_API_KEY_CHARACTERS)
        }
        throw e
    }

    override suspend fun sendMessageNonStreaming(
        request: ClaudeMessageRequest,
        apiKey: String
    ): Result<ClaudeMessageResponse> {
        return try {
            val validatedKey = validateApiKey(apiKey)
            val nonStreamingRequest = request.copy(stream = false)

            val response: HttpResponse = httpClient.post(MESSAGES_ENDPOINT) {
                configureHeaders(validatedKey)
                setBody(nonStreamingRequest)
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
            }

            handleNonStreamingResponse(response)
        } catch (e: Exception) {
            Napier.e("Error in API call", e)
            Result.failure(e)
        }
    }

    /**
     * Handles non-streaming response from the API
     */
    private suspend fun handleNonStreamingResponse(response: HttpResponse): Result<ClaudeMessageResponse> {
        return if (response.status == HttpStatusCode.OK) {
            val messageResponse = response.body<ClaudeMessageResponse>()
            Result.success(messageResponse)
        } else {
            val errorBody = response.bodyAsText()
            Napier.e("API Error: ${response.status} - $errorBody")

            val errorMessage = try {
                val errorResponse = json.decodeFromString<ClaudeErrorResponse>(errorBody)
                errorResponse.error.message
            } catch (_: Exception) {
                "API Error: ${response.status.description}"
            }

            Result.failure(Exception(errorMessage))
        }
    }
}
