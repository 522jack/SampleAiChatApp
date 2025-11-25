package com.claude.chat.platform

import android.content.Context
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of FileStorage
 * Requires context to be set via init()
 */
private class AndroidFileStorage(private val context: Context) : FileStorage {
    override suspend fun writeTextFile(fileName: String, content: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, fileName)
                file.writeText(content, Charsets.UTF_8)
                Napier.d("File saved: ${file.absolutePath}")
                true
            } catch (e: Exception) {
                Napier.e("Error writing file: $fileName", e)
                false
            }
        }
    }

    override suspend fun readTextFile(fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    val content = file.readText(Charsets.UTF_8)
                    Napier.d("File loaded: ${file.absolutePath}, size: ${content.length}")
                    content
                } else {
                    Napier.d("File not found: ${file.absolutePath}")
                    null
                }
            } catch (e: Exception) {
                Napier.e("Error reading file: $fileName", e)
                null
            }
        }
    }

    override suspend fun fileExists(fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, fileName)
                file.exists()
            } catch (e: Exception) {
                Napier.e("Error checking file existence: $fileName", e)
                false
            }
        }
    }

    override suspend fun deleteFile(fileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    file.delete()
                    Napier.d("File deleted: ${file.absolutePath}")
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

private var androidContext: Context? = null

/**
 * Initialize FileStorage with application context
 * Must be called before using FileStorage on Android
 */
fun initFileStorage(context: Context) {
    androidContext = context.applicationContext
}

/**
 * Create FileStorage instance for Android
 */
actual fun createFileStorage(): FileStorage {
    val context = androidContext ?: throw IllegalStateException(
        "FileStorage not initialized. Call initFileStorage(context) first."
    )
    return AndroidFileStorage(context)
}