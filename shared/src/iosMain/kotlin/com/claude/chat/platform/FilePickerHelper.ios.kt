package com.claude.chat.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS implementation of FilePickerHelper (placeholder)
 */
actual class FilePickerHelper {
    actual fun pickTextFile(onResult: (String?) -> Unit) {
        // TODO: Implement iOS file picker using UIDocumentPickerViewController
        onResult(null)
    }
}

/**
 * Composable helper for iOS (placeholder)
 */
@Composable
actual fun rememberFilePickerHelper(): FilePickerHelper {
    return remember { FilePickerHelper() }
}