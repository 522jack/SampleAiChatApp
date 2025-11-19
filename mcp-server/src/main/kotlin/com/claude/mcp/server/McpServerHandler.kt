package com.claude.mcp.server

import com.claude.mcp.server.protocol.*
import com.claude.mcp.server.services.WeatherService
import com.claude.mcp.server.services.ReminderService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

/**
 * MCP Server Handler
 * Handles all MCP protocol messages and routes them to appropriate services
 */
class McpServerHandler(
    private val weatherService: WeatherService,
    private val reminderService: ReminderService
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var initialized = false

    suspend fun handleRequest(requestText: String): String {
        logger.debug { "Received request: $requestText" }

        return try {
            val request = json.decodeFromString<JsonRpcRequest>(requestText)
            val response = processRequest(request)
            json.encodeToString(response)
        } catch (e: Exception) {
            logger.error(e) { "Error processing request" }
            val errorResponse = JsonRpcResponse(
                error = JsonRpcError(
                    code = ErrorCodes.PARSE_ERROR,
                    message = "Parse error: ${e.message}"
                )
            )
            json.encodeToString(errorResponse)
        }
    }

    private suspend fun processRequest(request: JsonRpcRequest): JsonRpcResponse {
        logger.info { "Processing method: ${request.method}" }

        return when (request.method) {
            McpMethods.INITIALIZE -> handleInitialize(request)
            McpMethods.TOOLS_LIST -> handleToolsList(request)
            McpMethods.TOOLS_CALL -> handleToolsCall(request)
            McpMethods.RESOURCES_LIST -> handleResourcesList(request)
            McpMethods.PROMPTS_LIST -> handlePromptsList(request)
            McpMethods.PING -> handlePing(request)
            else -> JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = ErrorCodes.METHOD_NOT_FOUND,
                    message = "Method not found: ${request.method}"
                )
            )
        }
    }

    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        logger.info { "Initializing MCP server" }

        initialized = true

        val result = InitializeResult(
            protocolVersion = "2024-11-05",
            capabilities = ServerCapabilities(
                tools = ToolsCapability(listChanged = false),
                resources = ResourcesCapability(subscribe = false, listChanged = false),
                prompts = PromptsCapability(listChanged = false)
            ),
            serverInfo = Implementation(
                name = "weather-mcp-server",
                version = "1.0.0"
            ),
            instructions = "MCP Server with Weather and Task Reminder features:\n" +
                "- Weather: Get current weather and forecasts for cities worldwide using OpenWeather API\n" +
                "- Reminders: Manage tasks with automatic periodic summaries every 60 seconds showing active tasks and completed tasks from today"
        )

        return JsonRpcResponse(
            id = request.id,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        if (!initialized) {
            return createNotInitializedError(request.id)
        }

        val tools = listOf(
            Tool(
                name = "get_current_weather",
                description = "Get current weather for a specific city. Returns temperature, humidity, pressure, and weather conditions.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("city", buildJsonObject {
                            put("type", "string")
                            put("description", "City name (e.g., 'London', 'New York', 'Moscow')")
                        })
                        put("units", buildJsonObject {
                            put("type", "string")
                            put("description", "Temperature units: 'metric' (Celsius), 'imperial' (Fahrenheit), or 'standard' (Kelvin)")
                            put("enum", buildJsonArray {
                                add("metric")
                                add("imperial")
                                add("standard")
                            })
                            put("default", "metric")
                        })
                    })
                    put("required", buildJsonArray {
                        add("city")
                    })
                }
            ),
            Tool(
                name = "get_weather_forecast",
                description = "Get 5-day weather forecast for a specific city. Returns forecast data in 3-hour intervals.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("city", buildJsonObject {
                            put("type", "string")
                            put("description", "City name (e.g., 'London', 'New York', 'Moscow')")
                        })
                        put("units", buildJsonObject {
                            put("type", "string")
                            put("description", "Temperature units: 'metric' (Celsius), 'imperial' (Fahrenheit), or 'standard' (Kelvin)")
                            put("enum", buildJsonArray {
                                add("metric")
                                add("imperial")
                                add("standard")
                            })
                            put("default", "metric")
                        })
                        put("days", buildJsonObject {
                            put("type", "number")
                            put("description", "Number of days to forecast (1-5)")
                            put("minimum", 1)
                            put("maximum", 5)
                            put("default", 3)
                        })
                    })
                    put("required", buildJsonArray {
                        add("city")
                    })
                }
            ),
            Tool(
                name = "add_task",
                description = "Add a new task to the reminder system. Tasks will appear in periodic summary notifications.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("title", buildJsonObject {
                            put("type", "string")
                            put("description", "Task title (required)")
                        })
                        put("description", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional task description with additional details")
                        })
                    })
                    put("required", buildJsonArray {
                        add("title")
                    })
                }
            ),
            Tool(
                name = "complete_task",
                description = "Mark a task as completed. Completed tasks will appear in the 'Completed Today' section of summaries.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "number")
                            put("description", "Task ID to mark as complete")
                        })
                    })
                    put("required", buildJsonArray {
                        add("id")
                    })
                }
            ),
            Tool(
                name = "list_tasks",
                description = "Get a list of all tasks. You can optionally filter to show only active (incomplete) tasks.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("include_completed", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Include completed tasks in the list (default: true)")
                            put("default", true)
                        })
                    })
                }
            ),
            Tool(
                name = "get_task_summary",
                description = "Get the current task summary report showing active tasks and tasks completed today. This is the same report that is sent periodically as notifications.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {})
                }
            ),
            Tool(
                name = "delete_task",
                description = "Permanently delete a task from the system.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "number")
                            put("description", "Task ID to delete")
                        })
                    })
                    put("required", buildJsonArray {
                        add("id")
                    })
                }
            )
        )

        val result = ToolsListResult(tools)

        return JsonRpcResponse(
            id = request.id,
            result = json.encodeToJsonElement(result)
        )
    }

    private suspend fun handleToolsCall(request: JsonRpcRequest): JsonRpcResponse {
        if (!initialized) {
            return createNotInitializedError(request.id)
        }

        val params = request.params ?: return JsonRpcResponse(
            id = request.id,
            error = JsonRpcError(
                code = ErrorCodes.INVALID_PARAMS,
                message = "Missing params"
            )
        )

        val callRequest = try {
            json.decodeFromJsonElement<CallToolRequest>(params)
        } catch (e: Exception) {
            return JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = ErrorCodes.INVALID_PARAMS,
                    message = "Invalid params: ${e.message}"
                )
            )
        }

        val result = when (callRequest.name) {
            "get_current_weather" -> executeGetCurrentWeather(callRequest.arguments)
            "get_weather_forecast" -> executeGetWeatherForecast(callRequest.arguments)
            "add_task" -> executeAddTask(callRequest.arguments)
            "complete_task" -> executeCompleteTask(callRequest.arguments)
            "list_tasks" -> executeListTasks(callRequest.arguments)
            "get_task_summary" -> executeGetTaskSummary()
            "delete_task" -> executeDeleteTask(callRequest.arguments)
            else -> CallToolResult(
                content = listOf(ToolContent(type = "text", text = "Unknown tool: ${callRequest.name}")),
                isError = true
            )
        }

        return JsonRpcResponse(
            id = request.id,
            result = json.encodeToJsonElement(result)
        )
    }

    private suspend fun executeGetCurrentWeather(arguments: JsonObject?): CallToolResult {
        logger.info { "executeGetCurrentWeather called with arguments: $arguments" }
        logger.info { "Arguments keys: ${arguments?.keys}" }
        arguments?.forEach { (key, value) ->
            logger.info { "  Argument: $key = $value (type: ${value::class.simpleName})" }
        }

        val city = arguments?.get("city")?.jsonPrimitive?.content
            ?: return CallToolResult(
                content = listOf(ToolContent(type = "text", text = "City parameter is required")),
                isError = true
            )

        val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"

        val weatherResult = weatherService.getCurrentWeather(city, units)

        return if (weatherResult.isSuccess) {
            CallToolResult(
                content = listOf(ToolContent(type = "text", text = weatherResult.getOrNull()!!)),
                isError = false
            )
        } else {
            CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error: ${weatherResult.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }
    }

    private suspend fun executeGetWeatherForecast(arguments: JsonObject?): CallToolResult {
        val city = arguments?.get("city")?.jsonPrimitive?.content
            ?: return CallToolResult(
                content = listOf(ToolContent(type = "text", text = "City parameter is required")),
                isError = true
            )

        val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"
        val days = arguments["days"]?.jsonPrimitive?.intOrNull ?: 3

        val forecastResult = weatherService.getWeatherForecast(city, units, days)

        return if (forecastResult.isSuccess) {
            CallToolResult(
                content = listOf(ToolContent(type = "text", text = forecastResult.getOrNull()!!)),
                isError = false
            )
        } else {
            CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error: ${forecastResult.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }
    }

    private fun handleResourcesList(request: JsonRpcRequest): JsonRpcResponse {
        if (!initialized) {
            return createNotInitializedError(request.id)
        }

        val result = ResourcesListResult(resources = emptyList())

        return JsonRpcResponse(
            id = request.id,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun handlePromptsList(request: JsonRpcRequest): JsonRpcResponse {
        if (!initialized) {
            return createNotInitializedError(request.id)
        }

        val result = PromptsListResult(prompts = emptyList())

        return JsonRpcResponse(
            id = request.id,
            result = json.encodeToJsonElement(result)
        )
    }

    private fun handlePing(request: JsonRpcRequest): JsonRpcResponse {
        return JsonRpcResponse(
            id = request.id,
            result = JsonObject(emptyMap())
        )
    }

    private fun createNotInitializedError(id: JsonElement?): JsonRpcResponse {
        return JsonRpcResponse(
            id = id,
            error = JsonRpcError(
                code = ErrorCodes.INTERNAL_ERROR,
                message = "Server not initialized. Call initialize first."
            )
        )
    }

    private suspend fun executeAddTask(arguments: JsonObject?): CallToolResult {
        val title = arguments?.get("title")?.jsonPrimitive?.content
            ?: return CallToolResult(
                content = listOf(ToolContent(type = "text", text = "Title parameter is required")),
                isError = true
            )

        val description = arguments["description"]?.jsonPrimitive?.content

        val result = reminderService.addTask(title, description)

        return if (result.isSuccess) {
            val task = result.getOrNull()!!
            val message = buildString {
                appendLine("✅ Task added successfully!")
                appendLine()
                appendLine("📌 Task Details:")
                appendLine("   ID: ${task.id}")
                appendLine("   Title: ${task.title}")
                if (task.description != null) {
                    appendLine("   Description: ${task.description}")
                }
                appendLine("   Created: ${task.createdAt}")
                appendLine()
                appendLine("The task will appear in periodic summaries every ${reminderService.notifications.value?.let { "60 seconds" } ?: "minute"}.")
            }
            CallToolResult(
                content = listOf(ToolContent(type = "text", text = message)),
                isError = false
            )
        } else {
            CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error adding task: ${result.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }
    }

    private suspend fun executeCompleteTask(arguments: JsonObject?): CallToolResult {
        val id = arguments?.get("id")?.jsonPrimitive?.longOrNull
            ?: return CallToolResult(
                content = listOf(ToolContent(type = "text", text = "Task ID parameter is required")),
                isError = true
            )

        val result = reminderService.completeTask(id)

        return if (result.isSuccess) {
            val task = result.getOrNull()!!
            val message = buildString {
                appendLine("✅ Task completed successfully!")
                appendLine()
                appendLine("📋 Task: ${task.title}")
                appendLine("   Completed at: ${task.completedAt}")
                appendLine()
                appendLine("This task will now appear in the 'Completed Today' section of summaries.")
            }
            CallToolResult(
                content = listOf(ToolContent(type = "text", text = message)),
                isError = false
            )
        } else {
            CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error completing task: ${result.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }
    }

    private suspend fun executeListTasks(arguments: JsonObject?): CallToolResult {
        val includeCompleted = arguments?.get("include_completed")?.jsonPrimitive?.booleanOrNull ?: true

        val result = reminderService.getTasks(includeCompleted)

        return if (result.isSuccess) {
            val tasks = result.getOrNull()!!
            val message = buildString {
                appendLine("📋 Task List")
                appendLine("━━━━━━━━━━━━━━━━━━━━")
                appendLine()

                if (tasks.isEmpty()) {
                    appendLine("No tasks found.")
                } else {
                    val activeTasks = tasks.filter { !it.isCompleted }
                    val completedTasks = tasks.filter { it.isCompleted }

                    if (activeTasks.isNotEmpty()) {
                        appendLine("📌 Active Tasks (${activeTasks.size}):")
                        activeTasks.forEach { task ->
                            appendLine("   [${task.id}] ${task.title}")
                            task.description?.let { desc ->
                                appendLine("       📝 $desc")
                            }
                            appendLine("       🕐 Created: ${task.createdAt}")
                        }
                        appendLine()
                    }

                    if (includeCompleted && completedTasks.isNotEmpty()) {
                        appendLine("✅ Completed Tasks (${completedTasks.size}):")
                        completedTasks.forEach { task ->
                            appendLine("   [${task.id}] ${task.title}")
                            task.completedAt?.let { completed ->
                                appendLine("       ✓ Completed: $completed")
                            }
                        }
                    }
                }

                appendLine()
                appendLine("Total tasks: ${tasks.size}")
            }
            CallToolResult(
                content = listOf(ToolContent(type = "text", text = message)),
                isError = false
            )
        } else {
            CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error listing tasks: ${result.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }
    }

    private suspend fun executeGetTaskSummary(): CallToolResult {
        val notification = reminderService.getLatestNotification()
            ?: "No summary available yet. The first summary will be generated in approximately 60 seconds."

        return CallToolResult(
            content = listOf(ToolContent(type = "text", text = notification)),
            isError = false
        )
    }

    private suspend fun executeDeleteTask(arguments: JsonObject?): CallToolResult {
        val id = arguments?.get("id")?.jsonPrimitive?.longOrNull
            ?: return CallToolResult(
                content = listOf(ToolContent(type = "text", text = "Task ID parameter is required")),
                isError = true
            )

        val result = reminderService.deleteTask(id)

        return if (result.isSuccess) {
            val message = "✅ Task #$id deleted successfully."
            CallToolResult(
                content = listOf(ToolContent(type = "text", text = message)),
                isError = false
            )
        } else {
            CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error deleting task: ${result.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }
    }
}