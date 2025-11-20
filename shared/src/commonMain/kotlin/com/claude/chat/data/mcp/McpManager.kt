package com.claude.chat.data.mcp

import com.claude.chat.data.local.SettingsStorage
import com.claude.chat.data.model.*
import io.github.aakira.napier.Napier
import io.ktor.client.*

/**
 * Manager for MCP clients and tools
 * Handles initialization and tool retrieval from multiple MCP servers
 */
class McpManager(
    private val httpClient: HttpClient? = null,
    private val weatherApiKey: String? = null,
    private val settingsStorage: SettingsStorage? = null
) {
    private val clients = mutableMapOf<String, McpClient>()
    private val externalServers = mutableListOf<McpServerConfig>()
    private var _availableTools = emptyList<McpTool>()

    val availableTools: List<McpTool>
        get() = _availableTools

    /**
     * Initialize with all available MCP clients
     */
    suspend fun initialize() {
        try {
            // Load saved external servers from storage
            loadServersFromStorage()

            // Initialize simple MCP client with basic tools
            val simpleClient = SimpleMcpClient()
            val simpleResult = simpleClient.initialize()

            if (simpleResult.isSuccess) {
                clients["simple"] = simpleClient
                Napier.i("MCP client initialized: ${simpleClient.serverInfo?.name}")
            } else {
                Napier.e("Failed to initialize Simple MCP client", simpleResult.exceptionOrNull())
            }

            // Initialize external servers
            initializeExternalServers()

            refreshTools()
        } catch (e: Exception) {
            Napier.e("Error initializing MCP manager", e)
        }
    }

    /**
     * Load servers from persistent storage
     */
    private fun loadServersFromStorage() {
        settingsStorage?.let { storage ->
            val savedServers = storage.getMcpServers()
            externalServers.clear()
            externalServers.addAll(savedServers)
            Napier.i("Loaded ${savedServers.size} MCP servers from storage")
        }
    }

    /**
     * Save servers to persistent storage
     */
    private fun saveServersToStorage() {
        settingsStorage?.let { storage ->
            storage.saveMcpServers(externalServers.toList())
            Napier.d("Saved ${externalServers.size} MCP servers to storage")
        }
    }

    /**
     * Add external MCP server configuration and initialize it
     */
    suspend fun addExternalServer(config: McpServerConfig) {
        externalServers.add(config)
        saveServersToStorage()
        Napier.i("Added external MCP server: ${config.name}")

        if (!config.enabled) {
            Napier.i("Skipping disabled MCP server: ${config.name}")
            return
        }

        // Initialize the newly added server
        try {
            val client = when (config.type) {
                McpServerType.HTTP -> {
                    if (httpClient == null) {
                        Napier.e("HTTP client not available for HTTP MCP server")
                        return
                    }
                    HttpMcpClient(config, httpClient)
                }
                McpServerType.PROCESS -> ProcessMcpClient(config)
            }

            val result = client.initialize()
            if (result.isSuccess) {
                clients[config.id] = client
                Napier.i("External MCP client initialized: ${config.name}")
                refreshTools()
            } else {
                Napier.e("Failed to initialize ${config.name}", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Napier.e("Error initializing external server ${config.name}", e)
        }
    }

    /**
     * Remove external MCP server
     */
    suspend fun removeExternalServer(serverId: String) {
        externalServers.removeAll { it.id == serverId }
        saveServersToStorage()
        clients[serverId]?.close()
        clients.remove(serverId)
        refreshTools()
    }

    /**
     * Update external MCP server (e.g., toggle enabled state)
     */
    suspend fun updateExternalServer(serverId: String, enabled: Boolean) {
        val serverIndex = externalServers.indexOfFirst { it.id == serverId }
        if (serverIndex != -1) {
            val updatedServer = externalServers[serverIndex].copy(enabled = enabled)
            externalServers[serverIndex] = updatedServer
            saveServersToStorage()

            // If disabling, close the client
            if (!enabled) {
                clients[serverId]?.close()
                clients.remove(serverId)
                refreshTools()
            } else {
                // If enabling, initialize the server
                try {
                    val client = when (updatedServer.type) {
                        McpServerType.HTTP -> {
                            if (httpClient == null) {
                                Napier.e("HTTP client not available for HTTP MCP server")
                                return
                            }
                            HttpMcpClient(updatedServer, httpClient)
                        }
                        McpServerType.PROCESS -> ProcessMcpClient(updatedServer)
                    }

                    val result = client.initialize()
                    if (result.isSuccess) {
                        clients[updatedServer.id] = client
                        Napier.i("External MCP client initialized: ${updatedServer.name}")
                        refreshTools()
                    } else {
                        Napier.e("Failed to initialize ${updatedServer.name}", result.exceptionOrNull())
                    }
                } catch (e: Exception) {
                    Napier.e("Error initializing external server ${updatedServer.name}", e)
                }
            }
        }
    }

    /**
     * Get list of configured external servers
     */
    fun getExternalServers(): List<McpServerConfig> = externalServers.toList()

    /**
     * Initialize external MCP servers
     */
    private suspend fun initializeExternalServers() {
        if (httpClient == null) {
            Napier.w("HTTP client not provided, external servers will not be initialized")
            return
        }

        externalServers.forEach { config ->
            if (!config.enabled) {
                Napier.i("Skipping disabled server: ${config.name}")
                return@forEach
            }

            try {
                val client = when (config.type) {
                    McpServerType.HTTP -> HttpMcpClient(config, httpClient)
                    McpServerType.PROCESS -> ProcessMcpClient(config)
                }

                val result = client.initialize()
                if (result.isSuccess) {
                    clients[config.id] = client
                    Napier.i("External MCP client initialized: ${config.name}")
                } else {
                    Napier.e("Failed to initialize ${config.name}", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                Napier.e("Error initializing external server ${config.name}", e)
            }
        }
    }

    /**
     * Refresh the list of available tools from all clients
     */
    suspend fun refreshTools() {
        val allTools = mutableListOf<McpTool>()

        Napier.d("Refreshing tools from ${clients.size} clients")
        clients.forEach { (id, client) ->
            Napier.d("Listing tools from client: $id")
            val result = client.listTools()
            if (result.isSuccess) {
                val tools = result.getOrNull() ?: emptyList()
                Napier.d("Client $id provided ${tools.size} tools")
                tools.forEach { tool ->
                    Napier.d("  - ${tool.name}: ${tool.description}")
                }
                allTools.addAll(tools)
            } else {
                Napier.e("Failed to list tools from client $id", result.exceptionOrNull())
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
        for ((id, client) in clients) {
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
    fun isInitialized(): Boolean = clients.any { it.value.isInitialized }

    /**
     * Close all clients
     */
    suspend fun close() {
        clients.forEach { (_, client) -> client.close() }
        clients.clear()
        _availableTools = emptyList()
    }
}