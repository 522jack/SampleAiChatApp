package com.claude.chat.data.mcp

import com.claude.chat.data.model.*
import kotlinx.serialization.json.*
import io.github.aakira.napier.Napier

/**
 * Simple MCP client implementation with built-in tools
 * This is a demonstration implementation that provides basic tools
 */
class SimpleMcpClient : McpClient {
    private var _initialized = false
    private var _serverInfo: McpServerInfo? = null

    override val isInitialized: Boolean
        get() = _initialized

    override val serverInfo: McpServerInfo?
        get() = _serverInfo

    private val builtInTools = listOf(
        McpTool(
            name = "get_current_time",
            description = "Get the current date and time",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
            }
        ),
        McpTool(
            name = "calculate",
            description = "Perform basic arithmetic calculations",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("expression", buildJsonObject {
                        put("type", "string")
                        put("description", "Mathematical expression to evaluate (e.g., '2 + 2', '10 * 5')")
                    })
                })
                put("required", buildJsonArray {
                    add("expression")
                })
            }
        ),
        McpTool(
            name = "format_json",
            description = "Format and pretty-print JSON data",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("json", buildJsonObject {
                        put("type", "string")
                        put("description", "JSON string to format")
                    })
                })
                put("required", buildJsonArray {
                    add("json")
                })
            }
        ),
        McpTool(
            name = "count_words",
            description = "Count words in a text string",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "Text to count words in")
                    })
                })
                put("required", buildJsonArray {
                    add("text")
                })
            }
        ),
        McpTool(
            name = "reverse_string",
            description = "Reverse a text string",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "Text to reverse")
                    })
                })
                put("required", buildJsonArray {
                    add("text")
                })
            }
        )
    )

    override suspend fun initialize(): Result<McpInitializeResult> {
        return try {
            _serverInfo = McpServerInfo(
                name = "SimpleMCP",
                version = "1.0.0"
            )
            _initialized = true

            val result = McpInitializeResult(
                protocolVersion = "2024-11-05",
                capabilities = McpServerCapabilities(
                    tools = McpToolsCapability(listChanged = false),
                    resources = McpResourcesCapability(subscribe = false, listChanged = false),
                    prompts = McpPromptsCapability(listChanged = false)
                ),
                serverInfo = _serverInfo!!
            )
            Result.success(result)
        } catch (e: Exception) {
            Napier.e("Failed to initialize MCP client", e)
            Result.failure(e)
        }
    }

    override suspend fun listTools(): Result<List<McpTool>> {
        return if (_initialized) {
            Result.success(builtInTools)
        } else {
            Result.failure(IllegalStateException("MCP client not initialized"))
        }
    }

    override suspend fun callTool(name: String, arguments: Map<String, Any>): Result<McpToolCallResult> {
        if (!_initialized) {
            return Result.failure(IllegalStateException("MCP client not initialized"))
        }

        return try {
            val result = when (name) {
                "get_current_time" -> executeGetCurrentTime()
                "calculate" -> executeCalculate(arguments["expression"] as? String ?: "")
                "format_json" -> executeFormatJson(arguments["json"] as? String ?: "")
                "count_words" -> executeCountWords(arguments["text"] as? String ?: "")
                "reverse_string" -> executeReverseString(arguments["text"] as? String ?: "")
                else -> McpToolCallResult(
                    content = listOf(McpContent(type = "text", text = "Unknown tool: $name")),
                    isError = true
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Napier.e("Failed to call tool $name", e)
            Result.success(
                McpToolCallResult(
                    content = listOf(McpContent(type = "text", text = "Error: ${e.message}")),
                    isError = true
                )
            )
        }
    }

    private fun executeGetCurrentTime(): McpToolCallResult {
        val now = kotlinx.datetime.Clock.System.now()
        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = "Current time: $now"
                )
            ),
            isError = false
        )
    }

    private fun executeCalculate(expression: String): McpToolCallResult {
        return try {
            // Simple calculator - only handles basic operations
            val result = evaluateExpression(expression)
            McpToolCallResult(
                content = listOf(
                    McpContent(
                        type = "text",
                        text = "$expression = $result"
                    )
                ),
                isError = false
            )
        } catch (e: Exception) {
            McpToolCallResult(
                content = listOf(
                    McpContent(
                        type = "text",
                        text = "Failed to calculate: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    private fun evaluateExpression(expr: String): Double {
        // Very simple expression evaluator - supports +, -, *, /
        val cleaned = expr.replace(" ", "")

        // Handle multiplication and division first
        var result = cleaned
        var regex = """(\d+\.?\d*)\s*([*/])\s*(\d+\.?\d*)""".toRegex()
        while (regex.containsMatchIn(result)) {
            result = regex.replace(result) { matchResult ->
                val a = matchResult.groupValues[1].toDouble()
                val op = matchResult.groupValues[2]
                val b = matchResult.groupValues[3].toDouble()
                when (op) {
                    "*" -> (a * b).toString()
                    "/" -> (a / b).toString()
                    else -> matchResult.value
                }
            }
        }

        // Handle addition and subtraction
        regex = """(\d+\.?\d*)\s*([+\-])\s*(\d+\.?\d*)""".toRegex()
        while (regex.containsMatchIn(result)) {
            result = regex.replace(result) { matchResult ->
                val a = matchResult.groupValues[1].toDouble()
                val op = matchResult.groupValues[2]
                val b = matchResult.groupValues[3].toDouble()
                when (op) {
                    "+" -> (a + b).toString()
                    "-" -> (a - b).toString()
                    else -> matchResult.value
                }
            }
        }

        return result.toDouble()
    }

    private fun executeFormatJson(json: String): McpToolCallResult {
        return try {
            val jsonElement = Json.parseToJsonElement(json)
            val formatted = Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), jsonElement)
            McpToolCallResult(
                content = listOf(
                    McpContent(
                        type = "text",
                        text = "Formatted JSON:\n$formatted"
                    )
                ),
                isError = false
            )
        } catch (e: Exception) {
            McpToolCallResult(
                content = listOf(
                    McpContent(
                        type = "text",
                        text = "Invalid JSON: ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }

    private fun executeCountWords(text: String): McpToolCallResult {
        val wordCount = text.trim().split("""\s+""".toRegex()).filter { it.isNotEmpty() }.size
        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = "Word count: $wordCount"
                )
            ),
            isError = false
        )
    }

    private fun executeReverseString(text: String): McpToolCallResult {
        val reversed = text.reversed()
        return McpToolCallResult(
            content = listOf(
                McpContent(
                    type = "text",
                    text = "Reversed: $reversed"
                )
            ),
            isError = false
        )
    }

    override suspend fun listResources(): Result<List<McpResource>> {
        return if (_initialized) {
            Result.success(emptyList())
        } else {
            Result.failure(IllegalStateException("MCP client not initialized"))
        }
    }

    override suspend fun listPrompts(): Result<List<McpPrompt>> {
        return if (_initialized) {
            Result.success(emptyList())
        } else {
            Result.failure(IllegalStateException("MCP client not initialized"))
        }
    }

    override suspend fun close() {
        _initialized = false
        _serverInfo = null
    }
}