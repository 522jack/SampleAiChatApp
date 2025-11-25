package com.claude.chat.platform

import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

/**
 * iOS implementation of FileStorage
 */
private class IosFileStorage : FileStorage {
    private val documentsDirectory: String
        get() {
            val paths = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            )
            return paths.first() as String
        }

    override suspend fun writeTextFile(fileName: String, content: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val filePath = "$documentsDirectory/$fileName"
                val nsString = content as NSString
                nsString.writeToFile(
                    filePath,
                    atomically = true,
                    encoding = NSUTF8StringEncoding,
                    error = null
                )
                Napier.d("File saved: $filePath")
                true
            } catch (e: Exception) {
                Napier.e("Error writing file: $fileName", e)
                false
            }
        }
    }

    override suspend fun readTextFile(fileName: String): String? {
        return withContext(Dispatchers.Default) {
            try {
                val filePath = "$documentsDirectory/$fileName"
                val fileManager = NSFileManager.defaultManager
                if (fileManager.fileExistsAtPath(filePath)) {
                    val content = NSString.stringWithContentsOfFile(
                        filePath,
                        encoding = NSUTF8StringEncoding,
                        error = null
                    )
                    Napier.d("File loaded: $filePath, size: ${content?.length ?: 0}")
                    content as? String
                } else {
                    Napier.d("File not found: $filePath")
                    null
                }
            } catch (e: Exception) {
                Napier.e("Error reading file: $fileName", e)
                null
            }
        }
    }

    override suspend fun fileExists(fileName: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val filePath = "$documentsDirectory/$fileName"
                val fileManager = NSFileManager.defaultManager
                fileManager.fileExistsAtPath(filePath)
            } catch (e: Exception) {
                Napier.e("Error checking file existence: $fileName", e)
                false
            }
        }
    }

    override suspend fun deleteFile(fileName: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                val filePath = "$documentsDirectory/$fileName"
                val fileManager = NSFileManager.defaultManager
                if (fileManager.fileExistsAtPath(filePath)) {
                    fileManager.removeItemAtPath(filePath, error = null)
                    Napier.d("File deleted: $filePath")
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Napier.e("Error deleting file: $fileName", e)
                false
            }
        }
    }
}

/**
 * Create FileStorage instance for iOS
 */
actual fun createFileStorage(): FileStorage = IosFileStorage()