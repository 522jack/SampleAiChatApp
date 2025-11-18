package com.claude.chat.data.mcp

import com.claude.chat.data.model.*
import io.github.aakira.napier.Napier

/**
 * Android implementation of ProcessMcpClient
 * Note: Process launching is limited on Android. This is a placeholder implementation.
 * For production use, consider using RemoteMcpClient instead.
 */
actual class ProcessMcpClient actual constructor(
    private val config: McpServerConfig
) : McpClient {
    actual override val isInitialized: Boolean = false
    actual override val serverInfo: McpServerInfo? = null

    actual override suspend fun initialize(): Result<McpInitializeResult> {
        Napier.w("ProcessMcpClient is not fully supported on Android. Use RemoteMcpClient for HTTP/SSE connections.")
        return Result.failure(UnsupportedOperationException("Process launching not supported on Android"))
    }

    actual override suspend fun listTools(): Result<List<McpTool>> {
        return Result.failure(IllegalStateException("Client not initialized"))
    }

    actual override suspend fun callTool(name: String, arguments: Map<String, Any>): Result<McpToolCallResult> {
        return Result.failure(IllegalStateException("Client not initialized"))
    }

    actual override suspend fun listResources(): Result<List<McpResource>> {
        return Result.failure(IllegalStateException("Client not initialized"))
    }

    actual override suspend fun listPrompts(): Result<List<McpPrompt>> {
        return Result.failure(IllegalStateException("Client not initialized"))
    }

    actual override suspend fun close() {
        // Nothing to close
    }
}