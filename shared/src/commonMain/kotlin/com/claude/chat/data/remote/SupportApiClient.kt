package com.claude.chat.data.remote

import com.claude.chat.data.model.SupportQuestion
import com.claude.chat.data.model.SupportResponse
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * API клиент для взаимодействия с сервисом поддержки
 */
class SupportApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://localhost:8080"
) {
    /**
     * Отправить вопрос в службу поддержки
     */
    suspend fun askQuestion(userId: String, question: String): Result<SupportResponse> {
        return try {
            Napier.i("Sending support question...")

            val response = httpClient.post("$baseUrl/api/support/ask") {
                contentType(ContentType.Application.Json)
                setBody(SupportQuestion(userId = userId, question = question))
            }

            if (response.status.isSuccess()) {
                val supportResponse = response.body<SupportResponse>()
                Napier.i("Received support response: category=${supportResponse.category}")
                Result.success(supportResponse)
            } else {
                val error = "API returned ${response.status}"
                Napier.e(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Napier.e("Failed to send support question", e)
            Result.failure(e)
        }
    }

    /**
     * Проверка работоспособности сервиса
     */
    suspend fun healthCheck(): Result<Boolean> {
        return try {
            val response = httpClient.get("$baseUrl/api/support/health")
            Result.success(response.status.isSuccess())
        } catch (e: Exception) {
            Napier.e("Health check failed", e)
            Result.failure(e)
        }
    }
}