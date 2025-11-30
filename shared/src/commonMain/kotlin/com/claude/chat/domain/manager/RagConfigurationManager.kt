package com.claude.chat.domain.manager

import com.claude.chat.data.model.RagDocument
import com.claude.chat.data.remote.OllamaClient
import com.claude.chat.data.repository.ChatRepository
import io.github.aakira.napier.Napier

/**
 * Manager for RAG (Retrieval-Augmented Generation) configuration
 * Handles RAG mode settings, document indexing, and Ollama availability checks
 */
class RagConfigurationManager(
    private val repository: ChatRepository,
    private val ollamaClient: OllamaClient
) {
    companion object {
        private const val DEFAULT_EMBEDDING_MODEL = "nomic-embed-text"
        private const val OLLAMA_DEFAULT_URL = "localhost:11434"

        // Error messages
        private const val OLLAMA_NOT_AVAILABLE_ERROR =
            "Cannot connect to OLLAMA at $OLLAMA_DEFAULT_URL. Please:\n" +
            "1. Install OLLAMA from https://ollama.ai\n" +
            "2. Start OLLAMA service\n" +
            "3. Run: ollama pull $DEFAULT_EMBEDDING_MODEL"

        private const val FILE_TOO_LARGE_ERROR =
            "File too large. Maximum size is 50MB or 10M characters."

        private const val TOO_MANY_CHUNKS_ERROR =
            "Document is too complex. Try splitting it into smaller files."

        private const val OUT_OF_MEMORY_ERROR =
            "Not enough memory. Try a smaller file or increase heap size."
    }

    /**
     * Get RAG mode setting
     */
    suspend fun getRagMode(): Boolean {
        return repository.getRagMode()
    }

    /**
     * Toggle RAG mode
     */
    suspend fun toggleRagMode(enabled: Boolean): Result<Unit> {
        return try {
            repository.saveRagMode(enabled)
            Napier.d("RAG mode ${if (enabled) "enabled" else "disabled"}")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error toggling RAG mode", e)
            Result.failure(e)
        }
    }

    /**
     * Get RAG reranking setting
     */
    suspend fun getRagRerankingEnabled(): Boolean {
        return repository.getRagRerankingEnabled()
    }

    /**
     * Toggle RAG reranking
     */
    suspend fun toggleRagReranking(enabled: Boolean): Result<Unit> {
        return try {
            repository.saveRagRerankingEnabled(enabled)
            Napier.d("RAG reranking ${if (enabled) "enabled" else "disabled"}")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error toggling RAG reranking", e)
            Result.failure(e)
        }
    }

    /**
     * Load RAG index from storage
     */
    suspend fun loadRagIndex(): Result<Boolean> {
        val result = repository.loadRagIndex()
        if (result.isSuccess) {
            Napier.d("RAG index loaded successfully")
        } else {
            Napier.d("No RAG index found or failed to load")
        }
        return result
    }

    /**
     * Get all indexed documents
     */
    suspend fun getIndexedDocuments(): List<RagDocument> {
        return try {
            val documents = repository.getIndexedDocuments()
            Napier.d("Loaded ${documents.size} RAG documents")
            documents
        } catch (e: Exception) {
            Napier.e("Error loading RAG documents", e)
            emptyList()
        }
    }

    /**
     * Check if Ollama is available and ready to use
     */
    suspend fun checkOllamaAvailability(): Result<Boolean> {
        return try {
            Napier.d("Checking OLLAMA availability...")
            val isAvailable = ollamaClient.checkHealth()

            if (isAvailable) {
                Napier.d("OLLAMA is available")
                Result.success(true)
            } else {
                Napier.w("OLLAMA is not available")
                Result.failure(OllamaNotAvailableException(OLLAMA_NOT_AVAILABLE_ERROR))
            }
        } catch (e: Exception) {
            Napier.e("OLLAMA health check failed", e)
            Result.failure(OllamaNotAvailableException(OLLAMA_NOT_AVAILABLE_ERROR, e))
        }
    }

    /**
     * Index a document for RAG with validation and Ollama availability check
     */
    suspend fun indexDocument(title: String, content: String): Result<String> {
        // Validate input
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()

        if (trimmedTitle.isBlank() || trimmedContent.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Title and content are required")
            )
        }

        // Check Ollama availability
        val ollamaCheck = checkOllamaAvailability()
        if (ollamaCheck.isFailure) {
            return Result.failure(ollamaCheck.exceptionOrNull()!!)
        }

        // Perform indexing
        Napier.d("OLLAMA is available, proceeding with indexing...")
        val result = repository.indexDocument(trimmedTitle, trimmedContent)

        if (result.isSuccess) {
            Napier.d("Document indexed successfully: $trimmedTitle")
        } else {
            val exception = result.exceptionOrNull()
            Napier.e("Failed to index document: $trimmedTitle", exception)
        }

        return result
    }

    /**
     * Remove a document from RAG index
     */
    suspend fun removeDocument(documentId: String): Result<Boolean> {
        return try {
            val removed = repository.removeRagDocument(documentId)
            if (removed) {
                Napier.d("Document removed: $documentId")
                Result.success(true)
            } else {
                Napier.w("Failed to remove document: $documentId")
                Result.success(false)
            }
        } catch (e: Exception) {
            Napier.e("Error removing document", e)
            Result.failure(e)
        }
    }

    /**
     * Clear all RAG index data
     */
    suspend fun clearRagIndex(): Result<Unit> {
        return try {
            repository.clearRagIndex()
            Napier.d("RAG index cleared successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error clearing RAG index", e)
            Result.failure(e)
        }
    }

    /**
     * Parse error message and return user-friendly message
     */
    fun parseErrorMessage(exception: Throwable?): String {
        return when {
            exception is OllamaNotAvailableException -> exception.message ?: OLLAMA_NOT_AVAILABLE_ERROR
            exception?.message?.contains("too large", ignoreCase = true) == true -> FILE_TOO_LARGE_ERROR
            exception?.message?.contains("Too many chunks", ignoreCase = true) == true -> TOO_MANY_CHUNKS_ERROR
            exception?.message?.contains("OLLAMA", ignoreCase = true) == true -> OLLAMA_NOT_AVAILABLE_ERROR
            exception?.message?.contains("OutOfMemoryError", ignoreCase = true) == true -> OUT_OF_MEMORY_ERROR
            else -> "Failed to index document: ${exception?.message ?: "Unknown error"}"
        }
    }

    /**
     * Load all RAG configuration settings
     */
    suspend fun loadAllSettings(): RagSettings {
        return try {
            RagSettings(
                ragMode = getRagMode(),
                ragReranking = getRagRerankingEnabled(),
                documents = getIndexedDocuments()
            )
        } catch (e: Exception) {
            Napier.e("Error loading RAG settings", e)
            RagSettings()
        }
    }
}

/**
 * Data class to hold all RAG configuration settings
 */
data class RagSettings(
    val ragMode: Boolean = false,
    val ragReranking: Boolean = false,
    val documents: List<RagDocument> = emptyList()
)

/**
 * Custom exception for Ollama availability issues
 */
class OllamaNotAvailableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)