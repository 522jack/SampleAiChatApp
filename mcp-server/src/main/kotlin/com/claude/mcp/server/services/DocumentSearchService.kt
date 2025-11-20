package com.claude.mcp.server.services

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.toList

private val logger = KotlinLogging.logger {}

/**
 * Service for searching documents in project folders
 */
class DocumentSearchService(
    private val projectRoot: String = System.getProperty("user.dir")
) {
    @Serializable
    data class DocumentInfo(
        val path: String,
        val name: String,
        val extension: String,
        val size: Long,
        val lastModified: String
    )

    /**
     * Search for documents in the specified folder
     * @param folderPath Relative or absolute path to search in (default: project root)
     * @param pattern File name pattern to match (supports wildcards like *.txt, *.md)
     * @param recursive Whether to search recursively in subdirectories
     * @param maxDepth Maximum depth for recursive search (default: 5)
     * @return List of found documents
     */
    fun searchDocuments(
        folderPath: String = ".",
        pattern: String = "*",
        recursive: Boolean = true,
        maxDepth: Int = 5
    ): Result<List<DocumentInfo>> {
        return try {
            logger.info { "Searching documents in: $folderPath with pattern: $pattern (recursive: $recursive)" }
            logger.info { "Project root: $projectRoot" }

            val searchPath = resolveSearchPath(folderPath)
            logger.info { "Resolved search path: ${searchPath.toAbsolutePath()}" }

            if (!searchPath.toFile().exists()) {
                val errorMsg = "Path does not exist: ${searchPath.toAbsolutePath()}"
                logger.error { errorMsg }
                return Result.failure(Exception(errorMsg))
            }

            if (!searchPath.toFile().isDirectory) {
                val errorMsg = "Path is not a directory: ${searchPath.toAbsolutePath()}"
                logger.error { errorMsg }
                return Result.failure(Exception(errorMsg))
            }

            val documents = if (recursive) {
                findDocumentsRecursive(searchPath, pattern, maxDepth)
            } else {
                findDocumentsInDirectory(searchPath, pattern)
            }

            logger.info { "Found ${documents.size} documents" }
            Result.success(documents)
        } catch (e: Exception) {
            logger.error(e) { "Error searching documents" }
            Result.failure(e)
        }
    }

    /**
     * Get document content by path
     */
    fun getDocumentContent(documentPath: String): Result<String> {
        return try {
            val path = resolveSearchPath(documentPath)

            if (!path.toFile().exists()) {
                return Result.failure(Exception("Document does not exist: ${path.toAbsolutePath()}"))
            }

            if (!path.toFile().isFile) {
                return Result.failure(Exception("Path is not a file: ${path.toAbsolutePath()}"))
            }

            val content = path.toFile().readText()
            logger.info { "Read document: ${path.toAbsolutePath()} (${content.length} characters)" }
            Result.success(content)
        } catch (e: Exception) {
            logger.error(e) { "Error reading document: $documentPath" }
            Result.failure(e)
        }
    }

    private fun resolveSearchPath(pathString: String): Path {
        val path = Path.of(pathString)
        return if (path.isAbsolute) {
            path
        } else {
            Path.of(projectRoot).resolve(path).normalize()
        }
    }

    private fun findDocumentsRecursive(
        searchPath: Path,
        pattern: String,
        maxDepth: Int
    ): List<DocumentInfo> {
        val regex = convertPatternToRegex(pattern)

        return Files.walk(searchPath, maxDepth)
            .filter { it.isRegularFile() }
            .filter { matchesPattern(it.name, regex) }
            .map { createDocumentInfo(it) }
            .toList()
            .sortedByDescending { it.lastModified }
    }

    private fun findDocumentsInDirectory(
        searchPath: Path,
        pattern: String
    ): List<DocumentInfo> {
        val regex = convertPatternToRegex(pattern)

        return searchPath.toFile()
            .listFiles { file -> file.isFile && matchesPattern(file.name, regex) }
            ?.map { createDocumentInfo(it.toPath()) }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }

    private fun createDocumentInfo(path: Path): DocumentInfo {
        val file = path.toFile()
        return DocumentInfo(
            path = path.toAbsolutePath().toString(),
            name = path.name,
            extension = path.extension,
            size = file.length(),
            lastModified = java.time.Instant.ofEpochMilli(file.lastModified()).toString()
        )
    }

    private fun convertPatternToRegex(pattern: String): Regex {
        // Convert simple wildcard pattern to regex
        // Need to escape special regex characters first, then convert wildcards
        val regexPattern = pattern
            .replace("\\", "\\\\")  // Escape backslash first
            .replace(".", "\\.")    // Escape dot
            .replace("*", ".*")     // Convert * to .*
            .replace("?", ".")      // Convert ? to .

        logger.debug { "Pattern '$pattern' converted to regex: '^$regexPattern$'" }
        return Regex("^$regexPattern$", RegexOption.IGNORE_CASE)
    }

    private fun matchesPattern(fileName: String, pattern: Regex): Boolean {
        return pattern.matches(fileName)
    }

    fun formatSearchResults(documents: List<DocumentInfo>): String {
        return buildString {
            appendLine("📁 Document Search Results")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()

            if (documents.isEmpty()) {
                appendLine("No documents found.")
            } else {
                appendLine("Found ${documents.size} document(s):")
                appendLine()
                documents.forEachIndexed { index, doc ->
                    appendLine("${index + 1}. 📄 ${doc.name}")
                    appendLine("   Path: ${doc.path}")
                    appendLine("   Size: ${formatSize(doc.size)}")
                    appendLine("   Modified: ${doc.lastModified}")
                    appendLine()
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}