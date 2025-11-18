package com.claude.chat.data.mcp

import com.claude.chat.data.model.ClaudeTool
import com.claude.chat.data.model.McpTool
import io.github.aakira.napier.Napier
import io.ktor.client.*

/**
 * Manager for MCP clients and tools
 * Handles initialization and tool retrieval from multiple MCP servers
 */
class McpManager(
    private val httpClient: HttpClient? = null,
    private val weatherApiKey: String? = null
) {
    private val clients = mutableListOf<McpClient>()
    private var _availableTools = emptyList<McpTool>()

    val availableTools: List<McpTool>
        get() = _availableTools

    /**
     * Initialize with all available MCP clients
     */
    suspend fun initialize() {
        try {
            // Initialize simple MCP client with basic tools
            val simpleClient = SimpleMcpClient()
            val simpleResult = simpleClient.initialize()

            if (simpleResult.isSuccess) {
                clients.add(simpleClient)
                Napier.i("MCP client initialized: ${simpleClient.serverInfo?.name}")
            } else {
                Napier.e("Failed to initialize Simple MCP client", simpleResult.exceptionOrNull())
            }

            // Initialize Weather MCP client if HTTP client is provided
            if (httpClient != null) {
                val weatherClient = WeatherMcpClient(
                    httpClient = httpClient,
                    apiKey = weatherApiKey ?: "demo"
                )
                val weatherResult = weatherClient.initialize()

                if (weatherResult.isSuccess) {
                    clients.add(weatherClient)
                    Napier.i("MCP client initialized: ${weatherClient.serverInfo?.name}")
                } else {
                    Napier.e("Failed to initialize Weather MCP client", weatherResult.exceptionOrNull())
                }
            } else {
                Napier.w("HTTP client not provided, Weather MCP client will not be initialized")
            }

            refreshTools()
        } catch (e: Exception) {
            Napier.e("Error initializing MCP manager", e)
        }
    }

    /**
     * Refresh the list of available tools from all clients
     */
    suspend fun refreshTools() {
        val allTools = mutableListOf<McpTool>()

        clients.forEach { client ->
            val result = client.listTools()
            if (result.isSuccess) {
                allTools.addAll(result.getOrNull() ?: emptyList())
            } else {
                Napier.e("Failed to list tools from client", result.exceptionOrNull())
            }
        }

        _availableTools = allTools
        Napier.i("Available MCP tools: ${allTools.size}")
    }

    /**
     * Convert MCP tools to Claude API tool format
     */
    fun getClaudeTools(): List<ClaudeTool> {
        return _availableTools.map { mcpTool ->
            ClaudeTool(
                name = mcpTool.name,
                description = mcpTool.description,
                inputSchema = mcpTool.inputSchema
            )
        }
    }

    /**
     * Call a tool by name
     */
    suspend fun callTool(name: String, arguments: Map<String, Any>): Result<String> {
        // Find the client that has this tool
        for (client in clients) {
            val toolsResult = client.listTools()
            if (toolsResult.isSuccess) {
                val tools = toolsResult.getOrNull() ?: emptyList()
                if (tools.any { it.name == name }) {
                    val result = client.callTool(name, arguments)
                    return if (result.isSuccess) {
                        val toolResult = result.getOrNull()!!
                        val text = toolResult.content
                            .filter { it.type == "text" }
                            .mapNotNull { it.text }
                            .joinToString("\n")
                        Result.success(text)
                    } else {
                        Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
                    }
                }
            }
        }

        return Result.failure(Exception("Tool not found: $name"))
    }

    /**
     * Check if any clients are initialized
     */
    fun isInitialized(): Boolean = clients.any { it.isInitialized }

    /**
     * Close all clients
     */
    suspend fun close() {
        clients.forEach { it.close() }
        clients.clear()
        _availableTools = emptyList()
    }
}