package com.claude.chat.domain.service

import com.claude.chat.data.model.TextChunk
import io.github.aakira.napier.Napier
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Service for splitting text into chunks for RAG processing
 */
@OptIn(ExperimentalUuidApi::class)
class TextChunker(
    private val chunkSize: Int = 500,
    private val chunkOverlap: Int = 50
) {
    /**
     * Split text into overlapping chunks
     */
    fun chunkText(
        text: String,
        documentId: String
    ): List<TextChunk> {
        if (text.isBlank()) {
            Napier.w("Attempted to chunk empty text")
            return emptyList()
        }

        // Safety check: limit maximum text size to prevent OOM
        val maxTextSize = 10_000_000 // 10MB of text
        if (text.length > maxTextSize) {
            Napier.e("Text too large: ${text.length} chars (max: $maxTextSize)")
            throw IllegalArgumentException("Text too large for chunking. Maximum size: $maxTextSize characters")
        }

        val chunks = mutableListOf<TextChunk>()
        var startPosition = 0
        var chunkIndex = 0
        val minChunkStep = maxOf(1, chunkSize - chunkOverlap) // Prevent infinite loop

        while (startPosition < text.length) {
            // Calculate end position
            val endPosition = minOf(startPosition + chunkSize, text.length)

            // Extract chunk content
            var actualEndPosition = endPosition
            var chunkContent = text.substring(startPosition, actualEndPosition)

            // Try to break at sentence boundary if not at the end
            if (endPosition < text.length && chunkContent.length >= chunkSize) {
                val lastPeriod = chunkContent.lastIndexOf('.')
                val lastNewline = chunkContent.lastIndexOf('\n')
                val breakPoint = maxOf(lastPeriod, lastNewline)

                if (breakPoint > chunkSize / 2) {
                    actualEndPosition = startPosition + breakPoint + 1
                    chunkContent = text.substring(startPosition, actualEndPosition)
                }
            }

            // Create chunk
            val chunk = TextChunk(
                id = Uuid.random().toString(),
                documentId = documentId,
                content = chunkContent.trim(),
                chunkIndex = chunkIndex,
                startPosition = startPosition,
                endPosition = actualEndPosition
            )

            chunks.add(chunk)

            // Move to next chunk with overlap (ensure we always move forward)
            val step = maxOf(minChunkStep, chunkContent.length - chunkOverlap)
            startPosition += step

            if (startPosition >= text.length) break

            chunkIndex++

            // Safety check: prevent too many chunks
            if (chunkIndex > 100_000) {
                Napier.e("Too many chunks created: $chunkIndex")
                throw IllegalStateException("Too many chunks. Please check chunk size configuration.")
            }
        }

        Napier.d("Created ${chunks.size} chunks from text of length ${text.length}")
        return chunks
    }

    /**
     * Split text by paragraphs and then chunk each paragraph
     */
    fun chunkByParagraphs(
        text: String,
        documentId: String
    ): List<TextChunk> {
        val paragraphs = text.split(Regex("\n\n+"))
        val allChunks = mutableListOf<TextChunk>()
        var globalChunkIndex = 0
        var globalPosition = 0

        paragraphs.forEach { paragraph ->
            if (paragraph.trim().isNotEmpty()) {
                if (paragraph.length <= chunkSize) {
                    // Small paragraph, keep as single chunk
                    val chunk = TextChunk(
                        id = Uuid.random().toString(),
                        documentId = documentId,
                        content = paragraph.trim(),
                        chunkIndex = globalChunkIndex,
                        startPosition = globalPosition,
                        endPosition = globalPosition + paragraph.length
                    )
                    allChunks.add(chunk)
                    globalChunkIndex++
                } else {
                    // Large paragraph, split into chunks
                    val paragraphChunks = chunkText(paragraph, documentId)
                    paragraphChunks.forEach { chunk ->
                        allChunks.add(
                            chunk.copy(
                                chunkIndex = globalChunkIndex,
                                startPosition = globalPosition + chunk.startPosition,
                                endPosition = globalPosition + chunk.endPosition
                            )
                        )
                        globalChunkIndex++
                    }
                }
            }
            globalPosition += paragraph.length + 2 // +2 for \n\n
        }

        Napier.d("Created ${allChunks.size} chunks from ${paragraphs.size} paragraphs")
        return allChunks
    }

    /**
     * Split text by sentences and group into chunks
     */
    fun chunkBySentences(
        text: String,
        documentId: String
    ): List<TextChunk> {
        // Simple sentence splitting (can be improved with better NLP)
        val sentences = text.split(Regex("[.!?]+\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val chunks = mutableListOf<TextChunk>()
        var currentChunk = StringBuilder()
        var chunkIndex = 0
        var startPosition = 0
        var currentPosition = 0

        sentences.forEach { sentence ->
            if (currentChunk.length + sentence.length > chunkSize && currentChunk.isNotEmpty()) {
                // Save current chunk
                val chunk = TextChunk(
                    id = Uuid.random().toString(),
                    documentId = documentId,
                    content = currentChunk.toString().trim(),
                    chunkIndex = chunkIndex,
                    startPosition = startPosition,
                    endPosition = currentPosition
                )
                chunks.add(chunk)

                // Start new chunk with overlap
                currentChunk = StringBuilder()
                if (chunkOverlap > 0 && chunks.isNotEmpty()) {
                    val overlapText = chunks.last().content.takeLast(chunkOverlap)
                    currentChunk.append(overlapText).append(" ")
                }

                chunkIndex++
                startPosition = currentPosition
            }

            currentChunk.append(sentence).append(". ")
            currentPosition += sentence.length + 2
        }

        // Add last chunk
        if (currentChunk.isNotEmpty()) {
            val chunk = TextChunk(
                id = Uuid.random().toString(),
                documentId = documentId,
                content = currentChunk.toString().trim(),
                chunkIndex = chunkIndex,
                startPosition = startPosition,
                endPosition = currentPosition
            )
            chunks.add(chunk)
        }

        Napier.d("Created ${chunks.size} chunks from ${sentences.size} sentences")
        return chunks
    }
}