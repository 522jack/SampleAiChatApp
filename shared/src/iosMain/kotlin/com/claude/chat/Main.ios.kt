package com.claude.chat

import androidx.compose.ui.window.ComposeUIViewController
import com.claude.chat.presentation.ui.App
import platform.UIKit.UIViewController

/**
 * Main entry point for iOS app
 */
fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        App()
    }
}
