package com.claude.chat.data.mcp

import com.claude.chat.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Interface for MCP (Model Context Protocol) client
 * Manages connection to MCP servers and provides tools/resources to Claude
 */
interface McpClient {
    /**
     * Initialize connection to MCP server
     */
    suspend fun initialize(): Result<McpInitializeResult>

    /**
     * Get list of available tools from MCP server
     */
    suspend fun listTools(): Result<List<McpTool>>

    /**
     * Call a tool on the MCP server
     */
    suspend fun callTool(name: String, arguments: Map<String, Any>): Result<McpToolCallResult>

    /**
     * Get list of available resources
     */
    suspend fun listResources(): Result<List<McpResource>>

    /**
     * Get list of available prompts
     */
    suspend fun listPrompts(): Result<List<McpPrompt>>

    /**
     * Check if client is initialized
     */
    val isInitialized: Boolean

    /**
     * Get server info
     */
    val serverInfo: McpServerInfo?

    /**
     * Close the connection
     */
    suspend fun close()
}