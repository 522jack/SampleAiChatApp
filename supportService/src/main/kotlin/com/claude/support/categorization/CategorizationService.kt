package com.claude.support.categorization

import com.claude.chat.data.model.ClaudeMessage
import com.claude.chat.data.model.ClaudeMessageContent
import com.claude.chat.data.model.ClaudeMessageRequest
import com.claude.chat.data.remote.ClaudeApiClient
import com.claude.support.model.CategoryResult
import com.claude.support.model.QuestionCategory
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json

/**
 * Сервис автоматической категоризации вопросов пользователей
 * Использует Claude API для определения категории вопроса
 */
class CategorizationService(
    private val claudeApiClient: ClaudeApiClient,
    private val apiKey: String
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Определяет категорию вопроса пользователя
     */
    suspend fun categorize(question: String): CategoryResult {
        val prompt = buildCategorizationPrompt(question)

        try {
            val request = ClaudeMessageRequest(
                model = "claude-sonnet-4-5-20250929",
                maxTokens = 200,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = ClaudeMessageContent.Text(prompt)
                    )
                ),
                temperature = 0.3, // Низкая температура для более последовательных результатов
                stream = false
            )

            val response = claudeApiClient.sendMessageNonStreaming(request, apiKey)
                .getOrThrow()

            // Парсим ответ от Claude
            val responseText = response.content.firstOrNull()?.text ?: ""
            val category = extractCategory(responseText)
            val confidence = extractConfidence(responseText)

            Napier.i("Categorized question as: $category (confidence: $confidence)")

            return CategoryResult(
                category = category,
                confidence = confidence
            )
        } catch (e: Exception) {
            Napier.e("Categorization failed: ${e.message}", e)
            // Fallback на "другое" при ошибке
            return CategoryResult(
                category = QuestionCategory.OTHER.displayName,
                confidence = 0.0
            )
        }
    }

    /**
     * Создает промпт для категоризации
     */
    private fun buildCategorizationPrompt(question: String): String {
        return """
Определи категорию следующего вопроса пользователя о приложении Claude Chat.

Доступные категории:
1. авторизация - вопросы о входе, регистрации, паролях, OAuth, сессиях
2. оплата - вопросы о подписках, платежах, тарифах, возвратах
3. функционал - вопросы о возможностях, настройках, использовании функций
4. баги - сообщения об ошибках, зависаниях, некорректной работе
5. другое - все остальные вопросы

Вопрос пользователя: "$question"

Ответь ТОЛЬКО в следующем формате (без дополнительного текста):
Категория: <название_категории>
Уверенность: <число от 0.0 до 1.0>

Пример:
Категория: авторизация
Уверенность: 0.95
        """.trimIndent()
    }

    /**
     * Извлекает категорию из ответа Claude
     */
    private fun extractCategory(response: String): String {
        val categoryLine = response.lines().find { it.startsWith("Категория:") }
        val categoryText = categoryLine?.substringAfter(":")?.trim()?.lowercase() ?: ""

        // Находим наиболее подходящую категорию
        return QuestionCategory.values().find {
            categoryText.contains(it.displayName)
        }?.displayName ?: QuestionCategory.OTHER.displayName
    }

    /**
     * Извлекает уверенность из ответа Claude
     */
    private fun extractConfidence(response: String): Double {
        val confidenceLine = response.lines().find { it.contains("Уверенность:") || it.contains("Confidence:") }
        val confidenceText = confidenceLine?.substringAfter(":")?.trim() ?: "0.5"

        return try {
            confidenceText.toDoubleOrNull() ?: 0.5
        } catch (e: Exception) {
            0.5
        }
    }

    /**
     * Простая категоризация на основе ключевых слов (fallback)
     * Используется если Claude API недоступен
     */
    fun categorizeByKeywords(question: String): CategoryResult {
        val lowerQuestion = question.lowercase()

        val categoryScores = mutableMapOf<QuestionCategory, Int>()

        // Ключевые слова для каждой категории
        val keywords = mapOf(
            QuestionCategory.AUTHORIZATION to listOf(
                "войти", "вход", "логин", "пароль", "авторизация", "регистрация",
                "oauth", "google", "apple", "сессия", "токен", "аккаунт"
            ),
            QuestionCategory.PAYMENT to listOf(
                "оплата", "платеж", "подписка", "premium", "деньги", "карта",
                "списали", "возврат", "тариф", "free", "купить"
            ),
            QuestionCategory.FUNCTIONALITY to listOf(
                "как", "функция", "возможность", "настройка", "экспорт", "история",
                "чат", "сообщение", "тема", "язык", "интерфейс"
            ),
            QuestionCategory.BUGS to listOf(
                "ошибка", "не работает", "зависает", "вылетает", "баг", "сломалось",
                "не открывается", "не сохраняется", "глюк", "проблема"
            )
        )

        // Подсчитываем совпадения
        keywords.forEach { (category, words) ->
            val matches = words.count { lowerQuestion.contains(it) }
            if (matches > 0) {
                categoryScores[category] = matches
            }
        }

        // Находим категорию с максимальным количеством совпадений
        val bestMatch = categoryScores.maxByOrNull { it.value }

        return if (bestMatch != null && bestMatch.value > 0) {
            val confidence = minOf(bestMatch.value * 0.3, 0.9)
            CategoryResult(
                category = bestMatch.key.displayName,
                confidence = confidence
            )
        } else {
            CategoryResult(
                category = QuestionCategory.OTHER.displayName,
                confidence = 0.3
            )
        }
    }
}