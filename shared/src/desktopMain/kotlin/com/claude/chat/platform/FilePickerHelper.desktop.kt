package com.claude.chat.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.aakira.napier.Napier
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Desktop implementation of FilePickerHelper using AWT FileDialog
 */
actual class FilePickerHelper {
    companion object {
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB limit
    }

    actual fun pickTextFile(onResult: (String?) -> Unit) {
        try {
            // Create file dialog (works on Windows, macOS, Linux)
            val fileDialog = FileDialog(null as Frame?, "Select Text File", FileDialog.LOAD).apply {
                // Filter for text files
                file = "*.txt;*.md"
                isMultipleMode = false
            }

            // Show dialog (blocking)
            fileDialog.isVisible = true

            // Get selected file
            val selectedFile = fileDialog.file
            val selectedDir = fileDialog.directory

            if (selectedFile != null && selectedDir != null) {
                val file = File(selectedDir, selectedFile)
                Napier.d("Selected file: ${file.absolutePath}")

                // Check file size before reading
                val fileSize = file.length()
                if (fileSize > MAX_FILE_SIZE) {
                    Napier.e("File too large: $fileSize bytes (max: $MAX_FILE_SIZE)")
                    onResult(null)
                    return
                }

                // Read file content
                val content = file.readText(Charsets.UTF_8)
                Napier.d("File read successfully, content length: ${content.length}")
                onResult(content)
            } else {
                Napier.d("File picker cancelled")
                onResult(null)
            }
        } catch (e: Exception) {
            Napier.e("Error reading file", e)
            onResult(null)
        }
    }
}

/**
 * Composable helper for Desktop
 */
@Composable
actual fun rememberFilePickerHelper(): FilePickerHelper {
    return remember { FilePickerHelper() }
}