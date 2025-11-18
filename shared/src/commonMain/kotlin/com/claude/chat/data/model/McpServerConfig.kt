package com.claude.chat.data.model

import kotlinx.serialization.Serializable

/**
 * Configuration for external MCP servers
 */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val type: McpServerType,
    val enabled: Boolean = true,
    val config: McpConnectionConfig
)

@Serializable
enum class McpServerType {
    /** HTTP/SSE connection */
    HTTP,
    /** Local process via STDIO */
    PROCESS
}

@Serializable
sealed class McpConnectionConfig {
    @Serializable
    data class HttpConfig(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    ) : McpConnectionConfig()

    @Serializable
    data class ProcessConfig(
        val command: String,
        val args: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
        val workingDir: String? = null
    ) : McpConnectionConfig()
}

/**
 * Preset configurations for common MCP servers
 */
object McpServerPresets {
    fun localWeatherServer(port: Int = 3000) = McpServerConfig(
        id = "local-weather",
        name = "Weather Server (Local)",
        type = McpServerType.HTTP,
        enabled = true,
        config = McpConnectionConfig.HttpConfig(
            url = "http://localhost:$port"
        )
    )

    fun weatherServerProcess(jarPath: String, apiKey: String? = null) = McpServerConfig(
        id = "weather-process",
        name = "Weather Server (Process)",
        type = McpServerType.PROCESS,
        enabled = true,
        config = McpConnectionConfig.ProcessConfig(
            command = "java",
            args = listOf("-jar", jarPath, "stdio"),
            env = if (apiKey != null) mapOf("OPENWEATHER_API_KEY" to apiKey) else emptyMap()
        )
    )
}