package com.claude.mcp.server.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * MCP Protocol Messages
 * Based on MCP specification: https://spec.modelcontextprotocol.io
 */

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonObject? = null
)

// MCP Protocol Methods
object McpMethods {
    const val INITIALIZE = "initialize"
    const val INITIALIZED = "notifications/initialized"
    const val TOOLS_LIST = "tools/list"
    const val TOOLS_CALL = "tools/call"
    const val RESOURCES_LIST = "resources/list"
    const val RESOURCES_READ = "resources/read"
    const val PROMPTS_LIST = "prompts/list"
    const val PROMPTS_GET = "prompts/get"
    const val PING = "ping"
}

// Initialize Request/Response
@Serializable
data class InitializeRequest(
    val protocolVersion: String,
    val capabilities: ClientCapabilities,
    val clientInfo: Implementation
)

@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: Implementation,
    val instructions: String? = null
)

@Serializable
data class ClientCapabilities(
    val experimental: JsonObject? = null,
    val sampling: JsonObject? = null,
    val roots: RootsCapability? = null
)

@Serializable
data class ServerCapabilities(
    val experimental: JsonObject? = null,
    val logging: JsonObject? = null,
    val prompts: PromptsCapability? = null,
    val resources: ResourcesCapability? = null,
    val tools: ToolsCapability? = null
)

@Serializable
data class RootsCapability(
    val listChanged: Boolean = false
)

@Serializable
data class PromptsCapability(
    val listChanged: Boolean = false
)

@Serializable
data class ResourcesCapability(
    val subscribe: Boolean = false,
    val listChanged: Boolean = false
)

@Serializable
data class ToolsCapability(
    val listChanged: Boolean = false
)

@Serializable
data class Implementation(
    val name: String,
    val version: String
)

// Tools
@Serializable
data class Tool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

@Serializable
data class ToolsListResult(
    val tools: List<Tool>
)

@Serializable
data class CallToolRequest(
    val name: String,
    val arguments: JsonObject? = null
)

@Serializable
data class CallToolResult(
    val content: List<ToolContent>,
    val isError: Boolean = false
)

@Serializable
data class ToolContent(
    val type: String, // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

// Resources
@Serializable
data class Resource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

@Serializable
data class ResourcesListResult(
    val resources: List<Resource>
)

// Prompts
@Serializable
data class Prompt(
    val name: String,
    val description: String? = null,
    val arguments: List<PromptArgument>? = null
)

@Serializable
data class PromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false
)

@Serializable
data class PromptsListResult(
    val prompts: List<Prompt>
)

// Error Codes (JSON-RPC standard + MCP extensions)
object ErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}