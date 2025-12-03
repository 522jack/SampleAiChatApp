package com.claude.support

import com.claude.chat.data.remote.ClaudeApiClientImpl
import com.claude.support.api.SupportService
import com.claude.support.api.configureSupportRoutes
import com.claude.support.categorization.CategorizationService
import com.claude.support.mcp.TicketMcpService
import com.claude.support.rag.SupportRagService
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.cors.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.logging.*

/**
 * Create HttpClient for JVM
 */
fun createJvmHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 120000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 120000
        }
    }
}

fun main() {
    // Initialize logger
    Napier.base(DebugAntilog())

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    // Configure JSON serialization
    install(ServerContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    // Configure CORS
    install(CORS) {
        anyHost()
        allowHeader("Content-Type")
    }

    Napier.i("Support Service starting...")

    // Get Claude API key from environment variable
    val apiKey = System.getenv("CLAUDE_API_KEY") ?: run {
        Napier.w("CLAUDE_API_KEY not set, using placeholder")
        "your-api-key-here"
    }

    // Initialize HTTP client
    val httpClient = createJvmHttpClient()

    // Initialize services
    val ticketService = TicketMcpService()
    val ragService = SupportRagService(httpClient)
    val claudeApiClient = ClaudeApiClientImpl(httpClient)
    val categorizationService = CategorizationService(claudeApiClient, apiKey)

    val supportService = SupportService(
        ragService = ragService,
        ticketService = ticketService,
        categorizationService = categorizationService,
        claudeApiClient = claudeApiClient,
        apiKey = apiKey
    )

    // Initialize RAG (index documents)
    runBlocking {
        try {
            Napier.i("Initializing RAG service...")
            ragService.initialize()
            Napier.i("RAG service initialized successfully")
        } catch (e: Exception) {
            Napier.e("Failed to initialize RAG service", e)
        }
    }

    // Configure routing
    configureSupportRoutes(supportService)

    Napier.i("Support Service started on http://0.0.0.0:8080")
    Napier.i("API documentation available at: http://0.0.0.0:8080/api/support")
}