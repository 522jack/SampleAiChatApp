package com.claude.support.mcp

import com.claude.support.model.SupportData
import com.claude.support.model.Ticket
import com.claude.support.model.User
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import java.io.File

/**
 * MCP Service для работы с тикетами поддержки
 * Предоставляет доступ к базе тикетов и пользователей
 */
class TicketMcpService(
    private val dataPath: String = "supportService/data/support_data.json"
) {
    private var supportData: SupportData? = null
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    init {
        loadData()
    }

    /**
     * Загрузка данных из JSON файла
     */
    private fun loadData() {
        try {
            val file = File(dataPath)
            if (file.exists()) {
                val jsonContent = file.readText()
                supportData = json.decodeFromString(SupportData.serializer(), jsonContent)
                Napier.i("Loaded ${supportData?.tickets?.size} tickets and ${supportData?.users?.size} users")
            } else {
                Napier.e("Support data file not found: $dataPath")
            }
        } catch (e: Exception) {
            Napier.e("Failed to load support data", e)
        }
    }

    /**
     * MCP Tool: Получить тикеты пользователя
     */
    fun getUserTickets(userId: String): List<Ticket> {
        return supportData?.tickets?.filter { it.userId == userId } ?: emptyList()
    }

    /**
     * MCP Tool: Получить пользователя по ID
     */
    fun getUserById(userId: String): User? {
        return supportData?.users?.find { it.id == userId }
    }

    /**
     * MCP Tool: Получить конкретный тикет по ID
     */
    fun getTicketById(ticketId: String): Ticket? {
        return supportData?.tickets?.find { it.id == ticketId }
    }

    /**
     * MCP Tool: Найти похожие решенные тикеты по категории
     * Используется для поиска решений похожих проблем
     */
    fun searchSimilarTickets(
        category: String? = null,
        keywords: List<String> = emptyList(),
        limit: Int = 5
    ): List<Ticket> {
        val tickets = supportData?.tickets ?: return emptyList()

        val filteredTickets = tickets.filter { ticket ->
            // Только решенные тикеты
            ticket.status == "resolved" &&
            // Фильтр по категории, если указана
            (category == null || ticket.category.equals(category, ignoreCase = true)) &&
            // Поиск по ключевым словам в заголовке, описании или тегах
            (keywords.isEmpty() || keywords.any { keyword ->
                ticket.title.contains(keyword, ignoreCase = true) ||
                ticket.description.contains(keyword, ignoreCase = true) ||
                ticket.tags.any { tag -> tag.contains(keyword, ignoreCase = true) }
            })
        }

        return filteredTickets.take(limit)
    }

    /**
     * MCP Tool: Поиск тикетов по тексту вопроса
     * Более гибкий поиск для нахождения похожих проблем
     */
    fun searchByQuestion(question: String, limit: Int = 3): List<Ticket> {
        val tickets = supportData?.tickets?.filter { it.status == "resolved" } ?: return emptyList()

        // Простой поиск по вхождению слов
        val questionWords = question.lowercase().split(" ").filter { it.length > 3 }

        val scored = tickets.map { ticket ->
            val text = "${ticket.title} ${ticket.description} ${ticket.tags.joinToString(" ")}".lowercase()
            val score = questionWords.count { word -> text.contains(word) }
            ticket to score
        }

        return scored
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * MCP Tool: Получить статистику по категориям
     */
    fun getCategoryStats(): Map<String, Int> {
        val tickets = supportData?.tickets ?: return emptyMap()
        return tickets.groupBy { it.category }.mapValues { it.value.size }
    }

    /**
     * Форматирование тикета для контекста
     */
    fun formatTicketForContext(ticket: Ticket): String {
        return buildString {
            appendLine("Тикет #${ticket.id}")
            appendLine("Категория: ${ticket.category}")
            appendLine("Приоритет: ${ticket.priority}")
            appendLine("Проблема: ${ticket.title}")
            appendLine("Описание: ${ticket.description}")
            if (ticket.solution.isNotEmpty()) {
                appendLine("Решение: ${ticket.solution}")
            }
            if (ticket.tags.isNotEmpty()) {
                appendLine("Теги: ${ticket.tags.joinToString(", ")}")
            }
        }
    }

    /**
     * Форматирование нескольких тикетов для контекста
     */
    fun formatTicketsForContext(tickets: List<Ticket>): String {
        if (tickets.isEmpty()) return "Похожие тикеты не найдены."

        return buildString {
            appendLine("Найдено ${tickets.size} похожих решенных тикетов:")
            appendLine()
            tickets.forEachIndexed { index, ticket ->
                appendLine("=== Тикет ${index + 1} ===")
                append(formatTicketForContext(ticket))
                appendLine()
            }
        }
    }
}