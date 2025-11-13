package com.claude.chat.data.remote

import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.model.ClaudeMessageResponse
import com.claude.chat.data.model.StreamChunk
import kotlinx.coroutines.flow.Flow

/**
 * Interface for Claude API client
 */
interface ClaudeApiClient {
    /**
     * Send a message to Claude API and get a streaming response with token usage
     */
    suspend fun sendMessage(
        request: ClaudeMessageRequest,
        apiKey: String
    ): Flow<StreamChunk>

    /**
     * Send a message to Claude API and get a complete response
     */
    suspend fun sendMessageNonStreaming(
        request: ClaudeMessageRequest,
        apiKey: String
    ): Result<ClaudeMessageResponse>
}
