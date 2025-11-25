package com.claude.chat.platform

import androidx.compose.runtime.Composable

/**
 * Platform-specific file picker helper
 */
expect class FilePickerHelper {
    /**
     * Pick a text file and return its content
     * @param onResult callback with file content or null if cancelled
     */
    fun pickTextFile(onResult: (String?) -> Unit)
}

/**
 * Composable function to create FilePickerHelper
 * Platform-specific implementation
 */
@Composable
expect fun rememberFilePickerHelper(): FilePickerHelper