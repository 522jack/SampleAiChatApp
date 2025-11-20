package com.claude.mcp.server.services

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Service for storing document summaries
 */
class DocumentStorageService(
    private val projectRoot: String = System.getProperty("user.dir")
) {
    companion object {
        private const val DEFAULT_SUMMARY_FOLDER = "summaries"
    }

    /**
     * Save summary to a file
     * @param content Summary content to save
     * @param originalDocumentPath Path to the original document
     * @param outputFolder Folder where to save the summary (relative to project root)
     * @param filename Custom filename (optional, will be auto-generated if not provided)
     * @return Path to the saved summary file
     */
    fun saveSummary(
        content: String,
        originalDocumentPath: String,
        outputFolder: String = DEFAULT_SUMMARY_FOLDER,
        filename: String? = null
    ): Result<String> {
        return try {
            logger.info { "Saving summary for document: $originalDocumentPath" }

            // Resolve output folder path
            val outputPath = resolvePath(outputFolder)
            val outputDir = outputPath.toFile()

            // Create directory if it doesn't exist
            if (!outputDir.exists()) {
                outputDir.mkdirs()
                logger.info { "Created summary directory: ${outputDir.absolutePath}" }
            }

            // Generate filename if not provided
            val summaryFilename = filename ?: generateSummaryFilename(originalDocumentPath)

            // Create full path
            val summaryFile = File(outputDir, summaryFilename)

            // Write content to file
            summaryFile.writeText(content)

            val savedPath = summaryFile.absolutePath
            logger.info { "Summary saved to: $savedPath" }

            Result.success(savedPath)
        } catch (e: Exception) {
            logger.error(e) { "Error saving summary" }
            Result.failure(e)
        }
    }

    /**
     * List all saved summaries
     * @param folder Folder to search in (default: summaries folder)
     * @return List of summary file paths
     */
    fun listSummaries(folder: String = DEFAULT_SUMMARY_FOLDER): Result<List<String>> {
        return try {
            val outputPath = resolvePath(folder)
            val outputDir = outputPath.toFile()

            if (!outputDir.exists() || !outputDir.isDirectory) {
                return Result.success(emptyList())
            }

            val summaries = outputDir.listFiles { file ->
                file.isFile && (file.extension == "md" || file.extension == "txt")
            }?.map { it.absolutePath } ?: emptyList()

            logger.info { "Found ${summaries.size} summaries in $folder" }
            Result.success(summaries)
        } catch (e: Exception) {
            logger.error(e) { "Error listing summaries" }
            Result.failure(e)
        }
    }

    /**
     * Read a saved summary
     */
    fun readSummary(summaryPath: String): Result<String> {
        return try {
            val path = resolvePath(summaryPath)
            val file = path.toFile()

            if (!file.exists()) {
                return Result.failure(Exception("Summary file does not exist: ${file.absolutePath}"))
            }

            if (!file.isFile) {
                return Result.failure(Exception("Path is not a file: ${file.absolutePath}"))
            }

            val content = file.readText()
            logger.info { "Read summary from: ${file.absolutePath}" }
            Result.success(content)
        } catch (e: Exception) {
            logger.error(e) { "Error reading summary: $summaryPath" }
            Result.failure(e)
        }
    }

    /**
     * Delete a saved summary
     */
    fun deleteSummary(summaryPath: String): Result<Boolean> {
        return try {
            val path = resolvePath(summaryPath)
            val file = path.toFile()

            if (!file.exists()) {
                return Result.failure(Exception("Summary file does not exist: ${file.absolutePath}"))
            }

            val deleted = file.delete()
            if (deleted) {
                logger.info { "Deleted summary: ${file.absolutePath}" }
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete summary: ${file.absolutePath}"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting summary: $summaryPath" }
            Result.failure(e)
        }
    }

    private fun resolvePath(pathString: String): Path {
        val path = Path.of(pathString)
        return if (path.isAbsolute) {
            path
        } else {
            Path.of(projectRoot).resolve(path).normalize()
        }
    }

    private fun generateSummaryFilename(originalDocumentPath: String): String {
        // Extract original filename without extension
        val originalName = originalDocumentPath
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .substringBeforeLast(".")

        // Add timestamp
        val timestamp = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")
            .format(Instant.now().atZone(java.time.ZoneId.systemDefault()))

        return "${originalName}_summary_$timestamp.md"
    }

    fun formatSaveResult(savedPath: String, originalPath: String): String {
        val filename = savedPath.substringAfterLast("/").substringAfterLast("\\")
        val folder = savedPath.substringBeforeLast("/").substringBeforeLast("\\")

        return buildString {
            appendLine()
            appendLine("╔════════════════════════════════════════════════╗")
            appendLine("║          💾 SUMMARY SAVED SUCCESSFULLY         ║")
            appendLine("╚════════════════════════════════════════════════╝")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📄 ORIGINAL DOCUMENT")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("   $originalPath")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("📁 SUMMARY FILE LOCATION")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("   Folder:   $folder")
            appendLine("   Filename: $filename")
            appendLine()
            appendLine("   Full path: $savedPath")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("✅ SUCCESS!")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📌 The summary has been saved and is ready to use.")
            appendLine("📌 You can access this file at any time.")
            appendLine()
        }
    }

    fun formatListResult(summaries: List<String>): String {
        return buildString {
            appendLine("📚 Saved Summaries")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()

            if (summaries.isEmpty()) {
                appendLine("No summaries found.")
            } else {
                appendLine("Found ${summaries.size} summary file(s):")
                appendLine()
                summaries.forEachIndexed { index, path ->
                    val filename = path.substringAfterLast("/").substringAfterLast("\\")
                    appendLine("${index + 1}. 📄 $filename")
                    appendLine("   Path: $path")
                    appendLine()
                }
            }
        }
    }
}