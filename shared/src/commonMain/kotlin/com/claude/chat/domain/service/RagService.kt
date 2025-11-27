package com.claude.chat.domain.service

import com.claude.chat.data.model.ChunkEmbedding
import com.claude.chat.data.model.OllamaOptions
import com.claude.chat.data.model.RagDocument
import com.claude.chat.data.model.RagIndex
import com.claude.chat.data.model.RagSearchConfig
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
     * Search for relevant chunks based on a query with configuration
     */
    suspend fun searchWithConfig(
        query: String,
        config: RagSearchConfig = RagSearchConfig()
    ): Result<List<RagSearchResult>> = coroutineScope {
        try {
            val index = currentIndex
            if (index == null || index.embeddings.isEmpty()) {
                Napier.w("No index available for search")
                return@coroutineScope Result.success(emptyList())
            }

            Napier.d("Searching for query: $query (reranking: ${config.enableReranking})")

            // Generate embedding for query
            val queryEmbeddingResult = ollamaClient.generateEmbedding(query, embeddingModel)
            if (queryEmbeddingResult.isFailure) {
                return@coroutineScope Result.failure(
                    queryEmbeddingResult.exceptionOrNull() ?: Exception("Failed to generate query embedding")
                )
            }

            val queryEmbedding = queryEmbeddingResult.getOrThrow()

            // Stage 1: Calculate cosine similarities and filter by threshold
            val initialResults = index.embeddings.map { chunkEmbedding ->
                val similarity = cosineSimilarity(queryEmbedding, chunkEmbedding.embedding)
                val document = index.documents.find { it.id == chunkEmbedding.documentId }

                RagSearchResult(
                    chunk = chunkEmbedding,
                    similarity = similarity,
                    documentTitle = document?.title ?: "Unknown"
                )
            }
                .filter { it.similarity >= config.minSimilarity }
                .sortedByDescending { it.similarity }
                .take(if (config.enableReranking) config.rerankTopN else config.topK)

            Napier.d("Stage 1: Found ${initialResults.size} candidates (minSimilarity: ${config.minSimilarity})")

            // Stage 2: Reranking (if enabled)
            val finalResults = if (config.enableReranking && initialResults.isNotEmpty()) {
                Napier.d("Stage 2: Reranking top ${initialResults.size} results")

                val rerankedResults = rerankResults(query, initialResults)
                    .filter { result ->
                        // Apply rerank score threshold
                        val score = result.rerankScore ?: 0.0
                        score >= config.minRerankScore
                    }
                    .let { filtered ->
                        if (config.useHybridScoring) {
                            // Hybrid scoring: combine similarity and rerank scores
                            filtered.sortedByDescending { result ->
                                val simScore = result.similarity * config.similarityWeight
                                val rerankScore = (result.rerankScore ?: 0.0) * config.rerankWeight
                                simScore + rerankScore
                            }
                        } else {
                            // Pure reranking: sort by rerank score only
                            filtered.sortedByDescending { it.rerankScore ?: 0.0 }
                        }
                    }
                    .take(config.topK)

                Napier.d("Stage 2 complete: ${rerankedResults.size} results after reranking and filtering")
                rerankedResults
            } else {
                initialResults.take(config.topK)
            }

            // Log final results
            finalResults.forEachIndexed { index, result ->
                val simScore = (result.similarity * 1000).toInt() / 1000.0
                val scoreInfo = result.rerankScore?.let { rerankScore ->
                    val rerank = (rerankScore * 1000).toInt() / 1000.0
                    "sim: $simScore, rerank: $rerank"
                } ?: "sim: $simScore"
                Napier.d("  ${index + 1}. ${result.documentTitle} - $scoreInfo")
            }

            Result.success(finalResults)

        } catch (e: Exception) {
            Napier.e("Error searching with config", e)
            Result.failure(e)
        }
    }

    /**
     * Search for relevant chunks based on a query (legacy method for backward compatibility)
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
     * Rerank search results using LLM-based scoring
     * This method uses Ollama to generate a relevance score for each result
     */
    private suspend fun rerankResults(
        query: String,
        results: List<RagSearchResult>
    ): List<RagSearchResult> {
        if (results.isEmpty()) {
            return results
        }

        return try {
            // For each result, ask the LLM to score its relevance to the query
            results.map { result ->
                val rerankScore = calculateRerankScore(query, result.chunk.content)
                result.copy(rerankScore = rerankScore)
            }
        } catch (e: Exception) {
            Napier.w("Reranking failed, returning original results: ${e.message}")
            // If reranking fails, return original results without rerank scores
            results
        }
    }

    /**
     * Calculate rerank score for a single chunk using LLM
     * Returns a score between 0.0 and 1.0 indicating relevance
     */
    private suspend fun calculateRerankScore(query: String, content: String): Double {
        try {
            // Trim content to reasonable length while preserving context
            val trimmedContent = if (content.length > 1500) {
                content.take(1500) + "..."
            } else {
                content
            }

            // Create a more structured prompt for the LLM to evaluate relevance
            val prompt = buildString {
                appendLine("You are a relevance scoring system. Your task is to evaluate how relevant a text passage is to answering a specific query.")
                appendLine()
                appendLine("Query: \"$query\"")
                appendLine()
                appendLine("Text passage:")
                appendLine(trimmedContent)
                appendLine()
                appendLine("Rate the relevance of this text to the query on a scale from 0.0 to 1.0:")
                appendLine("- 0.0 = Completely irrelevant, no connection to the query")
                appendLine("- 0.3 = Tangentially related, mentions similar topics but doesn't answer the query")
                appendLine("- 0.5 = Somewhat relevant, provides partial information")
                appendLine("- 0.7 = Relevant, provides useful information to answer the query")
                appendLine("- 1.0 = Highly relevant, directly and comprehensively answers the query")
                appendLine()
                appendLine("Respond with ONLY a single decimal number between 0.0 and 1.0, nothing else.")
                appendLine()
                append("Score:")
            }

            // Use a more capable model with temperature=0 for consistent results
            val response = ollamaClient.generateCompletion(
                prompt = prompt,
                model = "llama3.2:3b",
                options = OllamaOptions(
                    temperature = 0.0,
                    numPredict = 10  // We only need a short number response
                )
            )

            // Extract score from response
            val responseText = response.getOrNull()?.trim() ?: ""
            Napier.d("Rerank response for query '$query': '$responseText'")

            // Try to extract a number from the response
            val score = responseText
                .split(Regex("\\s+"))  // Split by whitespace
                .firstOrNull { it.toDoubleOrNull() != null }  // Find first valid number
                ?.toDoubleOrNull()
                ?.coerceIn(0.0, 1.0)
                ?: run {
                    Napier.w("Failed to parse rerank score from response: '$responseText', using 0.3")
                    0.3  // Lower default to not promote uncertain results
                }

            Napier.d("Calculated rerank score: $score")
            return score
        } catch (e: Exception) {
            Napier.w("Failed to calculate rerank score: ${e.message}")
            return 0.3 // Lower default score on error
        }
    }

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
     * Generate context from search results for RAG with detailed source citations and clickable links
     */
    fun generateContext(searchResults: List<RagSearchResult>): String {
        if (searchResults.isEmpty()) {
            return ""
        }

        return buildString {
            appendLine("Context from knowledge base:")
            appendLine()

            // Build a map of source URLs for easy reference
            val sourceLinks = mutableMapOf<Int, String>()

            searchResults.forEachIndexed { index, result ->
                val sourceNum = index + 1
                val relevanceScore = (result.similarity * 1000).toInt() / 1000.0

                // Get document metadata
                val document = currentIndex?.documents?.find { it.id == result.chunk.documentId }
                val url = document?.metadata?.get("url")
                val filePath = document?.metadata?.get("path") ?: document?.metadata?.get("file")

                // Store URL for later reference
                if (!url.isNullOrBlank()) {
                    sourceLinks[sourceNum] = url
                } else if (!filePath.isNullOrBlank()) {
                    sourceLinks[sourceNum] = "file://$filePath"
                }

                // Build source header with all available information
                val sourceHeader = buildString {
                    append("--- Source $sourceNum: ${result.documentTitle}")
                    append(" [Chunk #${result.chunk.chunkIndex + 1}]")

                    // Add relevance scores
                    append(" (similarity: $relevanceScore")
                    result.rerankScore?.let { rerank ->
                        val rerankScore = (rerank * 1000).toInt() / 1000.0
                        append(", rerank: $rerankScore")
                    }
                    append(")")

                    append(" ---")
                }

                appendLine(sourceHeader)

                // Add clickable link if URL is available
                if (!url.isNullOrBlank()) {
                    appendLine("🔗 Link: $url")
                } else if (!filePath.isNullOrBlank()) {
                    appendLine("📄 File: $filePath")
                }

                appendLine()
                appendLine(result.chunk.content)
                appendLine()

                // Add citation reference with link
                val citationRef = if (!url.isNullOrBlank()) {
                    "[Source $sourceNum]($url)"
                } else if (!filePath.isNullOrBlank()) {
                    "[Source $sourceNum](file://$filePath)"
                } else {
                    "Source $sourceNum"
                }
                appendLine("Cite as: $citationRef - ${result.documentTitle}, Chunk ${result.chunk.chunkIndex + 1}")
                appendLine()
            }

            // Add instructions for citation with clickable links
            appendLine("---")
            appendLine("IMPORTANT: When answering, you MUST include clickable markdown links to sources.")
            appendLine()
            appendLine("Citation format:")
            if (sourceLinks.isNotEmpty()) {
                sourceLinks.forEach { (num, url) ->
                    appendLine("- Source $num: [$num]($url)")
                }
                appendLine()
                appendLine("Examples of how to cite in your response:")
                appendLine("- According to [the documentation](${ sourceLinks[1] ?: "URL"}), the authentication system uses JWT tokens.")
                appendLine("- The configuration process is described in [Source 2](${ sourceLinks[2] ?: sourceLinks[1] ?: "URL"}).")
                appendLine("- Based on [this guide](${ sourceLinks[1] ?: "URL"}), the recommended approach is...")
            } else {
                appendLine("- Use the format: According to Source N, ...")
                appendLine("- Or reference by document name: According to ${searchResults.firstOrNull()?.documentTitle ?: "the documentation"}, ...")
            }
            appendLine()
            appendLine("ALWAYS include these clickable links when citing sources so users can navigate to the original documents.")
        }
    }
}