package com.claude.chat.data.mcp

import com.claude.chat.data.model.*
import io.github.aakira.napier.Napier

/**
 * MCP Client for launching local MCP servers via STDIO
 *
 * Note: This is a common interface. Platform-specific implementations
 * are required for actual process launching (see platform source sets).
 */
expect class ProcessMcpClient(
    config: McpServerConfig
) : McpClient {
    override val isInitialized: Boolean
    override val serverInfo: McpServerInfo?

    override suspend fun initialize(): Result<McpInitializeResult>
    override suspend fun listTools(): Result<List<McpTool>>
    override suspend fun callTool(name: String, arguments: Map<String, Any>): Result<McpToolCallResult>
    override suspend fun listResources(): Result<List<McpResource>>
    override suspend fun listPrompts(): Result<List<McpPrompt>>
    override suspend fun close()
}