package com.claude.chat.presentation.ui

import androidx.compose.runtime.Composable
import com.claude.chat.presentation.chat.mvi.ChatIntent

/**
 * Platform-specific voice input handler.
 * On Android: handles speech recognition with permission management.
 * On other platforms: no-op.
 */
@Composable
expect fun VoiceInputHandler(
    isRecording: Boolean,
    onIntent: (ChatIntent) -> Unit
)