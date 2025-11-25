package com.claude.chat.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.aakira.napier.Napier

/**
 * Android implementation of FilePickerHelper
 */
actual class FilePickerHelper {
    var launchPicker: ((String?) -> Unit) -> Unit = {}

    actual fun pickTextFile(onResult: (String?) -> Unit) {
        launchPicker(onResult)
    }
}

/**
 * Composable helper to create FilePickerHelper with activity context
 */
@Composable
actual fun rememberFilePickerHelper(): FilePickerHelper {
    val context = LocalContext.current
    var onResultCallback by remember { mutableStateOf<((String?) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            Napier.d("File picker cancelled")
            onResultCallback?.invoke(null)
        } else {
            try {
                Napier.d("Reading file from URI: $uri")
                val content = FileReaderHelper.readTextFromUri(context, uri)
                Napier.d("File read successfully, content length: ${content.length}")
                onResultCallback?.invoke(content)
            } catch (e: Exception) {
                Napier.e("Error reading file", e)
                onResultCallback?.invoke(null)
            }
        }
        onResultCallback = null
    }

    return remember {
        FilePickerHelper().apply {
            launchPicker = { callback ->
                onResultCallback = callback
                launcher.launch(arrayOf("text/plain", "text/markdown", "text/*"))
            }
        }
    }.also { helper ->
        // Update callback when rememberFilePickerHelper is recomposed
        helper.launchPicker = { callback ->
            onResultCallback = callback
            launcher.launch(arrayOf("text/plain", "text/markdown", "text/*"))
        }
    }
}