package com.claude.chat.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun isSystemInDarkTheme(): Boolean {
    return remember {
        try {
            // Check for macOS dark mode
            val osName = System.getProperty("os.name").lowercase()
            if (osName.contains("mac")) {
                val process = Runtime.getRuntime().exec(arrayOf("defaults", "read", "-g", "AppleInterfaceStyle"))
                val result = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                result.equals("Dark", ignoreCase = true)
            } else {
                // For other desktop platforms, check system property
                // This works for some Linux desktop environments
                val theme = System.getProperty("gtk.theme")
                theme?.contains("dark", ignoreCase = true) == true
            }
        } catch (e: Exception) {
            // If detection fails, default to light theme
            false
        }
    }
}