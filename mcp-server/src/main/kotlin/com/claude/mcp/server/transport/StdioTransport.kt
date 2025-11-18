package com.claude.mcp.server.transport

import com.claude.mcp.server.McpServerHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/**
 * STDIO Transport for MCP Server
 * Communicates via stdin/stdout using JSON-RPC over newline-delimited JSON
 */
class StdioTransport(
    private val handler: McpServerHandler
) {
    suspend fun start() {
        logger.info { "STDIO transport started. Listening for requests..." }

        withContext(Dispatchers.IO) {
            try {
                while (true) {
                    val line = readLine() ?: break

                    if (line.isBlank()) {
                        continue
                    }

                    logger.debug { "Received line: $line" }

                    try {
                        val response = handler.handleRequest(line)
                        println(response)
                        System.out.flush()
                    } catch (e: Exception) {
                        logger.error(e) { "Error handling request" }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Fatal error in STDIO transport" }
            }
        }

        logger.info { "STDIO transport stopped" }
    }
}