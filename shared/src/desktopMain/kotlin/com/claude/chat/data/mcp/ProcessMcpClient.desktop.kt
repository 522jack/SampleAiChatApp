package com.claude.chat.data.mcp

import com.claude.chat.data.model.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Desktop/JVM implementation of ProcessMcpClient
 * Launches MCP server as a subprocess and communicates via STDIO
 */
actual class ProcessMcpClient actual constructor(
    private val config: McpServerConfig
) : McpClient {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var readerJob: Job? = null
    private val responseChannel = Channel<String>(Channel.UNLIMITED)

    private var _initialized = false
    private var _serverInfo: McpServerInfo? = null
    private var requestIdCounter = 0

    private val processConfig: McpConnectionConfig.ProcessConfig
        get() = config.config as McpConnectionConfig.ProcessConfig

    actual override val isInitialized: Boolean
        get() = _initialized

    actual override val serverInfo: McpServerInfo?
        get() = _serverInfo

    actual override suspend fun initialize(): Result<McpInitializeResult> {
        return try {
            // Start process
            startProcess()

            // Send initialize request
            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", nextRequestId())
                put("method", "initialize")
                put("params", buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", buildJsonObject {})
                    put("clientInfo", buildJsonObject {
                        put("name", "claude-chat-app")
                        put("version", "1.0.0")
                    })
                })
            }

            val response = sendRequest(request)
            val result = Json.decodeFromJsonElement<McpInitializeResult>(
                response["result"] ?: JsonObject(emptyMap())
            )

            _serverInfo = result.serverInfo
            _initialized = true

            Result.success(result)
        } catch (e: Exception) {
            Napier.e("Failed to initialize process MCP client for ${config.name}", e)
            Result.failure(e)
        }
    }

    private suspend fun startProcess() = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder().apply {
            command(listOf(processConfig.command) + processConfig.args)

            // Set environment variables
            val env = environment()
            processConfig.env.forEach { (key, value) ->
                env[key] = value
            }

            // Set working directory if specified
            processConfig.workingDir?.let { workingDir ->
                directory(java.io.File(workingDir))
            }
        }

        process = processBuilder.start()

        val proc = process ?: throw IllegalStateException("Failed to start process")

        writer = BufferedWriter(OutputStreamWriter(proc.outputStream))
        reader = BufferedReader(InputStreamReader(proc.inputStream))

        // Start reader coroutine
        readerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isActive) {
                    val line = reader?.readLine() ?: break
                    if (line.isNotBlank()) {
                        Napier.d("Received from process: $line")
                        responseChannel.send(line)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Napier.e("Error reading from process", e)
                }
            }
        }

        // Give the process time to start
        delay(500)
    }

    private suspend fun sendRequest(request: JsonObject): JsonObject {
        val requestText = request.toString()
        Napier.d("Sending to process: $requestText")

        withContext(Dispatchers.IO) {
            writer?.write(requestText)
            writer?.newLine()
            writer?.flush()
        }

        // Wait for response
        return withTimeout(30000) {
            val responseText = responseChannel.receive()
            Json.parseToJsonElement(responseText).jsonObject
        }
    }

    private fun nextRequestId(): Int = ++requestIdCounter

    actual override suspend fun listTools(): Result<List<McpTool>> {
        if (!_initialized) {
            return Result.failure(IllegalStateException("Client not initialized"))
        }

        return try {
            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", nextRequestId())
                put("method", "tools/list")
            }

            val response = sendRequest(request)
            val result = response["result"]?.jsonObject ?: JsonObject(emptyMap())
            val tools = result["tools"]?.jsonArray ?: JsonArray(emptyList())

            val mcpTools = tools.map { toolElement ->
                val tool = toolElement.jsonObject
                McpTool(
                    name = tool["name"]?.jsonPrimitive?.content ?: "",
                    description = tool["description"]?.jsonPrimitive?.content ?: "",
                    inputSchema = tool["inputSchema"]?.jsonObject ?: JsonObject(emptyMap())
                )
            }

            Result.success(mcpTools)
        } catch (e: Exception) {
            Napier.e("Failed to list tools from ${config.name}", e)
            Result.failure(e)
        }
    }

    actual override suspend fun callTool(name: String, arguments: Map<String, Any>): Result<McpToolCallResult> {
        if (!_initialized) {
            return Result.failure(IllegalStateException("Client not initialized"))
        }

        return try {
            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", nextRequestId())
                put("method", "tools/call")
                put("params", buildJsonObject {
                    put("name", name)
                    put("arguments", Json.encodeToJsonElement(arguments))
                })
            }

            val response = sendRequest(request)
            val result = Json.decodeFromJsonElement<McpToolCallResult>(
                response["result"] ?: JsonObject(emptyMap())
            )

            Result.success(result)
        } catch (e: Exception) {
            Napier.e("Failed to call tool $name on ${config.name}", e)
            Result.failure(e)
        }
    }

    actual override suspend fun listResources(): Result<List<McpResource>> {
        if (!_initialized) {
            return Result.failure(IllegalStateException("Client not initialized"))
        }

        return try {
            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", nextRequestId())
                put("method", "resources/list")
            }

            val response = sendRequest(request)
            val result = response["result"]?.jsonObject ?: JsonObject(emptyMap())
            val resources = result["resources"]?.jsonArray ?: JsonArray(emptyList())

            val mcpResources = resources.map { resourceElement ->
                Json.decodeFromJsonElement<McpResource>(resourceElement)
            }

            Result.success(mcpResources)
        } catch (e: Exception) {
            Napier.e("Failed to list resources from ${config.name}", e)
            Result.failure(e)
        }
    }

    actual override suspend fun listPrompts(): Result<List<McpPrompt>> {
        if (!_initialized) {
            return Result.failure(IllegalStateException("Client not initialized"))
        }

        return try {
            val request = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", nextRequestId())
                put("method", "prompts/list")
            }

            val response = sendRequest(request)
            val result = response["result"]?.jsonObject ?: JsonObject(emptyMap())
            val prompts = result["prompts"]?.jsonArray ?: JsonArray(emptyList())

            val mcpPrompts = prompts.map { promptElement ->
                Json.decodeFromJsonElement<McpPrompt>(promptElement)
            }

            Result.success(mcpPrompts)
        } catch (e: Exception) {
            Napier.e("Failed to list prompts from ${config.name}", e)
            Result.failure(e)
        }
    }

    actual override suspend fun close() {
        readerJob?.cancel()
        readerJob = null

        withContext(Dispatchers.IO) {
            writer?.close()
            reader?.close()
        }

        process?.destroy()
        process?.waitFor()
        process = null

        _initialized = false
        _serverInfo = null
    }
}