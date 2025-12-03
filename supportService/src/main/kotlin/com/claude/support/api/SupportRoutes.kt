package com.claude.support.api

import com.claude.support.model.SupportQuestion
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * API маршруты для сервиса поддержки
 */
fun Application.configureSupportRoutes(supportService: SupportService) {
    routing {
        route("/api/support") {
            // Health check endpoint
            get("/health") {
                val health = supportService.healthCheck()
                call.respond(HttpStatusCode.OK, health)
            }

            // Главный endpoint для обработки вопросов
            post("/ask") {
                try {
                    val question = call.receive<SupportQuestion>()

                    if (question.question.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Question cannot be empty")
                        )
                        return@post
                    }

                    val response = supportService.processQuestion(question)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to process question: ${e.message}")
                    )
                }
            }

            // Root endpoint с информацией о сервисе
            get {
                call.respondText(
                    """
                    Support Service API

                    Available endpoints:
                    - GET  /api/support/health - Health check
                    - POST /api/support/ask    - Ask a support question

                    Example request to /api/support/ask:
                    {
                      "userId": "user_001",
                      "question": "Почему не работает авторизация?"
                    }
                    """.trimIndent(),
                    ContentType.Text.Plain
                )
            }
        }

        // Root endpoint
        get("/") {
            call.respondText(
                """
                Claude Chat Support Service

                Backend service for AI-powered customer support.

                Features:
                - RAG-based documentation search
                - MCP integration for ticket management
                - Automatic question categorization
                - Claude AI-powered responses

                API Documentation: /api/support
                Health Check: /api/support/health
                """.trimIndent(),
                ContentType.Text.Plain
            )
        }
    }
}