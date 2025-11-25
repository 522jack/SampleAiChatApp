package com.claude.chat.platform

/**
 * Platform-specific file storage interface
 */
interface FileStorage {
    /**
     * Write text content to file
     * @param fileName name of the file
     * @param content text content to write
     * @return true if successful
     */
    suspend fun writeTextFile(fileName: String, content: String): Boolean

    /**
     * Read text content from file
     * @param fileName name of the file
     * @return file content or null if file doesn't exist
     */
    suspend fun readTextFile(fileName: String): String?

    /**
     * Check if file exists
     * @param fileName name of the file
     * @return true if file exists
     */
    suspend fun fileExists(fileName: String): Boolean

    /**
     * Delete file
     * @param fileName name of the file
     * @return true if successful
     */
    suspend fun deleteFile(fileName: String): Boolean
}

/**
 * Create platform-specific FileStorage instance
 */
expect fun createFileStorage(): FileStorage