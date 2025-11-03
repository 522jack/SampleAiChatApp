package com.claude.chat.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.claude.chat.presentation.ui.App

/**
 * Main entry point for Desktop application
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Claude Chat",
        state = rememberWindowState(width = 1000.dp, height = 800.dp)
    ) {
        App()
    }
}
