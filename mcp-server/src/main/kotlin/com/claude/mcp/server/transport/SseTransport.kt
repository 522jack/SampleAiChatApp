package com.claude.mcp.server.transport

import com.claude.mcp.server.McpServerHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * SSE (Server-Sent Events) Transport for MCP Server
 * Provides HTTP-based communication for MCP protocol
 */
class SseTransport(
    private val handler: McpServerHandler,
    private val port: Int = 3000
) {
    private val sessions = mutableMapOf<String, Channel<String>>()

    suspend fun start() {
        logger.info { "Starting SSE transport on port $port" }

        embeddedServer(CIO, port = port) {
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Options)
            }

            routing {
                // Health check endpoint
                get("/health") {
                    call.respondText("OK", ContentType.Text.Plain, HttpStatusCode.OK)
                }

                // SSE endpoint for MCP communication
                get("/sse") {
                    val sessionId = UUID.randomUUID().toString()
                    val channel = Channel<String>(Channel.UNLIMITED)
                    sessions[sessionId] = channel

                    logger.info { "New SSE session: $sessionId" }

                    try {
                        call.response.cacheControl(CacheControl.NoCache(null))
                        call.response.header(HttpHeaders.ContentType, "text/event-stream")
                        call.response.header(HttpHeaders.Connection, "keep-alive")
                        call.response.header("X-Accel-Buffering", "no")

                        // Send session ID
                        call.respondTextWriter {
                            write("data: {\"type\":\"session\",\"sessionId\":\"$sessionId\"}\n\n")
                            flush()

                            // Send events from channel
                            channel.receiveAsFlow().collect { message ->
                                write("data: $message\n\n")
                                flush()
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error in SSE session $sessionId" }
                    } finally {
                        sessions.remove(sessionId)
                        channel.close()
                        logger.info { "SSE session closed: $sessionId" }
                    }
                }

                // Simple JSON-RPC endpoint (stateless)
                post("/mcp") {
                    try {
                        val requestBody = call.receiveText()
                        logger.debug { "Received RPC request: $requestBody" }

                        val response = handler.handleRequest(requestBody)

                        call.respondText(
                            response,
                            ContentType.Application.Json,
                            HttpStatusCode.OK
                        )
                    } catch (e: Exception) {
                        logger.error(e) { "Error processing RPC request" }
                        call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
                    }
                }

                // POST endpoint for sending MCP messages (SSE-based)
                post("/message") {
                    val sessionId = call.request.header("X-Session-Id")
                    if (sessionId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing X-Session-Id header")
                        return@post
                    }

                    val channel = sessions[sessionId]
                    if (channel == null) {
                        call.respond(HttpStatusCode.NotFound, "Session not found")
                        return@post
                    }

                    try {
                        val requestBody = call.receiveText()
                        logger.debug { "Received message in session $sessionId: $requestBody" }

                        val response = handler.handleRequest(requestBody)
                        channel.send(response)

                        call.respond(HttpStatusCode.OK, mapOf("status" to "sent"))
                    } catch (e: Exception) {
                        logger.error(e) { "Error processing message" }
                        call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
                    }
                }

                // List active sessions (for debugging)
                get("/sessions") {
                    call.respond(
                        mapOf(
                            "sessions" to sessions.keys.toList(),
                            "count" to sessions.size
                        )
                    )
                }
            }
        }.start(wait = true)
    }
}