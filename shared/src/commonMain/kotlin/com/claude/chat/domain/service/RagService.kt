package com.claude.chat.domain.service

import com.claude.chat.data.model.ChunkEmbedding
import com.claude.chat.data.model.RagDocument
import com.claude.chat.data.model.RagIndex
import com.claude.chat.data.model.RagSearchResult
import com.claude.chat.data.model.TextChunk
import com.claude.chat.data.remote.OllamaClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.math.sqrt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Service for RAG (Retrieval-Augmented Generation) operations
 */
@OptIn(ExperimentalUuidApi::class)
class RagService(
    private val ollamaClient: OllamaClient,
    private val textChunker: TextChunker,
    private val embeddingModel: String = "nomic-embed-text"
) {
    private var currentIndex: RagIndex? = null

    /**
     * Index a document by chunking it and generating embeddings
     */
    suspend fun indexDocument(
        title: String,
        content: String,
        metadata: Map<String, String> = emptyMap()
    ): Result<RagDocument> = coroutineScope {
        try {
            Napier.d("Indexing document: $title")

            // Create document
            val document = RagDocument(
                id = Uuid.random().toString(),
                title = title,
                content = content,
                timestamp = Clock.System.now(),
                metadata = metadata
            )

            // Chunk the text
            val chunks = textChunker.chunkText(content, document.id)
            Napier.d("Created ${chunks.size} chunks for document")

            // Generate embeddings for all chunks
            val chunkTexts = chunks.map { it.content }
            val embeddingsResult = ollamaClient.generateEmbeddings(chunkTexts, embeddingModel)

            if (embeddingsResult.isFailure) {
                return@coroutineScope Result.failure(
                    embeddingsResult.exceptionOrNull() ?: Exception("Failed to generate embeddings")
                )
            }

            val embeddings = embeddingsResult.getOrThrow()

            // Create chunk embeddings
            val chunkEmbeddings = chunks.mapIndexed { index, chunk ->
                ChunkEmbedding(
                    chunkId = chunk.id,
                    documentId = document.id,
                    content = chunk.content,
                    embedding = embeddings[index],
                    chunkIndex = chunk.chunkIndex
                )
            }

            // Update index
            val existingDocuments = currentIndex?.documents ?: emptyList()
            val existingEmbeddings = currentIndex?.embeddings ?: emptyList()

            currentIndex = RagIndex(
                documents = existingDocuments + document,
                embeddings = existingEmbeddings + chunkEmbeddings,
                lastUpdated = Clock.System.now()
            )

            Napier.d("Document indexed successfully with ${chunkEmbeddings.size} embeddings")
            Result.success(document)

        } catch (e: Exception) {
            Napier.e("Error indexing document", e)
            Result.failure(e)
        }
    }

    /**
     * Search for relevant chunks based on a query
     */
    suspend fun search(
        query: String,
        topK: Int = 5,
        minSimilarity: Double = 0.0
    ): Result<List<RagSearchResult>> = coroutineScope {
        try {
            val index = currentIndex
            if (index == null || index.embeddings.isEmpty()) {
                Napier.w("No index available for search")
                return@coroutineScope Result.success(emptyList())
            }

            Napier.d("Searching for query: $query")

            // Generate embedding for query
            val queryEmbeddingResult = ollamaClient.generateEmbedding(query, embeddingModel)
            if (queryEmbeddingResult.isFailure) {
                return@coroutineScope Result.failure(
                    queryEmbeddingResult.exceptionOrNull() ?: Exception("Failed to generate query embedding")
                )
            }

            val queryEmbedding = queryEmbeddingResult.getOrThrow()

            // Calculate similarities
            val results = index.embeddings.map { chunkEmbedding ->
                val similarity = cosineSimilarity(queryEmbedding, chunkEmbedding.embedding)
                val document = index.documents.find { it.id == chunkEmbedding.documentId }

                RagSearchResult(
                    chunk = chunkEmbedding,
                    similarity = similarity,
                    documentTitle = document?.title ?: "Unknown"
                )
            }
                .filter { it.similarity >= minSimilarity }
                .sortedByDescending { it.similarity }
                .take(topK)

            Napier.d("Found ${results.size} relevant chunks")
            results.forEachIndexed { index, result ->
                Napier.d("  ${index + 1}. ${result.documentTitle} - similarity: ${result.similarity}")
            }

            Result.success(results)

        } catch (e: Exception) {
            Napier.e("Error searching", e)
            Result.failure(e)
        }
    }

    /**
     * Get all indexed documents
     */
    fun getIndexedDocuments(): List<RagDocument> {
        return currentIndex?.documents ?: emptyList()
    }

    /**
     * Remove a document from the index
     */
    fun removeDocument(documentId: String): Boolean {
        val index = currentIndex ?: return false

        val updatedDocuments = index.documents.filter { it.id != documentId }
        val updatedEmbeddings = index.embeddings.filter { it.documentId != documentId }

        currentIndex = RagIndex(
            documents = updatedDocuments,
            embeddings = updatedEmbeddings,
            lastUpdated = Clock.System.now()
        )

        Napier.d("Removed document $documentId from index")
        return true
    }

    /**
     * Clear the entire index
     */
    fun clearIndex() {
        currentIndex = null
        Napier.d("Cleared RAG index")
    }

    /**
     * Save index to JSON string
     */
    fun saveIndexToJson(): String? {
        val index = currentIndex ?: return null

        return try {
            Json.encodeToString(RagIndex.serializer(), index)
        } catch (e: Exception) {
            Napier.e("Error serializing index", e)
            null
        }
    }

    /**
     * Load index from JSON string
     */
    fun loadIndexFromJson(json: String): Boolean {
        return try {
            currentIndex = Json.decodeFromString(RagIndex.serializer(), json)
            Napier.d("Loaded index with ${currentIndex?.documents?.size} documents")
            true
        } catch (e: Exception) {
            Napier.e("Error deserializing index", e)
            false
        }
    }

    /**
     * Get current index
     */
    fun getCurrentIndex(): RagIndex? = currentIndex

    /**
     * Calculate cosine similarity between two vectors
     */
    private fun cosineSimilarity(vec1: List<Double>, vec2: List<Double>): Double {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vectors must have the same dimension")
        }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0.0) dotProduct / denominator else 0.0
    }

    /**
     * Generate context from search results for RAG
     */
    fun generateContext(searchResults: List<RagSearchResult>): String {
        if (searchResults.isEmpty()) {
            return ""
        }

        return buildString {
            appendLine("Context from knowledge base:")
            appendLine()
            searchResults.forEachIndexed { index, result ->
                appendLine("--- Source ${index + 1}: ${result.documentTitle} (relevance: ${"%.2f".format(result.similarity)}) ---")
                appendLine(result.chunk.content)
                appendLine()
            }
        }
    }
}