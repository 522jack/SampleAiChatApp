package com.claude.chat.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.aakira.napier.Napier
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Helper to read text content from URI
 */
object FileReaderHelper {
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024L // 50MB limit

    fun readTextFromUri(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver

        // Check file size before reading
        val fileSize = getFileSize(context, uri)
        if (fileSize > MAX_FILE_SIZE) {
            Napier.e("File too large: $fileSize bytes (max: $MAX_FILE_SIZE)")
            throw IllegalStateException("File too large. Maximum size: ${MAX_FILE_SIZE / (1024 * 1024)}MB")
        }

        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

        return inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else {
                    -1L
                }
            } ?: -1L
        } catch (e: Exception) {
            Napier.w("Could not determine file size", e)
            -1L
        }
    }
}