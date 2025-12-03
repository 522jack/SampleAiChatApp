package com.claude.support.rag

import com.claude.chat.data.remote.OllamaClient
import com.claude.chat.domain.service.RagService
import com.claude.chat.domain.service.TextChunker
import com.claude.support.model.SearchResult
import com.claude.support.model.DocumentChunk
import io.github.aakira.napier.Napier
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * RAG сервис для индексации и поиска по документации поддержки
 */
class SupportRagService(
    httpClient: HttpClient,
    private val docsPath: String = "supportService/docs"
) {
    private val ollamaClient = OllamaClient(httpClient)
    private val textChunker = TextChunker(chunkSize = 500, chunkOverlap = 50)
    private val ragService = RagService(ollamaClient, textChunker)

    /**
     * Инициализация: индексация всех документов из папки docs
     */
    suspend fun initialize() {
        Napier.i("Initializing RAG service...")
        val docsDir = File(docsPath)

        if (!docsDir.exists() || !docsDir.isDirectory) {
            Napier.e("Docs directory not found: $docsPath")
            return
        }

        val markdownFiles = docsDir.listFiles { file ->
            file.extension == "md"
        } ?: emptyArray()

        Napier.i("Found ${markdownFiles.size} documentation files")

        markdownFiles.forEach { file ->
            try {
                Napier.i("Indexing ${file.name}...")
                val content = file.readText()
                val title = file.nameWithoutExtension
                    .split("_")
                    .joinToString(" ") { it.capitalize() }

                val result = ragService.indexDocument(
                    title = title,
                    content = content,
                    metadata = mapOf(
                        "filename" to file.name,
                        "path" to file.absolutePath
                    )
                )

                result.onSuccess { doc ->
                    Napier.i("Successfully indexed: ${doc.title}")
                }.onFailure { error ->
                    Napier.e("Failed to index ${file.name}: ${error.message}")
                }
            } catch (e: Exception) {
                Napier.e("Error processing ${file.name}", e)
            }
        }

        Napier.i("RAG initialization complete. Indexed ${ragService.getIndexedDocuments().size} documents")
    }

    /**
     * Поиск релевантных фрагментов документации по запросу
     */
    suspend fun search(query: String, topK: Int = 5): List<SearchResult> {
        val result = ragService.search(query, topK, minSimilarity = 0.3)

        return result.getOrElse {
            Napier.e("RAG search failed: ${it.message}")
            emptyList()
        }.map { ragResult ->
            SearchResult(
                chunk = DocumentChunk(
                    documentId = ragResult.chunk.documentId,
                    documentTitle = ragResult.documentTitle,
                    chunkIndex = ragResult.chunk.chunkIndex,
                    content = ragResult.chunk.content
                ),
                similarity = ragResult.similarity,
                source = ragResult.documentTitle
            )
        }
    }

    /**
     * Форматирование результатов поиска для контекста
     */
    fun formatSearchResultsForContext(results: List<SearchResult>): String {
        if (results.isEmpty()) {
            return "Релевантная информация в документации не найдена."
        }

        return buildString {
            appendLine("Найдено ${results.size} релевантных фрагментов документации:")
            appendLine()

            results.forEachIndexed { index, result ->
                appendLine("=== Источник ${index + 1}: ${result.chunk.documentTitle} ===")
                appendLine("Релевантность: ${String.format("%.2f", result.similarity * 100)}%")
                appendLine("Фрагмент #${result.chunk.chunkIndex + 1}")
                appendLine()
                appendLine(result.chunk.content.trim())
                appendLine()
            }
        }
    }

    /**
     * Получить список проиндексированных документов
     */
    fun getIndexedDocuments() = ragService.getIndexedDocuments()
}