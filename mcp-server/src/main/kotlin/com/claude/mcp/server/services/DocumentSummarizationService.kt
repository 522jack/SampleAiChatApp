package com.claude.mcp.server.services

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for summarizing documents
 */
class DocumentSummarizationService {
    @Serializable
    data class DocumentSummary(
        val documentPath: String,
        val title: String,
        val wordCount: Int,
        val characterCount: Int,
        val lineCount: Int,
        val preview: String,
        val keyPoints: List<String>,
        val createdAt: String
    )

    /**
     * Summarize a document
     * @param documentPath Path to the document
     * @param content Document content
     * @param maxPreviewLength Maximum length of preview text (default: 500)
     * @return Document summary
     */
    fun summarizeDocument(
        documentPath: String,
        content: String,
        maxPreviewLength: Int = 500
    ): Result<DocumentSummary> {
        return try {
            logger.info { "Summarizing document: $documentPath" }

            val lines = content.lines()
            val words = content.split(Regex("\\s+")).filter { it.isNotBlank() }

            val title = extractTitle(documentPath, lines)
            val preview = createPreview(content, maxPreviewLength)
            val keyPoints = extractKeyPoints(lines, words)

            val summary = DocumentSummary(
                documentPath = documentPath,
                title = title,
                wordCount = words.size,
                characterCount = content.length,
                lineCount = lines.size,
                preview = preview,
                keyPoints = keyPoints,
                createdAt = Instant.now().toString()
            )

            logger.info { "Summary created: ${summary.wordCount} words, ${summary.lineCount} lines" }
            Result.success(summary)
        } catch (e: Exception) {
            logger.error(e) { "Error summarizing document: $documentPath" }
            Result.failure(e)
        }
    }

    /**
     * Format summary for display
     */
    fun formatSummary(summary: DocumentSummary): String {
        return buildString {
            appendLine()
            appendLine("╔════════════════════════════════════════════════╗")
            appendLine("║        📊 DOCUMENT SUMMARY GENERATED           ║")
            appendLine("╚════════════════════════════════════════════════╝")
            appendLine()
            appendLine("📄 Title: ${summary.title}")
            appendLine("📁 Path: ${summary.documentPath}")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📈 STATISTICS")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("   • Words: ${summary.wordCount}")
            appendLine("   • Characters: ${summary.characterCount}")
            appendLine("   • Lines: ${summary.lineCount}")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📝 PREVIEW")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine(summary.preview)
            appendLine()

            if (summary.keyPoints.isNotEmpty()) {
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("🔑 KEY POINTS")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                summary.keyPoints.forEach { point ->
                    appendLine("   • $point")
                }
                appendLine()
            }

            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("🕐 Created: ${summary.createdAt}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("💡 Next step: Use 'save_summary' tool to save this summary to a file")
            appendLine()
        }
    }

    private fun extractTitle(documentPath: String, lines: List<String>): String {
        // Try to find title from first non-empty line
        val firstLine = lines.firstOrNull { it.isNotBlank() }?.trim() ?: ""

        // Remove markdown heading markers
        val cleanedTitle = firstLine.removePrefix("#").trim()

        // If title is too long or empty, use filename
        return if (cleanedTitle.isNotEmpty() && cleanedTitle.length <= 100) {
            cleanedTitle
        } else {
            documentPath.substringAfterLast("/").substringBeforeLast(".")
        }
    }

    private fun createPreview(content: String, maxLength: Int): String {
        val preview = content.take(maxLength).trim()
        return if (content.length > maxLength) {
            "$preview..."
        } else {
            preview
        }
    }

    private fun extractKeyPoints(lines: List<String>, words: List<String>): List<String> {
        val keyPoints = mutableListOf<String>()

        // Extract markdown headers (lines starting with #)
        val headers = lines
            .filter { it.trim().startsWith("#") }
            .map { it.trim().removePrefix("#").trim() }
            .take(5)
        keyPoints.addAll(headers)

        // Extract bullet points (lines starting with -, *, or •)
        val bullets = lines
            .filter { line ->
                val trimmed = line.trim()
                trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("•")
            }
            .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
            .take(5)
        keyPoints.addAll(bullets)

        // If no key points found, try to extract first few meaningful sentences
        if (keyPoints.isEmpty()) {
            val sentences = lines
                .filter { it.isNotBlank() && it.length > 20 }
                .take(3)
                .map {
                    val cleaned = it.trim()
                    if (cleaned.length > 100) "${cleaned.take(100)}..." else cleaned
                }
            keyPoints.addAll(sentences)
        }

        // Extract some word frequency statistics as key points
        if (words.size > 50) {
            val wordFrequency = words
                .filter { it.length > 4 } // Filter out short words
                .groupingBy { it.lowercase() }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { "Frequently mentioned: '${it.key}' (${it.value} times)" }

            if (wordFrequency.isNotEmpty() && keyPoints.size < 3) {
                keyPoints.add("Most common terms:")
                keyPoints.addAll(wordFrequency)
            }
        }

        return keyPoints.distinct().take(10)
    }

    /**
     * Create a simple summary string for storage
     */
    fun createSimpleSummary(summary: DocumentSummary): String {
        return buildString {
            appendLine("# Summary of ${summary.title}")
            appendLine()
            appendLine("Document: ${summary.documentPath}")
            appendLine("Generated: ${summary.createdAt}")
            appendLine()
            appendLine("## Statistics")
            appendLine("- Words: ${summary.wordCount}")
            appendLine("- Characters: ${summary.characterCount}")
            appendLine("- Lines: ${summary.lineCount}")
            appendLine()
            appendLine("## Preview")
            appendLine(summary.preview)
            appendLine()

            if (summary.keyPoints.isNotEmpty()) {
                appendLine("## Key Points")
                summary.keyPoints.forEach { point ->
                    appendLine("- $point")
                }
            }
        }
    }
}