package com.claude.support.api

import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageContent
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.support.categorization.CategorizationService
import com.claude.support.mcp.TicketMcpService
import com.claude.support.model.SourceReference
import com.claude.support.model.SupportQuestion
import com.claude.support.model.SupportResponse
import com.claude.support.rag.SupportRagService
import io.github.aakira.napier.Napier

/**
 * Главный сервис поддержки
 * Объединяет RAG, MCP и категоризацию для ответа на вопросы пользователей
 */
class SupportService(
    private val ragService: SupportRagService,
    private val ticketService: TicketMcpService,
    private val categorizationService: CategorizationService,
    private val claudeApiClient: ClaudeApiClient,
    private val apiKey: String
) {

    /**
     * Обработка вопроса пользователя
     * 1. Категоризация вопроса
     * 2. Поиск в RAG (документация)
     * 3. Поиск похожих тикетов через MCP
     * 4. Формирование контекста
     * 5. Генерация ответа через Claude
     */
    suspend fun processQuestion(question: SupportQuestion): SupportResponse {
        Napier.i("Processing support question from user: ${question.userId}")

        // 1. Категоризация вопроса
        val categoryResult = categorizationService.categorize(question.question)
        Napier.i("Question categorized as: ${categoryResult.category} (confidence: ${categoryResult.confidence})")

        // 2. Поиск в документации (RAG)
        val ragResults = ragService.search(question.question, topK = 3)
        Napier.i("Found ${ragResults.size} relevant documentation fragments")

        // 3. Поиск похожих тикетов
        val similarTickets = ticketService.searchByQuestion(question.question, limit = 3)
        Napier.i("Found ${similarTickets.size} similar tickets")

        // 4. Получение информации о пользователе
        val user = ticketService.getUserById(question.userId)
        val userContext = if (user != null) {
            "План пользователя: ${user.plan}"
        } else {
            "Информация о пользователе недоступна"
        }

        // 5. Формирование контекста
        val context = buildContext(ragResults, similarTickets, userContext)

        // 6. Генерация ответа через Claude
        val answer = generateAnswer(question.question, context, categoryResult.category)

        // 7. Формирование списка источников
        val sources = mutableListOf<SourceReference>()

        // Добавляем источники из документации
        ragResults.forEach { result ->
            sources.add(
                SourceReference(
                    type = "doc",
                    title = result.chunk.documentTitle,
                    id = result.source,
                    relevanceScore = result.similarity
                )
            )
        }

        // Добавляем источники из тикетов
        similarTickets.forEach { ticket ->
            sources.add(
                SourceReference(
                    type = "ticket",
                    title = ticket.title,
                    id = ticket.id
                )
            )
        }

        return SupportResponse(
            answer = answer,
            category = categoryResult.category,
            confidence = categoryResult.confidence,
            sources = sources
        )
    }

    /**
     * Формирование контекста для Claude
     */
    private fun buildContext(
        ragResults: List<com.claude.support.model.SearchResult>,
        similarTickets: List<com.claude.support.model.Ticket>,
        userContext: String
    ): String {
        return buildString {
            appendLine("=== КОНТЕКСТ ДЛЯ ОТВЕТА ===")
            appendLine()

            // Информация о пользователе
            appendLine("--- Информация о пользователе ---")
            appendLine(userContext)
            appendLine()

            // Документация
            if (ragResults.isNotEmpty()) {
                appendLine("--- Релевантные фрагменты документации ---")
                appendLine(ragService.formatSearchResultsForContext(ragResults))
                appendLine()
            }

            // Похожие тикеты
            if (similarTickets.isNotEmpty()) {
                appendLine("--- Похожие решенные тикеты ---")
                appendLine(ticketService.formatTicketsForContext(similarTickets))
                appendLine()
            }

            appendLine("=== КОНЕЦ КОНТЕКСТА ===")
        }
    }

    /**
     * Генерация ответа через Claude API
     */
    private suspend fun generateAnswer(
        question: String,
        context: String,
        category: String
    ): String {
        val systemPrompt = """
Ты - AI-ассистент службы поддержки приложения Claude Chat.
Твоя задача - отвечать на вопросы пользователей о приложении, используя предоставленную документацию и информацию о похожих тикетах.

ВАЖНО:
1. Отвечай на русском языке
2. Будь вежливым и профессиональным
3. Если информация есть в контексте - используй её и ссылайся на источники
4. Если информации нет - честно признай это и предложи связаться с человеком-оператором
5. Предоставляй конкретные шаги для решения проблемы
6. Используй форматирование для лучшей читаемости (списки, выделение)

Категория вопроса: $category

$context
        """.trimIndent()

        val userPrompt = "Вопрос пользователя: $question"

        try {
            val request = ClaudeMessageRequest(
                model = "claude-sonnet-4-5-20250929",
                maxTokens = 1500,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = ClaudeMessageContent.Text("$systemPrompt\n\n$userPrompt")
                    )
                ),
                temperature = 0.7,
                stream = false
            )

            val response = claudeApiClient.sendMessageNonStreaming(request, apiKey)
                .getOrThrow()
            return response.content.firstOrNull()?.text ?: "Извините, не удалось сгенерировать ответ."
        } catch (e: Exception) {
            Napier.e("Failed to generate answer", e)
            return "Извините, произошла ошибка при обработке вашего вопроса. Пожалуйста, попробуйте позже или обратитесь к оператору поддержки."
        }
    }

    /**
     * Проверка работоспособности сервиса
     */
    suspend fun healthCheck(): Map<String, Any> {
        return mapOf(
            "status" to "ok",
            "ragDocuments" to ragService.getIndexedDocuments().size,
            "ticketsLoaded" to (ticketService.getUserById("user_001") != null),
            "timestamp" to System.currentTimeMillis()
        )
    }
}