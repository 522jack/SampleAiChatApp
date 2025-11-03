package com.claude.chat.platform

import androidx.compose.runtime.Composable

@Composable
actual fun isSystemInDarkTheme(): Boolean {
    // For desktop, we could check system properties, but for simplicity
    // we'll return false by default. This can be enhanced later.
    return false
}