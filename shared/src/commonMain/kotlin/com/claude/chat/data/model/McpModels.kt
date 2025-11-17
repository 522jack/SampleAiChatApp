package com.claude.chat.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Model Context Protocol (MCP) models
 * Based on MCP specification: https://spec.modelcontextprotocol.io/
 */

/**
 * JSON-RPC 2.0 request
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonObject? = null
)

/**
 * JSON-RPC 2.0 response
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 error
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * MCP Tool definition
 */
@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject
)

/**
 * MCP Tools list response
 */
@Serializable
data class McpToolsListResponse(
    val tools: List<McpTool>
)

/**
 * MCP Tool call request
 */
@Serializable
data class McpToolCallRequest(
    val name: String,
    val arguments: JsonObject? = null
)

/**
 * MCP Tool call result
 */
@Serializable
data class McpToolCallResult(
    val content: List<McpContent>,
    @SerialName("isError")
    val isError: Boolean? = null
)

/**
 * MCP Content item
 */
@Serializable
data class McpContent(
    val type: String, // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

/**
 * MCP Resource definition
 */
@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

/**
 * MCP Resources list response
 */
@Serializable
data class McpResourcesListResponse(
    val resources: List<McpResource>
)

/**
 * MCP Prompt definition
 */
@Serializable
data class McpPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<McpPromptArgument>? = null
)

/**
 * MCP Prompt argument
 */
@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean? = null
)

/**
 * MCP Prompts list response
 */
@Serializable
data class McpPromptsListResponse(
    val prompts: List<McpPrompt>
)

/**
 * MCP Server capabilities
 */
@Serializable
data class McpServerCapabilities(
    val tools: McpToolsCapability? = null,
    val resources: McpResourcesCapability? = null,
    val prompts: McpPromptsCapability? = null
)

@Serializable
data class McpToolsCapability(
    val listChanged: Boolean? = null
)

@Serializable
data class McpResourcesCapability(
    val subscribe: Boolean? = null,
    val listChanged: Boolean? = null
)

@Serializable
data class McpPromptsCapability(
    val listChanged: Boolean? = null
)

/**
 * MCP Initialize result
 */
@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
    val capabilities: McpServerCapabilities,
    val serverInfo: McpServerInfo
)

@Serializable
data class McpServerInfo(
    val name: String,
    val version: String
)

/**
 * Claude API Tool use in response
 */
@Serializable
data class ClaudeToolUse(
    val type: String = "tool_use",
    val id: String,
    val name: String,
    val input: JsonObject
)

/**
 * Claude API Tool result
 */
@Serializable
data class ClaudeToolResult(
    val type: String = "tool_result",
    @SerialName("tool_use_id")
    val toolUseId: String,
    val content: String,
    @SerialName("is_error")
    val isError: Boolean? = null
)