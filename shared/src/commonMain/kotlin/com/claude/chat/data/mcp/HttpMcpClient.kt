package com.claude.chat.data.mcp

import com.claude.chat.data.model.*
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * MCP Client for HTTP/SSE connections
 * Connects to remote MCP servers over HTTP
 */
class HttpMcpClient(
    private val config: McpServerConfig,
    private val httpClient: HttpClient
) : McpClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override var isInitialized: Boolean = false
        private set

    override var serverInfo: McpServerInfo? = null
        private set

    private val baseUrl: String
        get() = when (val cfg = config.config) {
            is McpConnectionConfig.HttpConfig -> {
                // Remove trailing slash if present
                cfg.url.trimEnd('/')
            }
            else -> throw IllegalStateException("Invalid config for HTTP client")
        }

    override suspend fun initialize(): Result<McpInitializeResult> {
        return try {
            Napier.d("Initializing HTTP MCP client: ${config.name}")
            Napier.d("Base URL from config: $baseUrl")
            Napier.d("Full URL will be: $baseUrl/mcp")

            val params = buildJsonObject {
                put("protocolVersion", "2024-11-05")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "Claude Chat KMP")
                    put("version", "1.0.0")
                }
            }

            val request = JsonRpcRequest(
                jsonrpc = "2.0",
                id = "1",
                method = "initialize",
                params = params
            )

            val response: HttpResponse = httpClient.post("$baseUrl/mcp") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(JsonRpcRequest.serializer(), request))
            }

            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                Napier.d("Initialize response: $body")

                val jsonResponse = json.decodeFromString<JsonRpcResponse>(body)

                if (jsonResponse.error != null) {
                    Napier.e("MCP initialization error: ${jsonResponse.error}")
                    return Result.failure(Exception("MCP Error: ${jsonResponse.error.message}"))
                }

                val info = McpServerInfo(
                    name = config.name,
                    version = "1.0.0"
                )
                serverInfo = info

                isInitialized = true
                Napier.i("HTTP MCP client initialized successfully: ${info.name}")

                Result.success(McpInitializeResult(
                    protocolVersion = "2024-11-05",
                    capabilities = McpServerCapabilities(),
                    serverInfo = info
                ))
            } else {
                val error = "HTTP error: ${response.status}"
                Napier.e(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Napier.e("Failed to initialize HTTP MCP client", e)
            Result.failure(e)
        }
    }

    override suspend fun listTools(): Result<List<McpTool>> {
        if (!isInitialized) {
            return Result.failure(Exception("Client not initialized"))
        }

        return try {
            val request = JsonRpcRequest(
                jsonrpc = "2.0",
                id = "2",
                method = "tools/list",
                params = null
            )

            val response: HttpResponse = httpClient.post("$baseUrl/mcp") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(JsonRpcRequest.serializer(), request))
            }

            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val jsonResponse = json.decodeFromString<JsonRpcResponse>(body)

                if (jsonResponse.error != null) {
                    return Result.failure(Exception("MCP Error: ${jsonResponse.error.message}"))
                }

                val result = jsonResponse.result ?: return Result.success(emptyList())
                val toolsListResponse = json.decodeFromJsonElement<McpToolsListResponse>(result)
                val tools = toolsListResponse.tools

                Napier.d("Listed ${tools.size} tools from HTTP MCP server")
                Result.success(tools)
            } else {
                Result.failure(Exception("HTTP error: ${response.status}"))
            }
        } catch (e: Exception) {
            Napier.e("Error listing tools", e)
            Result.failure(e)
        }
    }

    override suspend fun callTool(name: String, arguments: Map<String, Any>): Result<McpToolCallResult> {
        if (!isInitialized) {
            return Result.failure(Exception("Client not initialized"))
        }

        return try {
            Napier.d("Calling HTTP MCP tool: $name with args: $arguments")

            val paramsObj = buildJsonObject {
                put("name", name)
                if (arguments.isNotEmpty()) {
                    putJsonObject("arguments") {
                        arguments.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key, value)
                                is Number -> put(key, JsonPrimitive(value))
                                is Boolean -> put(key, value)
                                else -> put(key, value.toString())
                            }
                        }
                    }
                }
            }

            val request = JsonRpcRequest(
                jsonrpc = "2.0",
                id = "3",
                method = "tools/call",
                params = paramsObj
            )

            val requestBody = Json.encodeToString(JsonRpcRequest.serializer(), request)
            Napier.d("Sending JSON-RPC request: $requestBody")

            val response: HttpResponse = httpClient.post("$baseUrl/mcp") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                Napier.d("Tool call response: $body")

                val jsonResponse = json.decodeFromString<JsonRpcResponse>(body)

                if (jsonResponse.error != null) {
                    return Result.failure(Exception("MCP Error: ${jsonResponse.error.message}"))
                }

                val result = jsonResponse.result ?: throw Exception("No result in response")
                val toolResult = json.decodeFromJsonElement<McpToolCallResult>(result)

                Napier.d("Tool call successful")
                Result.success(toolResult)
            } else {
                Result.failure(Exception("HTTP error: ${response.status}"))
            }
        } catch (e: Exception) {
            Napier.e("Error calling tool", e)
            Result.failure(e)
        }
    }

    override suspend fun listResources(): Result<List<McpResource>> {
        return Result.success(emptyList())
    }

    override suspend fun listPrompts(): Result<List<McpPrompt>> {
        return Result.success(emptyList())
    }

    override suspend fun close() {
        isInitialized = false
        serverInfo = null
    }
}