package com.claude.mcp.server

import com.claude.mcp.server.services.WeatherService
import com.claude.mcp.server.transport.SseTransport
import com.claude.mcp.server.transport.StdioTransport
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) = runBlocking {
    logger.info { "Starting Weather MCP Server v1.0.0" }

    val transport = args.firstOrNull() ?: "stdio"
    val apiKey = System.getenv("OPENWEATHER_API_KEY") ?: "demo"

    if (apiKey == "demo") {
        logger.warn { "Using demo API key. Set OPENWEATHER_API_KEY environment variable for production use." }
    }

    // Create HTTP client
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    // Create services
    val weatherService = WeatherService(httpClient, apiKey)

    // Create MCP handler
    val mcpHandler = McpServerHandler(weatherService)

    // Start appropriate transport
    when (transport.lowercase()) {
        "stdio" -> {
            logger.info { "Starting in STDIO mode" }
            val stdioTransport = StdioTransport(mcpHandler)
            stdioTransport.start()
        }
        "sse" -> {
            val port = args.getOrNull(1)?.toIntOrNull() ?: 3000
            logger.info { "Starting in SSE mode on port $port" }
            val sseTransport = SseTransport(mcpHandler, port)
            sseTransport.start()
        }
        else -> {
            logger.error { "Unknown transport: $transport. Use 'stdio' or 'sse'" }
            return@runBlocking
        }
    }
}