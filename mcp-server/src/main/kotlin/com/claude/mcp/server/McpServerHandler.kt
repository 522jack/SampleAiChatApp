package com.claude.mcp.server

import com.claude.mcp.server.protocol.*
import com.claude.mcp.server.services.WeatherService
import com.claude.mcp.server.services.ReminderService
import com.claude.mcp.server.services.DocumentSearchService
import com.claude.mcp.server.services.DocumentSummarizationService
import com.claude.mcp.server.services.DocumentStorageService
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
    private val reminderService: ReminderService,
    private val documentSearchService: DocumentSearchService,
    private val documentSummarizationService: DocumentSummarizationService,
    private val documentStorageService: DocumentStorageService
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
            instructions = "MCP Server with Weather, Task Reminder, and Document Processing features:\n" +
                "- Weather: Get current weather and forecasts for cities worldwide using OpenWeather API\n" +
                "- Reminders: Manage tasks with automatic periodic summaries every 60 seconds showing active tasks and completed tasks from today\n" +
                "- Documents: Search for documents in project folders, generate summaries with key information and statistics, and save summaries to files"
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
            ),
            Tool(
                name = "search_documents",
                description = "Search for documents in the project folder. Supports file pattern matching and recursive search.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("folder_path", buildJsonObject {
                            put("type", "string")
                            put("description", "Relative or absolute path to search in (default: project root '.')")
                            put("default", ".")
                        })
                        put("pattern", buildJsonObject {
                            put("type", "string")
                            put("description", "File name pattern to match (supports wildcards like *.txt, *.md)")
                            put("default", "*")
                        })
                        put("recursive", buildJsonObject {
                            put("type", "boolean")
                            put("description", "Whether to search recursively in subdirectories")
                            put("default", true)
                        })
                        put("max_depth", buildJsonObject {
                            put("type", "number")
                            put("description", "Maximum depth for recursive search")
                            put("default", 5)
                        })
                    })
                }
            ),
            Tool(
                name = "summarize_document",
                description = "Summarize a document. Extracts key information, statistics, and main points from the document.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("document_path", buildJsonObject {
                            put("type", "string")
                            put("description", "Path to the document to summarize (can be from search_documents result)")
                        })
                        put("max_preview_length", buildJsonObject {
                            put("type", "number")
                            put("description", "Maximum length of preview text in characters")
                            put("default", 500)
                        })
                    })
                    put("required", buildJsonArray {
                        add("document_path")
                    })
                }
            ),
            Tool(
                name = "save_summary",
                description = "Save a document summary to a file. Creates a markdown file with the summary in the specified folder.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("summary_content", buildJsonObject {
                            put("type", "string")
                            put("description", "Summary content to save (can be from summarize_document result)")
                        })
                        put("original_document_path", buildJsonObject {
                            put("type", "string")
                            put("description", "Path to the original document that was summarized")
                        })
                        put("output_folder", buildJsonObject {
                            put("type", "string")
                            put("description", "Folder where to save the summary (relative to project root)")
                            put("default", "summaries")
                        })
                        put("filename", buildJsonObject {
                            put("type", "string")
                            put("description", "Custom filename for the summary (optional, will be auto-generated if not provided)")
                        })
                    })
                    put("required", buildJsonArray {
                        add("summary_content")
                        add("original_document_path")
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
            "search_documents" -> executeSearchDocuments(callRequest.arguments)
            "summarize_document" -> executeSummarizeDocument(callRequest.arguments)
            "save_summary" -> executeSaveSummary(callRequest.arguments)
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

    private fun executeSearchDocuments(arguments: JsonObject?): CallToolResult {
        val folderPath = arguments?.get("folder_path")?.jsonPrimitive?.content ?: "."
        val pattern = arguments?.get("pattern")?.jsonPrimitive?.content ?: "*"
        val recursive = arguments?.get("recursive")?.jsonPrimitive?.booleanOrNull ?: true
        val maxDepth = arguments?.get("max_depth")?.jsonPrimitive?.intOrNull ?: 5

        logger.info { "Searching documents: folder=$folderPath, pattern=$pattern, recursive=$recursive" }

        val result = documentSearchService.searchDocuments(folderPath, pattern, recursive, maxDepth)

        return if (result.isSuccess) {
            val documents = result.getOrNull()!!
            val message = documentSearchService.formatSearchResults(documents)
            CallToolResult(
                content = listOf(ToolContent(type = "text", text = message)),
                isError = false
            )
        } else {
            CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error searching documents: ${result.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }
    }

    private fun executeSummarizeDocument(arguments: JsonObject?): CallToolResult {
        val documentPath = arguments?.get("document_path")?.jsonPrimitive?.content
            ?: return CallToolResult(
                content = listOf(ToolContent(type = "text", text = "document_path parameter is required")),
                isError = true
            )

        val maxPreviewLength = arguments["max_preview_length"]?.jsonPrimitive?.intOrNull ?: 500

        logger.info { "Summarizing document: $documentPath" }

        // First, read the document
        val contentResult = documentSearchService.getDocumentContent(documentPath)
        if (contentResult.isFailure) {
            return CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error reading document: ${contentResult.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }

        val content = contentResult.getOrNull()!!

        // Then summarize it
        val summaryResult = documentSummarizationService.summarizeDocument(
            documentPath,
            content,
            maxPreviewLength
        )

        return if (summaryResult.isSuccess) {
            val summary = summaryResult.getOrNull()!!
            val message = documentSummarizationService.formatSummary(summary)
            CallToolResult(
                content = listOf(ToolContent(type = "text", text = message)),
                isError = false
            )
        } else {
            CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error summarizing document: ${summaryResult.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }
    }

    private fun executeSaveSummary(arguments: JsonObject?): CallToolResult {
        val summaryContent = arguments?.get("summary_content")?.jsonPrimitive?.content
            ?: return CallToolResult(
                content = listOf(ToolContent(type = "text", text = "summary_content parameter is required")),
                isError = true
            )

        val originalDocumentPath = arguments["original_document_path"]?.jsonPrimitive?.content
            ?: return CallToolResult(
                content = listOf(ToolContent(type = "text", text = "original_document_path parameter is required")),
                isError = true
            )

        val outputFolder = arguments["output_folder"]?.jsonPrimitive?.content ?: "summaries"
        val filename = arguments["filename"]?.jsonPrimitive?.content

        logger.info { "Saving summary for: $originalDocumentPath to folder: $outputFolder" }

        val result = documentStorageService.saveSummary(
            summaryContent,
            originalDocumentPath,
            outputFolder,
            filename
        )

        return if (result.isSuccess) {
            val savedPath = result.getOrNull()!!
            val message = documentStorageService.formatSaveResult(savedPath, originalDocumentPath)
            CallToolResult(
                content = listOf(ToolContent(type = "text", text = message)),
                isError = false
            )
        } else {
            CallToolResult(
                content = listOf(
                    ToolContent(
                        type = "text",
                        text = "Error saving summary: ${result.exceptionOrNull()?.message}"
                    )
                ),
                isError = true
            )
        }
    }
}